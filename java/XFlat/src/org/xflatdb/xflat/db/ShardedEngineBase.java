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
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.jdom2.Element;
import org.xflatdb.xflat.EngineStateException;
import org.xflatdb.xflat.ShardsetConfig;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.query.Interval;

/**
 * The base class for all engines that are sharded.  Sharded engines store the table
 * data across multiple files.
 * @author Gordon
 */
public abstract class ShardedEngineBase<T> extends EngineBase {
    /** The shards that are currently open and ready to use. */
    protected ConcurrentMap<Interval<T>, TableMetadata> openShards = new ConcurrentHashMap<>();
    /** The shards that are known to exist on disk. */
    protected ConcurrentMap<Interval<T>, File> knownShards = new ConcurrentHashMap<>();
    
    
    //the engines that are spinning down while this engine spins down
    private Map<Interval<T>, EngineBase> spinningDownEngines = new HashMap<>();
    
    private final Object spinDownSyncRoot = new Object();
    
    /** The directory managed by this sharded engine. */
    protected File directory;
        
    /** The configuration of this sharded table. */
    protected ShardsetConfig<T> config;
    
    private TableMetadataFactory metadataFactory;
    /**
     * Gets a metadata factory which can be used to generate {@link TableMetadata} objects.
     * This allows the engine to spawn additional engines as necessary.
     * The metadata factory is set up to read and write metadata from the same
     * {@link File} given to the {@link EngineFactory#newEngine(java.io.File, java.lang.String, org.xflatdb.xflat.TableConfig) } method,
     * so if the engine uses this it must also use that file as a directory.
     * @return 
     */
    protected TableMetadataFactory getMetadataFactory(){
        return this.metadataFactory;
    }
    /** @see #getMetadataFactory() */
    protected void setMetadataFactory(TableMetadataFactory metadataFactory){
        this.metadataFactory = metadataFactory;
    }
    
    /**
     * Creates a new ShardedEngine for the given directory, table name, and configuration
     * @param file The directory in which the shards are saved.
     * @param tableName The name of the sharded table.
     * @param config The sharding configuration.
     */
    protected ShardedEngineBase(File file, String tableName, ShardsetConfig<T> config){
        super(tableName);
        
        this.directory = file;
        this.config = config;
        
        if(file.exists() && ! file.isDirectory()){
            //TODO: automatically convert old data in this case.
            throw new UnsupportedOperationException("Cannot create sharded engine for existing non-sharded table");
        }
    }
    
    /**
     * Gets the interval in which the row should reside, based on the shard property
     * selector in the configuration.
     * @param row The row representing converted data.
     * @return The interval in which the row should reside.
     */
    protected Interval<T> getRangeForRow(Element row){
        Object selected = config.getShardPropertySelector().evaluateFirst(row);
        return getInterval(selected);
    }
    
    /**
     * Gets the interval in which the given selected shard property should reside,
     * based on the IntervalProvider given in the configuration.
     * @param value The shard property selected by the shard property selector in the configuration.
     * @return The interval in which the shard property should reside.
     */
    protected Interval<T> getInterval(Object value){
        T converted;
        if(value == null || !this.config.getShardPropertyClass().isAssignableFrom(value.getClass())){
            try {
                converted = this.getConversionService().convert(value, this.config.getShardPropertyClass());
            } catch (ConversionException ex) {
                throw new XFlatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                        " selected non-convertible value " + value, ex);
            }
        }
        else{
            converted = (T)value;
        }
        
        Interval<T> ret;
        try{
            ret = this.config.getIntervalProvider().getInterval(converted);
        }catch(java.lang.NullPointerException ex){
            throw new XFlatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                    " selected null value which cannot be mapped to a range");
        }
        
        if(ret == null){
            throw new XFlatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                    " selected value " + converted + " which cannot be mapped to a range");
        }
        
        return ret;
    }
    
    private EngineBase getEngine(Interval<T> interval){
        
        TableMetadata metadata = openShards.get(interval);
        if(metadata == null){
            //definitely ensure we aren't spinning down before we start up a new engine
            synchronized(spinDownSyncRoot){
                
                EngineState state = getState();
                if(state == EngineState.SpunDown){
                    throw new XFlatException("Engine has already spun down");
                }
                
                //build the new metadata element so we can use it to provide engines
                String name = this.config.getIntervalProvider().getName(interval);
                File file = new File(directory, name + ".xml");
                this.knownShards.put(interval, file);
                
                metadata = this.getMetadataFactory().makeTableMetadata(this.getTableName(), file);
                metadata.config = new TableConfig(); //not even really used for our purposes
                
                TableMetadata weWereLate = openShards.putIfAbsent(interval, metadata);
                if(weWereLate != null){
                    //another thread put the new metadata already
                    metadata = weWereLate;
                }

                if(state == EngineState.SpinningDown){
                    EngineBase eng = spinningDownEngines.get(interval);
                    if(eng == null){
                        //we're requesting a new engine for some kind of read, get it and let the task spin it down.
                        eng = metadata.provideEngine();
                        spinningDownEngines.put(interval, eng);
                        return eng;
                    }
                }
            }
        }
        
        return metadata.provideEngine();
    }
    
    /**
     * Performs an action with the appropriate engine for the given shard interval.
     * The shard interval must be one that is provided by the IntervalProvider
     * for this sharded engine, which maps to a shard file on disk.
     * 
     * @param <U> The generic type of the value to return.
     * @param range The shard interval mapping to a shard file on disk.
     * @param action The action to perform once the engine is provided.
     * @return The value returned by the action.
     */
    protected <U> U doWithEngine(Interval<T> range, EngineAction<U> action){
        
        EngineState state = getState();
        if(state == EngineState.Uninitialized || state == EngineState.SpunDown){
            throw new XFlatException("Attempt to read or write to an engine in an uninitialized state");
        }
        
        try{
            return action.act(getEngine(range));
        }
        catch(EngineStateException ex){
            //try one more time with a potentially new engine, if we still fail then let it go
            return action.act(getEngine(range));
        }
    }
    
    /**
     * Executed by the recurring update task every 500 ms in order to clean up
     * the shardset and spin down any inactive shards.
     */
    protected void updateTask(){
        Iterator<TableMetadata> it = openShards.values().iterator();
        while(it.hasNext()){
            TableMetadata table = it.next();
            if(table.canSpinDown()){
                EngineBase spinDown = table.spinDown(false);
                
                //don't remove any metadata.  It's too dangerous with the way the concurrency is structured.
                
                try {
                    this.getMetadataFactory().saveTableMetadata(table);
                } catch (IOException ex) {
                    //oh well
                    this.log.warn("Failure to save metadata for sharded table " + this.getTableName() + " shard " + table.getName(), ex);
                }
            }
        }
        
    }
    
    /**
     * Returns true if any of the individual shards have uncommitted data.
     * @return 
     */
    @Override
    protected boolean hasUncomittedData() {
        EngineState state = this.state.get();
        if(state == EngineState.SpinningDown){
            for(EngineBase e : this.spinningDownEngines.values()){
                if(e.hasUncomittedData()){
                    return true;
                }
            }
        }
        else if(state == EngineState.Running){
            for(TableMetadata table : this.openShards.values()){
                EngineBase e = table.getEngine();
                if(e != null && e.hasUncomittedData()){
                    return true;
                }
            }
        }
        return false;
    }
        
    /**
     * Reverts the given transaction.  If not recovering, this does nothing,
     * since the individual shards will have been bound to the transaction themselves.<br/>
     * If recovering, every shard in the shardset will be opened and the transaction 
     * will be reverted in that shard.
     * @param txId The ID of the (potentially partially-committed) transaction to revert.
     * @param isRecovering true if this was called during a recovery operation on startup.
     */
    @Override
    public void revert(long txId, boolean isRecovering){
        if(!isRecovering){
            //the individual shard engines will also have been registered.
            return;
        }
        
        //we will need to revert over all known shards in order to recover.
        for(Interval<T> interval : this.knownShards.keySet()){
            this.getEngine(interval).revert(txId, isRecovering);
        }
    }
   
    @Override
    protected boolean spinUp() {
        if(!this.state.compareAndSet(EngineState.Uninitialized, EngineState.SpinningUp)){
            return false;
        }
        
        if(!directory.exists()){
            directory.mkdirs();
        }
        else{
            //need to scan the directory for existing known shards.
            for(File f : directory.listFiles()){
                if(!f.getName().endsWith(".xml")){
                    continue;
                }
                
                String shardName = f.getName().substring(0, f.getName().length() - 4);
                Interval<T> i = config.getIntervalProvider().getInterval(shardName);
                if(i != null){
                    knownShards.put(i, f);
                }
            }
        }
        
        //we'll spin up tables as we need them.
        this.getExecutorService().scheduleWithFixedDelay(new Runnable(){
            @Override
            public void run() {
                EngineState state = getState();
                if(state == EngineState.SpinningDown ||
                        state == EngineState.SpunDown){
                    throw new RuntimeException("task termination");
                }
                
                updateTask();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
        
        
        this.state.compareAndSet(EngineState.SpinningUp, EngineState.SpunUp);
        return true;
    }

    @Override
    protected boolean beginOperations() {
        return this.state.compareAndSet(EngineState.SpunUp, EngineState.Running);
    }

    @Override
    protected boolean spinDown(final SpinDownEventHandler completionEventHandler) {
        try{
            this.getTableLock();
            
            if(!this.state.compareAndSet(EngineState.Running, EngineState.SpinningDown)){
                //we're in the wrong state.
                return false;
            }

            synchronized(spinDownSyncRoot){
                for(Map.Entry<Interval<T>, TableMetadata> m : this.openShards.entrySet()){
                    EngineBase spinningDown = m.getValue().spinDown(true);
                    this.spinningDownEngines.put(m.getKey(), spinningDown);
                }
            }

            Runnable spinDownMonitor = new Runnable(){
                @Override
                public void run() {
                    if(getState() != EngineState.SpinningDown){
                        throw new RuntimeException("task complete");
                    }

                    synchronized(spinDownSyncRoot){
                        if(isSpunDown()){
                            if(state.compareAndSet(EngineState.SpinningDown, EngineState.SpunDown)){
                                if(completionEventHandler != null)
                                    completionEventHandler.spinDownComplete(new SpinDownEvent(ShardedEngineBase.this));
                            }
                            else{
                                //somehow we weren't in the spinning down state
                                forceSpinDown();
                            }
                            throw new RuntimeException("task complete");
                        }


                        Iterator<EngineBase> it = spinningDownEngines.values().iterator();
                        while(it.hasNext()){
                            EngineBase spinningDown = it.next();
                            EngineState state = spinningDown.getState();
                            if(state == EngineState.SpunDown || state == EngineState.Uninitialized){
                                it.remove();
                            }
                            else if(state == EngineState.Running){
                                spinningDown.spinDown(null);
                            }
                        }
                        //give it a few more ms just in case
                    }
                }
            };

            this.getExecutorService().scheduleWithFixedDelay(spinDownMonitor, 5, 10, TimeUnit.MILLISECONDS);


            return true;
        
        }
        finally{
            this.releaseTableLock();
        }
    }
    
    /**
     * Invoked in a synchronized context to see if the sharded engine is 
     * fully spun down.  Default implementation checks whether the spinning
     * down engines have all spun down.
     * @return 
     */
    protected boolean isSpunDown(){
        return spinningDownEngines.isEmpty();
    }

    @Override
    protected boolean forceSpinDown() {
        this.state.set(EngineState.SpunDown);
        
        synchronized(spinDownSyncRoot){
            for(Map.Entry<Interval<T>, TableMetadata> m : this.openShards.entrySet()){
                EngineBase spinningDown = m.getValue().spinDown(true);
                this.spinningDownEngines.put(m.getKey(), spinningDown);
            }
            
            for(EngineBase spinningDown : spinningDownEngines.values()){
                spinningDown.forceSpinDown();
            }
        }
        
        return true;
    }
}
