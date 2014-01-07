/* 
*	Copyright 2013 Gordon Burgett and individual contributors
*
*	Licensed under the Apache License, Version 2.0 (the "License");
*	you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*	Unless required by applicable law or agreed to in writing, software
*	distributed under the License is distributed on an "AS IS" BASIS,
*	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*	See the License for the specific language governing permissions and
*	limitations under the License.
*/
package org.xflatdb.xflat.db;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.xflatdb.xflat.EngineStateException;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.db.EngineBase.SpinDownEvent;
import org.xflatdb.xflat.db.EngineBase.SpinDownEventHandler;

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
    
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    
    //</editor-fold>
    
    IdGenerator idGenerator;
    
    TableConfig config;
    
    long lastActivity = System.currentTimeMillis();
    
    Log log = LogFactory.getLog(getClass());
    
    /**
     * Called to determine whether the table has been inactive long enough
     * that it can be spun down.
     * @return true if the engine is inactive and has no uncommitted data.
     */
    public boolean canSpinDown(){
        EngineBase engine = this.engine.get();
        return lastActivity + config.getInactivityShutdownMs() < System.currentTimeMillis() && engine != null && !engine.hasUncomittedData();
    }
    
    public EngineBase getEngine(){
        return this.engine.get();
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
        
        //go ahead and spin up the engine if necessary
        provideEngine();
        
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
                throw new XFlatException(String.format("Cannot serialize class %s to table %s: ID type %s not supported by the table's Id generator %s.",
                                clazz, this.name, accessor.getIdType(), this.idGenerator));
            }
        }

        ConvertingTable<T> ret = new ConvertingTable<>(clazz, this.name);
        
        ret.setConversionService(this.db.getConversionService());
        
        //check if there's an alternate ID expression we can use for queries that come through
        //the converting table.
        if(accessor.hasId()){
            XPathExpression<Object> alternateId = accessor.getAlternateIdExpression();
            if(alternateId != null){
                ret.setAlternateIdExpression(alternateId);
            }
        }
        
        return ret;
    }
    
    //</editor-fold>
    
    //<editor-fold desc="EngineProvider implementation">
    
    @Override
    public EngineBase provideEngine(){
        long newActivity = System.currentTimeMillis();
        boolean didLock = false;
        try{
            //are we within 100ms of being able to be spun down?
            if(lastActivity + config.getInactivityShutdownMs() - 100 < newActivity){
                //if so, lock
                lock.readLock().lock();
                
                didLock = true;
            }
            
            //for the next 2990ms (default inactivity shutdown) we should be OK to not lock,
            //just want to be careful anytime we're close to interacting with a spin down.
            this.lastActivity = System.currentTimeMillis();
            
            return this.ensureSpinUp(didLock);
        }
        finally{
            if(didLock)
                lock.readLock().unlock();
        }
    }
    
    public void notifyRecoveryComplete(){
        this.lock.writeLock().lock();
        try{
            EngineBase engine = this.engine.get();
            
            if(engine.getState() == EngineState.SpunUp){
                //need to give the engine the go-ahead
                engine.beginOperations();
            }
            
        }
        finally{
            this.lock.writeLock().unlock();
        }
    }
    
    private EngineBase makeNewEngine(File file){
        
        //TODO: engines will in the future be configurable & based on a strategy

        EngineBase ret = db.getEngineFactory().newEngine(file, name, config);

        ret.setConversionService(db.getConversionService());
        ret.setExecutorService(db.getExecutorService());
        ret.setTransactionManager(db.getEngineTransactionManager());
        ret.setIdGenerator(this.idGenerator);

        if(ret instanceof ShardedEngineBase){
            //give it a metadata factory centered in its own file.  If it uses this,
            //it must also use the file as a directory.
            ((ShardedEngineBase)ret).setMetadataFactory(new TableMetadataFactory(this.db, file));
        }

        ret.loadMetadata(engineMetadata);



        return ret;
    }

    private EngineBase ensureSpinUp(final boolean didLock){
        EngineBase engine = this.engine.get();
        
        EngineState state;
        
        if(engine == null ||
                (state = engine.getState()) == EngineState.SpinningDown ||
                state == EngineState.SpunDown){
            
            //must unlock a read lock before we enter a write lock
            if(didLock)
                lock.readLock().unlock();
            lock.writeLock().lock();
            try{
                //re-check condition after locking
                engine = this.engine.get();
                if(engine == null ||
                (state = engine.getState()) == EngineState.SpinningDown ||
                state == EngineState.SpunDown){
                    
                    EngineBase newEngine = makeNewEngine(engineFile);
                    if(this.engine.compareAndSet(engine, newEngine)){
                        engine = newEngine;
                        if(log.isTraceEnabled())
                            log.trace(String.format("Spinning up new engine for table %s", this.name));
                    }else{
                        //dunno how we got here, so long as we have the lock there ought to be no way.
                        newEngine = this.engine.get();
                        throw new EngineStateException("Synchronization error on spin up, could not put new engine", 
                                newEngine == null ? EngineState.Uninitialized : newEngine.getState());
                    }
                }
            }finally{
                //we can downgrade to a readlock
                if(didLock)
                    lock.readLock().lock();
                
                lock.writeLock().unlock();
                
            }
        }
        else if(state == EngineState.SpinningUp ||
                            state == EngineState.SpunUp ||
                            state == EngineState.Running){
            //good to go
            return engine;
        }
        
        //spinUp returns true if this thread successfully spun it up
        if(engine.spinUp()){
            if(this.db.getState() == XFlatDatabase.DatabaseState.Running){
                //spin-up could be called when initializing, in which case
                //the engine needs to be ready to do recovery but not running yet.
                engine.beginOperations();
            }
        }
        
        return engine;
    }
    
    /**
     * Spins down the engine, leaving the metadata in a state where it will
     * be required to spin up a new engine before providing it.
     * @param ignoreUncommitted Whether to require a spin down even if the engine has uncommitted
     * data, effectively automatically reverting it.  Usually only set when the
     * entire database is being shut down.
     * @param force whether to use forceSpinDown instead of a natural spin down.
     * @return The engine that was spun down.
     */
    public EngineBase spinDown(boolean ignoreUncommitted, boolean force){
        lock.writeLock().lock();
        try{
            EngineBase engine = this.engine.get();
            
            EngineState state;
            if(engine == null ||
                    (state = engine.getState()) == EngineState.SpinningDown ||
                    state == EngineState.SpunDown){
                //another thread already spinning it down
                return engine;
                
            }
            
            try{
            engine.getTableLock();

                if(engine.hasUncomittedData() && !ignoreUncommitted){
                    //can't spin it down, return the engine
                    return engine;
                }
                
                this.engine.compareAndSet(engine, null);
            }finally{
                //table lock no longer needed 
                engine.releaseTableLock();
            }

            if(log.isTraceEnabled())
                log.trace(String.format("Spinning down table %s", this.name));
        
            
            if(!force && engine.spinDown(new SpinDownEventHandler(){
                    @Override
                    public void spinDownComplete(SpinDownEvent event) {                                         
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
        finally{
            lock.writeLock().unlock();
        }
    }
    
    //</editor-fold>



}
