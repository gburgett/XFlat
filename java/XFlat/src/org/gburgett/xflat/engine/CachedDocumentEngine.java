/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.EngineStateException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.hamcrest.Matcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

/**
 * This is an engine that caches the entire table in memory as a JDOM {@link Document}.
 * @author gordon
 */
public class CachedDocumentEngine extends EngineBase implements Engine {

    private final AtomicBoolean operationsReady = new AtomicBoolean(false);
    
    private final Object syncRoot = new Object();    
    
    private ConcurrentMap<String, Element> cache = null;
    
    private DocumentFileWrapper file;
    public DocumentFileWrapper getFile(){
        return file;
    }
    
    public CachedDocumentEngine(File file, String tableName){
        super(tableName);
        this.file = new DocumentFileWrapper(file);
    }
    
    protected CachedDocumentEngine(DocumentFileWrapper file, String tableName){
        super(tableName);
        this.file = file;
    }
    
    //<editor-fold desc="interface methods">
    @Override
    public void insertRow(String id, Element data) throws DuplicateKeyException {
        ensureReady();
        
        Element row = wrapInRow(data, id);
        Element existed = this.cache.putIfAbsent(id, row);
        if(existed != null){
            throw new DuplicateKeyException(id);
        }
        
        setLastActivity(System.currentTimeMillis());
        dumpCache();
    }

    @Override
    public Element readRow(String id) {
        Element row = this.cache.get(id);
        if(row == null){
            return null;
        }
        
        setLastActivity(System.currentTimeMillis());
        
        //lock the row
        synchronized(row){
            //clone the data
            return row.getChildren().get(0).clone();
        }
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        query.setConversionService(this.getConversionService());
        TableCursor ret = new TableCursor(this.cache.values(), query);
        this.openCursors.put(ret, "");
        setLastActivity(System.currentTimeMillis());
        
        return ret;
    }

    @Override
    public void replaceRow(String id, Element data) throws KeyNotFoundException {
        ensureReady();
        
        Element row = wrapInRow(data, id);
        Element replaced = this.cache.replace(id, row);
        if(replaced == null){
            throw new KeyNotFoundException(id);
        }
        
        setLastActivity(System.currentTimeMillis());
        dumpCache();
    }

    @Override
    public boolean update(String id, XpathUpdate update) throws KeyNotFoundException {
        ensureReady();
        
        Element row = this.cache.get(id);
        if(row == null){
            throw new KeyNotFoundException(id);
        }
        
        update.setConversionService(this.getConversionService());
        
        boolean ret = false;
        try {
            //lock the row
            synchronized(row){
                int updates = update.apply(row);
                ret = updates > 0;
            }
        } catch (JDOMException ex) {
            if(log.isDebugEnabled())
                log.debug("Exception while applying update " + update.toString(), ex);
            
            ret = false;
        }
        
        setLastActivity(System.currentTimeMillis());
        
        if(ret)
            dumpCache();
        
        return ret;
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        ensureReady();
        
        query.setConversionService(this.getConversionService());
        update.setConversionService(this.getConversionService());
        
        Matcher<Element> rowMatcher = query.getRowMatcher();
        
        int rowsUpdated = 0;
        
        for(Element row : this.cache.values()){
            synchronized(row){
                if(!rowMatcher.matches(row))
                    continue;
                try {
                    int updates = update.apply(row);
                    rowsUpdated = updates > 0 ? rowsUpdated + 1 : rowsUpdated;
                } 
                catch (JDOMException ex) {
                    if(log.isDebugEnabled())
                        log.debug("Exception while applying update " + update.toString(), ex);
                }
            }
        }
        
        setLastActivity(System.currentTimeMillis());
        
        if(rowsUpdated > 0){
            dumpCache();
        }
        
        return rowsUpdated;
    }

    @Override
    public boolean upsertRow(String id, Element data) {
        ensureReady();
        
        Element row = wrapInRow(data, id);
        Element existed = this.cache.put(id, row);
        
        setLastActivity(System.currentTimeMillis());
        dumpCache();
        
        return existed == null; //if none existed, then we inserted
    }

    @Override
    public void deleteRow(String id) throws KeyNotFoundException {
        ensureReady();
        
        Element removed = this.cache.remove(id);
        
        if(removed == null){
            throw new KeyNotFoundException(id);
        }
        
        setLastActivity(System.currentTimeMillis());
        dumpCache();
    }

    @Override
    public int deleteAll(XpathQuery query) {
        ensureReady();
        
        query.setConversionService(this.getConversionService());
        
        Matcher<Element> rowMatcher = query.getRowMatcher();
        Iterator<Map.Entry<String,Element>> it = this.cache.entrySet().iterator();
        
        int numRemoved = 0;
        
        while(it.hasNext()){
            Map.Entry<String, Element> entry = it.next();
            Element row = entry.getValue();
            synchronized(row){
                if(rowMatcher.matches(row)){
                    it.remove();
                    numRemoved++;
                }
            }
        }
        
        setLastActivity(System.currentTimeMillis());
        
        if(numRemoved > 0)
            dumpCache();
        
        return numRemoved;
    }
    
    //</editor-fold>

    @Override
    protected boolean spinUp() {
        if(!this.state.compareAndSet(EngineState.Uninitialized, EngineState.SpinningUp)){
            return false;
        }
        
        //concurrency level 4 - don't expect to need more than this.
        this.cache = new ConcurrentHashMap<>(16, 0.75f, 4);
        
        if(file.exists()){
            try {
                Document doc = this.file.readFile();
                List<Element> rowList = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
                //copy to array to avoid concurrent modification exception
                Element[] rowArr = new Element[rowList.size()];
                for(Element row : rowList.toArray(rowArr)){
                    row.detach();
                    this.cache.put(getId(row), row);
                }
            } catch (JDOMException | IOException ex) {
                throw new XflatException("Error building document cache", ex);
            }
        }
        
        this.state.set(EngineState.SpunUp);
        if(operationsReady.get()){
            this.state.set(EngineState.Running);
            synchronized(operationsReady){
                operationsReady.notifyAll();
            }
        }
        
        return true;
    }

    @Override
    protected boolean beginOperations() {
        if(this.state.compareAndSet(EngineState.SpunUp, EngineState.Running)){
            synchronized(operationsReady){
                operationsReady.set(true);
                operationsReady.notifyAll();
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Called before every write to ensure we are ready to write.
     * If the engine is spinning down then we throw because engines are read-only
     * when spinning down.
     */
    private void ensureReady(){
        EngineState state = this.state.get();
        if(state == EngineState.SpunDown ||
                state == EngineState.SpinningDown){
            throw new EngineStateException("Write operations not supported on an engine that is spinning down", state);
        }
        
        if(operationsReady.get()){
            return;
        }
        
        synchronized(operationsReady){
            while(!operationsReady.get()){
                try {
                    operationsReady.wait();
                } catch (InterruptedException ex) {
                    if(operationsReady.get()){
                        //oh ok we're all good to go
                        return;
                    }
                    throw new XflatException("Interrupted while waiting for engine to be ready");
                }
            }
        }
    }
    
    private WeakHashMap<Cursor<Element>, String> openCursors = new WeakHashMap<>();
    
    @Override
    protected boolean spinDown(final SpinDownEventHandler completionEventHandler) {
        //not much to do since everything's in the cache, just dump the cache
        //and set read-only mode.
        if(!this.state.compareAndSet(EngineState.Running, EngineState.SpinningDown)){
            //we're in the wrong state.
            return false;
        }
        
        final AtomicReference<ScheduledFuture<?>> cacheDumpTask = new AtomicReference<>(null);
        if(this.cache != null && lastModified.get() >= lastDump.get()){
            //schedule immediate dump
             cacheDumpTask.set(this.getExecutorService().schedule(
                new Runnable(){
                    @Override
                    public void run() {
                        try{
                            dumpCacheNow();
                        }
                        catch(Exception ex){
                            log.warn("Unable to dump cached data", ex);
                        }
                    }
                }, 0, TimeUnit.MILLISECONDS));
        }
        
        if(openCursors.isEmpty() && cacheDumpTask.get() == null || cacheDumpTask.get().isDone()){
            this.state.set(EngineState.SpunDown);
            if(completionEventHandler != null)
                completionEventHandler.spinDownComplete(new SpinDownEvent(CachedDocumentEngine.this));

            //we're ok to finish our spin down now
            return forceSpinDown();
            
        }

        Runnable spinDownTask = new Runnable(){
                @Override
                public void run() {
                    if(!openCursors.isEmpty())
                        return;

                    if(cacheDumpTask.get() != null && !cacheDumpTask.get().isDone()){
                        return;
                    }

                    if(!state.compareAndSet(EngineState.SpinningDown, EngineState.SpunDown)){
                        throw new RuntimeException("cancel task - in wrong state");
                    }

                    if(completionEventHandler != null)
                        completionEventHandler.spinDownComplete(new SpinDownEvent(CachedDocumentEngine.this));
                    //we're ok to finish our spin down now
                    forceSpinDown();

                    throw new RuntimeException("Scheduled Task Complete");

                }
            };
        this.getExecutorService().scheduleAtFixedRate(
            spinDownTask, 5, 10, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    protected boolean forceSpinDown() {
        //drop all remaining references to the cache, replace with a cache
        //that throws exceptions on access.
        this.cache = new InactiveCache();
        
        this.state.set(EngineState.SpunDown);
        
        return true;
    }
    
    private boolean isSpinningDown(){
        return this.state.get() == EngineState.SpunDown ||
                this.state.get() == EngineState.SpinningDown;
    }

    
    private String getId(Element row) {
        return row.getAttributeValue("id", XFlatDatabase.xFlatNs);
    }
    
    private void setId(Element row, String id){
        row.setAttribute("id", id, XFlatDatabase.xFlatNs);
    }
    
    private Element wrapInRow(Element data, String id){
        Element row = new Element("row", XFlatDatabase.xFlatNs).setContent(data);
        setId(row, id);
        
        return row;
    }
    
    
    private AtomicReference<Future<?>> scheduledDump = new AtomicReference<>(null);
    private AtomicLong lastDump = new AtomicLong(System.currentTimeMillis());
    private AtomicLong lastModified = new AtomicLong(System.currentTimeMillis());
    private AtomicInteger dumpFailures = new AtomicInteger();
    
    private void dumpCache(){
        long delay = 0;
        lastModified.set(System.currentTimeMillis());
        
        //did we dump inside the last 250 ms?
        if(lastDump.get() + 250 > System.currentTimeMillis())
        {
            //yes, dump at 250 ms
            delay = lastDump.get() + 250 - System.currentTimeMillis();
            if(delay < 0)
                delay = 0;
        }
        
        if(scheduledDump.get() != null || isSpinningDown()){
            //we're already scheduled to dump the cache
            return;
        }
        
        ScheduledFuture<?> dumpTask;
        synchronized(dumpSyncRoot){
            if(scheduledDump.get() != null || isSpinningDown()){
                return;
            }
            
            //dump the cache on a separate thread so we can remain responsive
             dumpTask = this.getExecutorService().schedule(
                new Runnable(){
                    @Override
                    public void run() {
                        try{
                            dumpCacheNow();
                        }
                        catch(XflatException ex){
                            log.warn("Unable to dump cached data", ex);
                        }
                    }
                }, delay, TimeUnit.MILLISECONDS);
            scheduledDump.set(dumpTask);
        }
        
        if(dumpFailures.get() > 10){
            //get this on the thread that is doing the writing, so someone notices
            while(!dumpTask.isDone()){
                try {
                    dumpTask.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new XflatException("An error occurred after attempting to write to disk " +
                            dumpFailures.get() + " times", ex);
                }
            }
        }
    }
    
    private final Object dumpSyncRoot = new Object();
    private void dumpCacheNow(){
        synchronized(dumpSyncRoot){
            if(lastModified.get() < lastDump.get()){
                //no need to dump
                return;
            }
            
            //take a 'snapshot' of the detached elements
            Document doc = new Document();
            Element root = new Element("table", XFlatDatabase.xFlatNs)
                    .setAttribute("name", this.getTableName(), XFlatDatabase.xFlatNs);
            doc.setRootElement(root);
            
            for(Element e : this.cache.values()){
                synchronized(e){
                    root.addContent(e.clone());
                }
            }
            
            try{
                this.file.writeFile(doc);
            }
            catch(IOException ex) {
                dumpFailures.incrementAndGet();
                throw new XflatException("Unable to dump cache to file", ex);
            }
            finally {
                scheduledDump.set(null);
                lastDump.set(System.currentTimeMillis());
            }

            //success!
            dumpFailures.set(0);
        }
    }
    
    private class TableCursor implements Cursor<Element>{

        private final Iterable<Element> toIterate;
        private final XpathQuery filter;
        
        public TableCursor(Iterable<Element> toIterate, XpathQuery filter){
            this.filter = filter;
            this.toIterate = toIterate;
        }
        
        @Override
        public Iterator<Element> iterator() {
            return new TableCursorIterator(toIterate.iterator(), filter.getRowMatcher());
        }

        @Override
        public void close() throws Exception {
            CachedDocumentEngine.this.openCursors.remove(this);
        }
        
    }
    
    private static class TableCursorIterator implements Iterator<Element>{
        private final Iterator<Element> toIterate;
        private final Matcher<Element> rowMatcher;
        
        private Element peek = null;
        private boolean isFinished = false;
        private int peekCount = 0;
        private int returnCount = 0;
        
        public TableCursorIterator(Iterator<Element> toIterate, Matcher<Element> rowMatcher){
            this.toIterate = toIterate;
            this.rowMatcher = rowMatcher;
        }
        
        private void peekNext(){
            while(toIterate.hasNext()){
                Element next = toIterate.next();
                synchronized(next){
                    if(rowMatcher.matches(next)){
                        peekCount++;
                        this.peek = next;
                        return;
                    }
                }
            }
            
            peekCount++;
            this.peek = null;
            isFinished = true;
        }

        @Override
        public boolean hasNext() {
            if(isFinished)
                return false;
            
            while(peekCount <= returnCount){
                peekNext();
            }
            
            return !isFinished;
        }

        @Override
        public Element next() {
            if(isFinished){
                throw new NoSuchElementException();
            }
            
            while(peekCount <= returnCount){
                //gotta peek
                peekNext();
            }
            
            //try again
            if(isFinished){
                throw new NoSuchElementException();
            }
            
            Element ret = peek;
            synchronized(ret){
                //lock the row
                ret = ret.getChildren().get(0).clone();
            }
            
            returnCount++;
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported on cursors.");
        }
    }
}
