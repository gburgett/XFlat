/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineAction;
import org.gburgett.xflat.db.ShardedEngineBase;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.EmptyCursor;
import org.gburgett.xflat.query.Interval;
import org.gburgett.xflat.query.IntervalComparator;
import org.gburgett.xflat.query.IntervalProvider;
import org.gburgett.xflat.query.IntervalSet;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom2.Element;

/**
 * An engine that shards the table based on ID.  This engine manages several sub-engines,
 * which each manage one shard.
 * @author Gordon
 */
public class IdShardedEngine<T> extends ShardedEngineBase<T> {
    
    private Map<Cursor<Element>, String> crossShardQueries = new ConcurrentHashMap<>();
    
    public IdShardedEngine(File file, String tableName, ShardsetConfig<T> config){
        super(file, tableName, config);
        
        if((config.getShardPropertySelector().getExpression() == null ? XpathQuery.Id.getExpression() != null : !config.getShardPropertySelector().getExpression().equals(XpathQuery.Id.getExpression())) ||
                config.getShardPropertySelector().getNamespace("db") != XFlatDatabase.xFlatNs){
            throw new XflatException("IdShardedEngine must be sharded by the expression '@db:id' where db is the XFlat Namespace");
        }
    }
    
    @Override
    protected boolean isSpunDown(){
        return super.isSpunDown() && crossShardQueries.isEmpty();
    }
    
    private List<Interval<T>> getExecutionPlan(XpathQuery query){
        final IntervalProvider<T> provider = config.getIntervalProvider();
        
        IntervalSet<T> dissectedQuery = query.dissectId(provider.getComparator(), config.getShardPropertyClass());
        
        //go through each known shard, and see if the query intersects it.
        List<Interval<T>> ret = new ArrayList<>();
        for(Interval<T> known : this.knownShards.keySet()){
            if(dissectedQuery.intersects(known, provider.getComparator())){
                ret.add(known);
            }
        }
        
        Collections.sort(ret, new IntervalComparator<>(provider.getComparator()));
        return ret;
    }

    @Override
    public void insertRow(final String id, final Element data) throws DuplicateKeyException {
        
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.insertRow(id, data);
                return null;
            }
        });
    }

    @Override
    public Element readRow(final String id) {
        return doWithEngine(getInterval(id), new EngineAction<Element>(){
            @Override
            public Element act(Engine engine) {
                return engine.readRow(id);
            }
        });
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        query.setConversionService(this.getConversionService());
        
        return internalQuery(query);
    }
    
    private Cursor<Element> internalQuery(final XpathQuery query){
        List<Interval<T>> shardIntervals = getExecutionPlan(query);
        
        //no known shards intersect the query
        if(shardIntervals.isEmpty()){
            return EmptyCursor.instance();
        }
        
        //only one known shard intersects the query, just use it
        if(shardIntervals.size() == 1){
            return doWithEngine(shardIntervals.get(0), new EngineAction<Cursor<Element>>(){
                @Override
                public Cursor<Element> act(Engine engine) {
                    return engine.queryTable(query);
                }
            });
        }
        
        //we need a cursor that will cross multiple shards.
        Cursor<Element> ret = new CrossShardQueryCursor(query, shardIntervals);
        //remember it so we don't spin down while it's open
        this.crossShardQueries.put(ret, "");
        return ret;
    }

    @Override
    public void replaceRow(final String id, final Element data) throws KeyNotFoundException {
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.replaceRow(id, data);
                return null;
            }
        });
    }

    @Override
    public boolean update(final String id, final XpathUpdate update) throws KeyNotFoundException {
        return doWithEngine(getInterval(id), new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.update(id, update);
            }
        });
    }

    @Override
    public int update(final XpathQuery query, final XpathUpdate update) {
        query.setConversionService(this.getConversionService());
        update.setConversionService(this.getConversionService());
        
        EngineAction<Integer> action = new EngineAction<Integer>(){
            @Override
            public Integer act(Engine engine) {
                return engine.update(query, update);
            }
        };
        
        int updated = 0;
        for(Interval<T> shardInterval : this.getExecutionPlan(query)){
            updated += doWithEngine(shardInterval, action);
        }
        
        return updated;
    }

    @Override
    public boolean upsertRow(final String id, final Element data) {
        return doWithEngine(getInterval(id), new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.upsertRow(id, data);
            }
        });
    }

    @Override
    public void deleteRow(final String id) throws KeyNotFoundException {
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.deleteRow(id);
                return null;
            }
        });
    }

    @Override
    public int deleteAll(final XpathQuery query) {
        query.setConversionService(this.getConversionService());
        EngineAction<Integer> action = new EngineAction<Integer>(){
            @Override
            public Integer act(Engine engine) {
                return engine.deleteAll(query);
            }
        };
        
        int count = 0;
        for(Interval<T> shard : getExecutionPlan(query)){
            count += doWithEngine(shard, action);
        }
        
        return count;
    }

    
    /**
     * A cursor that queries across multiple engines.
     * This cursor is NOT thread-safe.
     */
    private class CrossShardQueryCursor implements Cursor<Element>{

        private final XpathQuery query;
        private final List<Interval<T>> intervals;
        
        private Set<Cursor<Element>> openCursors = new HashSet<>();
        private boolean closed = false;
        
        public CrossShardQueryCursor(XpathQuery query, List<Interval<T>> shardIntervals){
            this.query = query;
            this.intervals = shardIntervals;
        }
        
        private void closeCursor(Cursor<Element> cursor) throws Exception{
            openCursors.remove(cursor);
            cursor.close();
        }
        
        private Cursor<Element> openCursor(Interval<T> interval){
            Cursor<Element> ret = doWithEngine(interval, new EngineAction<Cursor<Element>>(){
                @Override
                public Cursor<Element> act(Engine engine) {
                    return engine.queryTable(query);
                }
            });
            openCursors.add(ret);
            return ret;
        }
        
        @Override
        public Iterator<Element> iterator() {
            if(this.closed){
                throw new IllegalStateException("Cursor is closed");
            }
            
            return new Iterator<Element>(){
                private int intervalIndex = 0;
                private Iterator<Element> currentCursorIterator = null;
                private Cursor<Element> currentCursor = null;
                
                @Override
                public boolean hasNext() {
                    if(closed){
                        throw new IllegalStateException("Cursor is closed");
                    }
                    
                    if(intervalIndex >= intervals.size()){
                        return false;
                    }

                    if(currentCursorIterator != null){
                        if(currentCursorIterator.hasNext()){
                            return true;
                        }
                        try {
                            //current open cursor is done, need to close it
                            closeCursor(currentCursor);
                        } catch (Exception ex) {
                            throw new XflatException("Exception closing cursor for shard " + config.getIntervalProvider().getName(intervals.get(intervalIndex)), ex);
                        }
                        currentCursor = null;
                        currentCursorIterator = null;
                        //advance the index
                        intervalIndex++;
                        if(intervalIndex >= intervals.size()){
                            return false;
                        }
                    }

                    //get the next cursor and see if it has anything
                    this.currentCursor = openCursor(intervals.get(intervalIndex));
                    this.currentCursorIterator = currentCursor.iterator();
                    
                    return hasNext();
                }

                @Override
                public Element next() {
                    if(closed){
                        throw new IllegalStateException("Cursor is closed");
                    }
                    
                    if(hasNext()){
                        return currentCursorIterator.next();
                    }
                    throw new IllegalStateException("Iterator does not have next");
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Remove not supported.");
                }
            };
        }

        @Override
        public void close() throws XflatException {
            if(this.closed){
                return;
            }
            this.closed = true;
            
            Exception last = null;
            for(Cursor<Element> c : openCursors){
                try{
                    c.close();
                }catch(Exception e){
                    last = e;
                }
            }
            
            IdShardedEngine.this.crossShardQueries.remove(this);
            
            if(last != null){
                throw new XflatException("Exception while closing multi-shard cursor", last);
            }
        }
    }

    
}
