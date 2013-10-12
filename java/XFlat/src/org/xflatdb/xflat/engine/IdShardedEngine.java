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
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jdom2.Element;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.ShardsetConfig;
import org.xflatdb.xflat.XFlatConstants;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.db.Engine;
import org.xflatdb.xflat.db.EngineAction;
import org.xflatdb.xflat.db.EngineActionEx;
import org.xflatdb.xflat.db.ShardedEngineBase;
import org.xflatdb.xflat.query.EmptyCursor;
import org.xflatdb.xflat.query.Interval;
import org.xflatdb.xflat.query.IntervalComparator;
import org.xflatdb.xflat.query.IntervalProvider;
import org.xflatdb.xflat.query.IntervalSet;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;

/**
 * An engine that shards the table based on ID.  This engine manages several sub-engines,
 * which each manage one shard.
 * @author Gordon
 */
public class IdShardedEngine<T> extends ShardedEngineBase<T> {
    
    private Map<Cursor<Element>, String> crossShardQueries = new ConcurrentHashMap<>();
    
    /**
     * Creates a new IdShardedEngine for the given directory, with the given table name,
     * using the given configuration.
     * @param file The directory in which the shard files will be located.
     * @param tableName The name of the sharded table.
     * @param config The configuration of the sharded table.
     */
    public IdShardedEngine(File file, String tableName, ShardsetConfig<T> config){
        super(file, tableName, config);
        
        if((config.getShardPropertySelector().getExpression() == null ? XPathQuery.Id.getExpression() != null : !config.getShardPropertySelector().getExpression().equals(XPathQuery.Id.getExpression())) ||
                config.getShardPropertySelector().getNamespace("db") != XFlatConstants.xFlatNs){
            throw new XFlatException("IdShardedEngine must be sharded by the expression '@db:id' where db is the XFlat Namespace");
        }
    }
    
    /**
     * Returns true if all cross-shard queries are finished.
     */
    @Override
    protected boolean isSpunDown(){
        return super.isSpunDown() && crossShardQueries.isEmpty();
    }
    
    /**
     * Gets a list of shard intervals over which the query should be executed.
     * This is obtained by dissecting the query according to ID and then
     * looking for known shards that intersect the dissected query.
     * @param query The query to dissect in order to create the execution plan.
     * @return A set of intervals mapping to shards over which this query needs to execute.
     */
    private List<Interval<T>> getExecutionPlan(XPathQuery query){
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
        ensureWriteReady();
        try{
            doWithEngine(getInterval(id), new EngineActionEx<Object, DuplicateKeyException>(){
                @Override
                public Object act(Engine engine) throws DuplicateKeyException {
                    engine.insertRow(id, data);
                    return null;
                }
            });
        }finally{
            writeComplete();
        }
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
    public Cursor<Element> queryTable(final XPathQuery query) {
        query.setConversionService(this.getConversionService());
        
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
    public Element replaceRow(final String id, final Element data) throws KeyNotFoundException {
        ensureWriteReady();
        try{
            return doWithEngine(getInterval(id), new EngineActionEx<Element, KeyNotFoundException>(){
                @Override
                public Element act(Engine engine) throws KeyNotFoundException {
                    return engine.replaceRow(id, data);
                }
            });
        }finally{
            writeComplete();
        }
    }

    @Override
    public boolean update(final String id, final XPathUpdate update) throws KeyNotFoundException {
        ensureWriteReady();
        try{
            return doWithEngine(getInterval(id), new EngineActionEx<Boolean, KeyNotFoundException>(){
                @Override
                public Boolean act(Engine engine) throws KeyNotFoundException {
                    return engine.update(id, update);
                }
            });
        }finally{
            writeComplete();
        }
    }

    @Override
    public int update(final XPathQuery query, final XPathUpdate update) {
        ensureWriteReady();
        try{
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
        }finally{
            writeComplete();
        }
    }

    @Override
    public boolean upsertRow(final String id, final Element data) {
        ensureWriteReady();
        try{
            return doWithEngine(getInterval(id), new EngineAction<Boolean>(){
                @Override
                public Boolean act(Engine engine) {
                    return engine.upsertRow(id, data);
                }
            });
        }finally{
            writeComplete();
        }
    }

    @Override
    public void deleteRow(final String id) throws KeyNotFoundException {
        ensureWriteReady();
        try{
            doWithEngine(getInterval(id), new EngineActionEx<Object, KeyNotFoundException>(){
                @Override
                public Object act(Engine engine) throws KeyNotFoundException {
                    engine.deleteRow(id);
                    return null;
                }
            });
        }finally{
            writeComplete();
        }
    }

    @Override
    public int deleteAll(final XPathQuery query) {
        ensureWriteReady();
        try{
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
        }finally{
            writeComplete();
        }
    }

    
    /**
     * A cursor that queries across multiple engines.
     * This cursor is NOT thread-safe.
     */
    private class CrossShardQueryCursor implements Cursor<Element>{

        private final XPathQuery query;
        private final List<Interval<T>> intervals;
        
        private Set<Cursor<Element>> openCursors = new HashSet<>();
        private boolean closed = false;
        
        public CrossShardQueryCursor(XPathQuery query, List<Interval<T>> shardIntervals){
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
                            throw new XFlatException("Exception closing cursor for shard " + config.getIntervalProvider().getName(intervals.get(intervalIndex)), ex);
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
        public void close() throws XFlatException {
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
                throw new XFlatException("Exception while closing multi-shard cursor", last);
            }
        }
    }

    
}
