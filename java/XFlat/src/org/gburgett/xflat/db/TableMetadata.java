/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.db.EngineBase.EngineState;
import org.gburgett.xflat.db.EngineBase.SpinDownEvent;
import org.gburgett.xflat.db.EngineBase.SpinDownEventHandler;
import org.gburgett.xflat.engine.CachedDocumentEngine;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class TableMetadata implements EngineProvider {
    String name;

    AtomicReference<EngineBase> engine = new AtomicReference<>();
    
    Element engineMetadata;

    IdGenerator idGenerator;

    XFlatDatabase db;
    
    TableConfig config;
    
    long lastActivity = System.currentTimeMillis();
    
    EngineState getEngineState(){
        EngineBase engine = this.engine.get();
        if(engine == null)
            return EngineState.Uninitialized;
        
        return engine.getState();
    }

    public TableMetadata(String name, XFlatDatabase db){
        this.name = name;
        this.db = db;
    }

    public TableBase getTable(Class<?> clazz){
        if(clazz == null){
            throw new IllegalArgumentException("clazz cannot be null");
        }
        
        this.ensureSpinUp();

        TableBase table = makeTableForClass(clazz);
        table.setIdGenerator(idGenerator);
        table.setEngineProvider(this);

        return table;
    }

    @Override
    public Engine provideEngine(){
        this.lastActivity = System.currentTimeMillis();
        return this.ensureSpinUp();
    }
    
    private <T> TableBase makeTableForClass(Class<T> clazz){
        if(Element.class.equals(clazz)){
            return new ElementTable(this.db, this.name);
        }

        ConvertingTable<T> ret = new ConvertingTable<>(this.db, clazz, this.name);
        ret.setConversionService(this.db.getConversionService());
        return ret;
    }
    
    private EngineBase makeNewEngine(){
        synchronized(this){
            //TODO: engines will in the future be configurable & based on a strategy
            EngineBase ret = new CachedDocumentEngine(new File(db.getDirectory(), name + ".xml"), name);

            ret.setConversionService(db.getConversionService());
            ret.setExecutorService(db.getExecutorService());
            ret.loadMetadata(engineMetadata);
            
            return ret;
        }
    }

    private EngineBase ensureSpinUp(){
        EngineBase engine = this.engine.get();
        
        EngineState state;
        
        if(engine == null ||
                (state = engine.getState()) == EngineState.SpinningDown ||
                state == EngineState.SpunDown){
            EngineBase newEngine = makeNewEngine();
            if(!this.engine.compareAndSet(newEngine, engine)){
                //another thread has changed the engine - spinwait and retry if necessary
                long waitUntil = System.currentTimeMillis() + 1;
                while(System.currentTimeMillis() < waitUntil){
                    engine = this.engine.get();
                    if(engine.getState() == EngineState.SpinningUp ||
                            engine.getState() == EngineState.SpunUp ||
                            engine.getState() == EngineState.Running){
                        return engine;
                    }
                    //still in the wrong state, retry recursive
                    return this.ensureSpinUp();
                }
            }
        }
        else if(state == EngineState.SpinningUp ||
                            state == EngineState.SpunUp ||
                            state == EngineState.Running){
            //good to go
            return engine;
        }
        
        //spinUp returns true if this thread successfully spun it up
        if(engine.spinUp())
            engine.beginOperations();
        
        return engine;
    }
    
    public void spinDown(){
        synchronized(this){
            final EngineBase engine = this.engine.getAndSet(null);
            EngineState state;
            if(engine == null ||
                    (state = engine.getState()) == EngineState.SpinningDown ||
                    state == EngineState.SpunDown)
                //another thread already spinning it down
                return;

            if(engine.spinDown(new SpinDownEventHandler(){
                    @Override
                    public void spinDownComplete(SpinDownEvent event) {
                        engine.forceSpinDown();
                    }
                }))
            {
                //save metadata for the next engine
                engine.saveMetadata(engineMetadata);
            }
            else{
                engine.forceSpinDown();
            }
        }
    }

    private static IdGenerator makeIdGenerator(Class<? extends IdGenerator> generatorClass){
        if(generatorClass == null){
            throw new XflatException("generator class could not be loaded");
        }

        try {
            return generatorClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new XflatException("Cannot load metadata: generator class could not be instantiated", ex);
        }
    }

    public static TableMetadata makeNewTableMetadata(String name, XFlatDatabase db, DatabaseConfig dbConfig, TableConfig config, Class<?> idType){

        TableMetadata ret = new TableMetadata(name, db);
        
        config = config == null ? TableConfig.defaultConfig : config;
        ret.config = config;

        //make ID Generator
        Class<? extends IdGenerator> generatorClass = config.getIdGenerator();
        if(generatorClass != null){
            ret.idGenerator = makeIdGenerator(generatorClass);
            if(!ret.idGenerator.supports(idType)){
                throw new XflatException("Id Generator " + generatorClass.getName() +
                        " does not support type " + idType);
            }
        }
        else {
            //pick using our strategy
            for(Class<? extends IdGenerator> g : dbConfig.getIdGeneratorStrategy()){
                IdGenerator gen = makeIdGenerator(g);
                if(gen.supports(idType)){
                    ret.idGenerator = gen;
                    break;
                }
            }
            if(ret.idGenerator == null){
                throw new XflatException("Could not pick id generator for type " + idType);
            }
        }

        //make engine
        
        
        return ret;
    }

    public static TableMetadata makeTableMetadataFromDocument(String name, XFlatDatabase db, Document metadata, TableConfig config, Class<?> idType){
        TableMetadata ret = new TableMetadata(name, db);
        if(config == null){
            Element c = metadata.getRootElement().getChild("config", XFlatDatabase.xFlatNs);
            try {
                config = TableConfig.FromElementConverter.convert(c);
            } catch (ConversionException ex) {
                throw new XflatException("Cannot deserialize metadata for table " + name, ex);
            }
        }
        //else we already verified that config was equal to that stored in metadata

        ret.config = config;
        
        //load ID generator
        Class<? extends IdGenerator> generatorClass = null;
        Element g = metadata.getRootElement().getChild("generator", XFlatDatabase.xFlatNs);
        if(g != null){
            String gClassStr = g.getAttributeValue("class", XFlatDatabase.xFlatNs);
            if(gClassStr != null){
                try {
                    generatorClass = (Class<? extends IdGenerator>) TableMetadata.class.getClassLoader().loadClass(gClassStr);
                } catch (ClassNotFoundException ex) {
                    throw new XflatException("Cannot load metadata: generator class could not be loaded", ex);
                }
            }
        }
        ret.idGenerator = makeIdGenerator(generatorClass);
        if(!ret.idGenerator.supports(idType)){
            throw new XflatException("Id Generator " + generatorClass + " does not support " + 
                    " ID type " + idType);
        }
        ret.idGenerator.loadState(g);

        //load engine
        ret.engineMetadata = metadata.getRootElement().getChild("engine", XFlatDatabase.xFlatNs);
        
        return ret;
    }

    public Document saveTableMetadata(){
        Document doc = new Document();
        doc.setRootElement(new Element("metadata", XFlatDatabase.xFlatNs));
        
        //save config
        Element cfg;
        try {
            cfg = TableConfig.ToElementConverter.convert(this.config);
        } catch (ConversionException ex) {
            throw new XflatException("Cannot serialize table metadata", ex);
        }
        doc.getRootElement().addContent(cfg);
        
        //save generator
        Element g= new Element("generator", XFlatDatabase.xFlatNs);
        g.setAttribute("class", this.idGenerator.getClass().getName(), XFlatDatabase.xFlatNs);
        this.idGenerator.saveState(g);
        
        doc.getRootElement().addContent(g);
        
        //save engine
        Element e = new Element("engine", XFlatDatabase.xFlatNs);
        e.setAttribute("class", this.engine.getClass().getName(), XFlatDatabase.xFlatNs);
        this.engine.get().saveMetadata(e);
        doc.getRootElement().addContent(e);
        
        return doc;
    }

}
