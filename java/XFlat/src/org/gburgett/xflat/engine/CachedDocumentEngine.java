/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
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
import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.Database;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.hamcrest.Matcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

/**
 * This is an engine that caches the entire table in memory as a JDOM {@link Document}.
 * @author gordon
 */
public class CachedDocumentEngine extends EngineBase implements Engine {

    private final AtomicBoolean operationsReady = new AtomicBoolean(false);
    private final AtomicBoolean isSpinningDown = new AtomicBoolean(false);
    
    private final Object syncRoot = new Object();    
    
    private ConcurrentMap<String, Element> cache = null;
    
    private DocumentFileWrapper file;
    public DocumentFileWrapper getFile(){
        return file;
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
        
        dumpCache();
    }

    @Override
    public Element readRow(String id) {
        Element row = this.cache.get(id);
        if(row == null){
            return null;
        }
        
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
        
        if(numRemoved > 0)
            dumpCache();
        
        return numRemoved;
    }
    
    //</editor-fold>

    @Override
    protected void spinUp() {
        //concurrency level 4 - don't expect to need more than this.
        this.cache = new ConcurrentHashMap<>(16, 0.75f, 4);
        
        if(!file.exists()){
            //new file
            return;
        }
        
        
        try {
            Document doc = this.file.readFile();
            List<Element> rowList = doc.getRootElement().getChildren("row", Database.xFlatNs);
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

    @Override
    protected void beginOperations() {
        synchronized(operationsReady){
            operationsReady.set(true);
            operationsReady.notifyAll();
        }
    }

    /**
     * Called before every write to ensure we are ready to write.
     * If the engine is spinning down then we throw because engines are read-only
     * when spinning down.
     */
    private void ensureReady(){
        if(isSpinningDown.get()){
            throw new XflatException("Write operations not supported on an engine that is spinning down");
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
    private AtomicReference<Runnable> spinDownTask = new AtomicReference<>(null);
    
    @Override
    protected void spinDown(final SpinDownEventHandler completionEventHandler) {
        //not much to do since everything's in the cache, just dump the cache
        //and set read-only mode.
        isSpinningDown.set(true);
        
        if(this.cache != null)
            dumpCacheNow();
        //start a task to check the open cursors - when they are all closed,
        //we can fire the event
        
        if(spinDownTask.get() != null){
            return;
        }
        
        synchronized(syncRoot){
            if(spinDownTask.get() != null)
                return;
            
            if(openCursors.isEmpty()){
                completionEventHandler.spinDownComplete(new SpinDownEvent(CachedDocumentEngine.this));
                spinDownTask.set(null);
                //we're ok to finish our spin down now
                forceSpinDown();
                return;
            }
            
            final AtomicReference<ScheduledFuture<?>> spinDownFuture = new AtomicReference<>();
            this.spinDownTask.set(new Runnable(){
                    @Override
                    public void run() {
                        if(!openCursors.isEmpty())
                            return;

                        Future<?> thisTask = spinDownFuture.get();
                        if(thisTask == null || thisTask.isDone())
                            return;

                        thisTask.cancel(false);
                        spinDownFuture.set(null);

                        synchronized(syncRoot){
                            if(spinDownTask.get() == null)
                                return;
                            
                            if(!openCursors.isEmpty())
                                return;
                            
                            completionEventHandler.spinDownComplete(new SpinDownEvent(CachedDocumentEngine.this));
                            spinDownTask.set(null);
                            //we're ok to finish our spin down now
                            forceSpinDown();
                        }
                    }
                });
            spinDownFuture.set(
                this.getExecutorService().scheduleAtFixedRate(
                this.spinDownTask.get(), 5, 10, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    protected void forceSpinDown() {
        //drop all remaining references to the cache, replace with a cache
        //that throws exceptions on access.
        this.cache = new InactiveCache();
    }

    private AtomicReference<Date> lastModified = new AtomicReference<>(null);
    @Override
    protected Date getLastModified() {
        return lastModified.get();
    }

    private String getId(Element row) {
        return row.getAttributeValue("id", Database.xFlatNs);
    }
    
    private void setId(Element row, String id){
        row.setAttribute("id", id, Database.xFlatNs);
    }
    
    private Element wrapInRow(Element data, String id){
        Element row = new Element("row", Database.xFlatNs).setContent(data);
        setId(row, id);
        
        return row;
    }
    
    
    private AtomicReference<Future<?>> scheduledDump = new AtomicReference<>(null);
    private AtomicReference<Date> lastDump = new AtomicReference<>(null);
    private AtomicInteger dumpFailures = new AtomicInteger();
    
    private void dumpCache(){
        //we dump cache anytime we modify, so lastmodified = now
        lastModified.set(new Date(System.currentTimeMillis()));
        
        long delay = 0;
        
        //did we dump inside the last 100 ms?
        if(lastDump.get() != null &&
            lastDump.get().after(new Date(System.currentTimeMillis() - 100)))
        {
            //yes, dump at 100 ms
            delay = lastDump.get().getTime() + 100 - System.currentTimeMillis();
            if(delay < 0)
                delay = 0;
        }
        
        if(scheduledDump.get() != null || isSpinningDown.get()){
            //we're already scheduled to dump the cache
            return;
        }
        
        ScheduledFuture<?> dumpTask;
        synchronized(syncRoot){
            if(scheduledDump.get() != null || isSpinningDown.get()){
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
            //take a 'snapshot' of the detached elements
            Document doc = new Document();
            Element root = new Element("table", Database.xFlatNs)
                    .setAttribute("name", this.getTableName(), Database.xFlatNs);
            doc.setRootElement(root);

            root.addContent(this.cache.values());

            try{
                this.file.writeFile(doc);
            }
            catch(IOException ex) {
                dumpFailures.incrementAndGet();
                throw new XflatException("Unable to dump cache to file", ex);
            }
            finally {
                scheduledDump.set(null);
                lastDump.set(new Date(System.currentTimeMillis()));
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
            if(!isSpinningDown.get())
                return;
            
            //run the spin down monitoring task immediately, don't wait for the scheduler.
            Runnable t = spinDownTask.get();
            if(t != null)
                t.run();
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
