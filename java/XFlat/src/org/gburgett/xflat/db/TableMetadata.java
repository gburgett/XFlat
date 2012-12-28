/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.WeakHashMap;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.XflatException;
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
public class TableMetadata {
    String name;

    EngineBase engine;

    IdGenerator idGenerator;

    Map<TableBase, Class<?>> tables = new WeakHashMap<>();

    Database db;
    
    TableConfig config;

    public TableMetadata(String name, Database db){
        this.name = name;
        this.db = db;
    }

    public synchronized <T> Table<T> getTable(Class<T> clazz){
        if(clazz == null){
            throw new IllegalArgumentException("clazz cannot be null");
        }

        if(engine.getState() == EngineState.SpinningDown || 
                engine.getState() == EngineState.SpunDown)
        {
            throw new UnsupportedOperationException("Cannot get table for spun-down engine");
        }

        if(engine.getState() == EngineState.Uninitialized){
            //spin it up
            this.spinUp();
        }

        for(Map.Entry<TableBase, Class<?>> entry : tables.entrySet()){
            if(entry.getValue().equals(clazz)){
                return (Table<T>)entry.getKey();
            }
        }

        TableBase table = makeTableForClass(clazz);
        table.setIdGenerator(idGenerator);
        table.setEngine(engine);
        tables.put(table, clazz);

        return (Table<T>)table;
    }

    private <T> TableBase makeTableForClass(Class<T> clazz){
        if(Element.class.equals(clazz)){
            return new ElementTable(this.db, this.name);
        }

        ConvertingTable<T> ret = new ConvertingTable<>(this.db, clazz, this.name);
        ret.setConversionService(this.db.getConversionService());
        return ret;
    }

    private void spinUp(){
        this.engine.spinUp();
        this.engine.beginOperations();
    }
    
    public void spinDown(){
        this.engine.spinDown(new SpinDownEventHandler(){
            @Override
            public void spinDownComplete(SpinDownEvent event) {
            }
        });
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

    public static TableMetadata makeNewTableMetadata(String name, Database db, DatabaseConfig dbConfig, TableConfig config, Class<?> idType){

        config = config == null ? TableConfig.defaultConfig : config;
        TableMetadata ret = new TableMetadata(name, db);

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
        //TODO: engines will in the future be configurable & based on a strategy
        ret.engine = new CachedDocumentEngine(new File(db.getDirectory(), name + ".xml"), name);

        return ret;
    }

    public static TableMetadata makeTableMetadataFromDocument(String name, Database db, Document metadata, TableConfig config, Class<?> idType){
        TableMetadata ret = new TableMetadata(name, db);
        if(config == null){
            Element c = metadata.getRootElement().getChild("config", Database.xFlatNs);
            config = TableConfig.FromElementConverter.convert(c);
        }
        //else we already verified that config was equal to that stored in metadata

        //load ID generator
        Class<? extends IdGenerator> generatorClass = null;
        Element g = metadata.getRootElement().getChild("generator", Database.xFlatNs);
        if(g != null){
            String gClassStr = g.getAttributeValue("class", Database.xFlatNs);
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
        Class<? extends EngineBase> engineClass = null;
        Element engineEl = metadata.getRootElement().getChild("engine", Database.xFlatNs);
        if(engineEl != null){
            String engineClassAttr = engineEl.getAttributeValue("class", Database.xFlatNs);
            if(engineClassAttr != null){
                try {
                    engineClass = (Class<? extends EngineBase>) TableMetadata.class.getClassLoader().loadClass(engineClassAttr);
                } catch (ClassNotFoundException ex) {
                    throw new XflatException("Cannot load metadata: Cannot load engine class", ex);
                }
            }
        }
        if(engineClass == null){
            throw new XflatException("Cannot load metadata: Cannot load engine class");
        }
        try {
            Constructor<? extends EngineBase> constructor = 
                    engineClass.getConstructor(File.class, String.class);

            ret.engine = constructor.newInstance(new File(db.getDirectory(), name + ".xml"), name);
        } catch (Exception ex) {
            throw new XflatException("Cannot load metadata: cannot instantiate new engine", ex);
        }
        ret.engine.loadMetadata(engineEl);

        return ret;
    }

    public Document saveTableMetadata(){
        Document doc = new Document();
        doc.setRootElement(new Element("metadata", Database.xFlatNs));
        
        //save config
        Element cfg = TableConfig.ToElementConverter.convert(this.config);
        doc.getRootElement().addContent(cfg);
        
        //save generator
        Element g= new Element("generator", Database.xFlatNs);
        g.setAttribute("class", this.idGenerator.getClass().getName(), Database.xFlatNs);
        this.idGenerator.saveState(g);
        
        doc.getRootElement().addContent(g);
        
        //save engine
        Element e = new Element("engine", Database.xFlatNs);
        e.setAttribute("class", this.engine.getClass().getName(), Database.xFlatNs);
        this.engine.saveMetadata(e);
        doc.getRootElement().addContent(e);
        
        return doc;
    }

}
