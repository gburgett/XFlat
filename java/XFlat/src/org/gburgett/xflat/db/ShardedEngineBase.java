/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.gburgett.xflat.Range;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.engine.IdShardedEngine;
import org.jdom2.Element;

/**
 *
 * @author Gordon
 */
public abstract class ShardedEngineBase<T> extends EngineBase {
    protected ConcurrentMap<Range<T>, TableMetadata> openShards = new ConcurrentHashMap<>();
    
    //the engines that are spinning down while this engine spins down
    private Map<Range<T>, EngineBase> spinningDownEngines = new HashMap<>();
    
    private final Object spinDownSyncRoot = new Object();
    
    protected File directory;
        
    protected ShardsetConfig<T> config;
    
    public ShardedEngineBase(File file, String tableName, ShardsetConfig<T> config){
        super(tableName);
        
        this.directory = file;
        this.config = config;
        
        if(file.exists() && ! file.isDirectory()){
            //TODO: automatically convert old data in this case.
            throw new UnsupportedOperationException("Cannot create sharded engine for existing non-sharded table");
        }
    }
    
    protected Range<T> getRangeForRow(Element row){
        Object selected = config.getShardPropertySelector().evaluateFirst(row);
        return getRange(selected);
    }
    
    protected Range<T> getRange(Object value){
        T converted;
        try {
            converted = this.getConversionService().convert(value, this.config.getShardPropertyClass());
        } catch (ConversionException ex) {
            throw new XflatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                    " selected non-convertible value " + value, ex);
        }
        
        Range<T> ret;
        try{
            ret = this.config.getRangeProvider().getRange(converted);
        }catch(java.lang.NullPointerException ex){
            throw new XflatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                    " selected null value which cannot be mapped to a range");
        }
        
        if(ret == null){
            throw new XflatException("Data cannot be sharded: sharding expression " + config.getShardPropertySelector().getExpression() +
                    " selected value " + converted + " which cannot be mapped to a range");
        }
        
        return ret;
    }
    
    protected EngineBase getEngine(Range<T> range){
        
        EngineState state = getState();
        if(state == EngineState.Uninitialized || state == EngineState.SpunDown){
            throw new XflatException("Attempt to read or write to an engine in an uninitialized state");
        }
        
        
        TableMetadata ret = openShards.get(range);
        
        if(ret == null){
            //definitely ensure we aren't spinning down before we start up a new engine
            synchronized(spinDownSyncRoot){
                
                state = getState();
                if(state == EngineState.SpunDown){
                    throw new XflatException("Engine has already spun down");
                }
                
                //build the new metadata element so we can use it to provide engines
                String name = range.getName();
                ret = this.getMetadataFactory().makeTableMetadata(name, new File(directory, name + ".xml"));
                TableMetadata weWereLate = openShards.putIfAbsent(range, ret);
                if(weWereLate != null){
                    //another thread put the new metadata already
                    ret = weWereLate;
                }

                if(state == EngineState.SpinningDown){
                    EngineBase eng = spinningDownEngines.get(range);
                    if(eng == null){
                        //we're requesting a new engine for some kind of read, get it and immediately begin spinning it down.
                        eng = ret.provideEngine();
                        spinningDownEngines.put(range, eng);
                        ret.spinDown();
                    }
                    return eng;
                }
            }
        }
        
        return ret.provideEngine();
    }
    
    
    protected void update(){
        Iterator<TableMetadata> it = openShards.values().iterator();
        while(it.hasNext()){
            TableMetadata table = it.next();
            if(table.getLastActivity() + 3000 < System.currentTimeMillis()){
                //remove right now - if between the check and the remove we got some activity
                //then oh well, we can spin up a new instance.
                it.remove();
                
                table.spinDown();
                try {
                    this.getMetadataFactory().saveTableMetadata(table);
                } catch (IOException ex) {
                    //oh well
                    this.log.warn("Failure to save metadata for sharded table " + this.getTableName() + " shard " + table.getName(), ex);
                }
            }
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
        
        //we'll spin up tables as we need them.
        this.getExecutorService().scheduleWithFixedDelay(new Runnable(){
            @Override
            public void run() {
                EngineState state = getState();
                if(state == EngineState.SpinningDown ||
                        state == EngineState.SpunDown){
                    throw new RuntimeException("task termination");
                }
                
                update();
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
        if(!this.state.compareAndSet(EngineState.Running, EngineState.SpinningDown)){
            //we're in the wrong state.
            return false;
        }
        
        synchronized(spinDownSyncRoot){
            for(Map.Entry<Range<T>, TableMetadata> m : this.openShards.entrySet()){
                EngineBase spinningDown = m.getValue().spinDown();
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
                    if(spinningDownEngines.isEmpty()){
                        if(state.compareAndSet(EngineState.SpinningDown, EngineState.SpunDown)){
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
                    }
                    //give it a few more ms just in case
                }
            }
        };
        
        this.getExecutorService().scheduleWithFixedDelay(spinDownMonitor, 5, 10, TimeUnit.MILLISECONDS);
        
        
        return true;
    }

    @Override
    protected boolean forceSpinDown() {
        this.state.set(EngineState.SpunDown);
        
        synchronized(spinDownSyncRoot){
            for(Map.Entry<Range<T>, TableMetadata> m : this.openShards.entrySet()){
                EngineBase spinningDown = m.getValue().spinDown();
                this.spinningDownEngines.put(m.getKey(), spinningDown);
            }
            
            for(EngineBase spinningDown : spinningDownEngines.values()){
                spinningDown.forceSpinDown();
            }
        }
        
        return true;
    }
}
