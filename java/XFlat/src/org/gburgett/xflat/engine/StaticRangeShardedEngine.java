/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.Range;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineState;
import org.gburgett.xflat.db.TableMetadata;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.jdom2.Element;

/**
 *
 * @author Gordon
 */
public class StaticRangeShardedEngine<T> extends EngineBase {

    private ConcurrentMap<Range<T>, TableMetadata> openShards = new ConcurrentHashMap<>();
    
    //the engines that are spinning down while this engine spins down
    private Map<Range<T>, EngineBase> spinningDownEngines = new HashMap<>();
    
    private final Object spinDownSyncRoot = new Object();
    
    private File directory;
    
    private DocumentFileWrapper wrapper;
    
    private ShardsetConfig<T> config;
    
    public StaticRangeShardedEngine(File file, String tableName, ShardsetConfig<T> config){
        this(file, new DocumentFileWrapper(file), tableName, config);
    }
    
    StaticRangeShardedEngine(File file, DocumentFileWrapper wrapper, String tableName, ShardsetConfig<T> config){
        super(tableName);
        
        this.directory = file;
        this.config = config;
        
        if(file.exists() && ! file.isDirectory()){
            //TODO: automatically convert old data in this case.
            throw new UnsupportedOperationException("Cannot create sharded engine for existing non-sharded table");
        }
    }
    
    private EngineBase getEngine(Range<T> range) throws IOException{
        
        EngineState state = getState();
        if(state == EngineState.Uninitialized || state == EngineState.SpunDown){
            throw new XflatException("Attempt to read or write to an engine in an uninitialized state");
        }
        
        
        TableMetadata ret = openShards.get(range);
        
        if(ret == null){
            //build the new metadata element so we can use it to provide engines
            String name = range.getName();
            ret = this.getMetadataFactory().makeTableMetadata(name, new File(directory, name + ".xml"));
            TableMetadata weWereLate = openShards.putIfAbsent(range, ret);
            if(weWereLate != null){
                //another thread put the new metadata already
                ret = weWereLate;
            }
        }
        
        if(state == EngineState.SpinningDown){
            synchronized(spinDownSyncRoot){
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
        
        return ret.provideEngine();
    }
    
    
    private void update(){
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
    protected boolean spinDown(SpinDownEventHandler completionEventHandler) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected boolean forceSpinDown() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void insertRow(String id, Element data) throws DuplicateKeyException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Element readRow(String id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void replaceRow(String id, Element data) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean update(String id, XpathUpdate update) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean upsertRow(String id, Element data) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteRow(String id) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int deleteAll(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
