/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import org.gburgett.xflat.TableConfig;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.PojoConverter;
import org.gburgett.xflat.db.EngineBase.SpinDownEvent;
import org.gburgett.xflat.db.EngineBase.SpinDownEventHandler;
import org.jdom2.Element;

/**
 * A class containing metadata about a Table, and providing the ability to spin up
 * engines for that table.
 * 
 * TODO: this class really performs two responsibilities, one is managing the spin-up
 * and spin-down of the engine for the table, the other is creating {@link TableBase} instances.
 * The two responsibilities should be separated.  You can see where the line is inside the
 * {@link TableMetadataFactory} class.
 * @author gordon
 */
public class TableMetadata implements EngineProvider {

    //<editor-fold desc="EngineProvider dependencies">
    String name;
    public String getName(){
        return name;
    }
    
    File engineFile;

    AtomicReference<EngineBase> engine = new AtomicReference<>();
    
    Element engineMetadata;

    XFlatDatabase db;
    //</editor-fold>
    
    IdGenerator idGenerator;
    
    TableConfig config;
    
    long lastActivity = System.currentTimeMillis();
    public long getLastActivity(){
        return lastActivity;
    }
    
    EngineState getEngineState(){
        EngineBase engine = this.engine.get();
        if(engine == null)
            return EngineState.Uninitialized;
        
        return engine.getState();
    }

    public TableMetadata(String name, XFlatDatabase db, File engineFile){
        this.name = name;
        this.db = db;
        this.engineFile = engineFile;
    }

    //<editor-fold desc="table creation">
    
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
    
    private <T> TableBase makeTableForClass(Class<T> clazz){
        if(Element.class.equals(clazz)){
            return new ElementTable(this.name);
        }
        
        IdAccessor accessor = IdAccessor.forClass(clazz);
        if(accessor.hasId()){
            if(!this.idGenerator.supports(accessor.getIdType())){
                throw new XflatException(String.format("Cannot serialize class %s to table %s: ID type %s not supported by the table's Id generator %s.",
                                clazz, this.name, accessor.getIdType(), this.idGenerator));
            }
        }

        ConvertingTable<T> ret = new ConvertingTable<>(clazz, this.name);
        
        ret.setConversionService(this.db.getConversionService());
        PojoConverter converter = this.db.getPojoConverter();
        if(converter != null){
            ret.setAlternateIdExpression(converter.idSelector(clazz));
        }
        return ret;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="EngineProvider implementation">
    
    @Override
    public EngineBase provideEngine(){
        this.lastActivity = System.currentTimeMillis();
        return this.ensureSpinUp();
    }
    
    private EngineBase makeNewEngine(File file){
        synchronized(this){
            //TODO: engines will in the future be configurable & based on a strategy
            
            EngineBase ret = db.getEngineFactory().newEngine(file, name, config);

            ret.setConversionService(db.getConversionService());
            ret.setExecutorService(db.getExecutorService());
            
            if(ret instanceof ShardedEngineBase){
                //give it a metadata factory centered in its own file.  If it uses this,
                //it must also use the file as a directory.
                ((ShardedEngineBase)ret).setMetadataFactory(new TableMetadataFactory(this.db, file));
            }
            
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
            EngineBase newEngine = makeNewEngine(engineFile);
            if(!this.engine.compareAndSet(engine, newEngine)){
                //another thread has changed the engine - spinwait and retry if necessary
                long waitUntil = System.nanoTime() + 250;
                while(System.nanoTime() - waitUntil < 0){
                    engine = this.engine.get();
                    if(engine == null)
                        continue;
                    
                    if(engine.getState() == EngineState.SpinningUp ||
                            engine.getState() == EngineState.SpunUp ||
                            engine.getState() == EngineState.Running){
                        return engine;
                    }
                    //still in the wrong state, retry recursive
                    return this.ensureSpinUp();
                }
            }else{
                engine = newEngine;
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
    
    public EngineBase spinDown(){
        synchronized(this){
            final EngineBase engine = this.engine.getAndSet(null);
            EngineState state;
            if(engine == null ||
                    (state = engine.getState()) == EngineState.SpinningDown ||
                    state == EngineState.SpunDown)
                //another thread already spinning it down
                return engine;

            Log l = LogFactory.getLog(getClass());
            if(l.isTraceEnabled())
                l.trace(String.format("Spinning down table %s", this.name));
        
            
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
            
            return engine;
        }
    }
    
    //</editor-fold>



}
