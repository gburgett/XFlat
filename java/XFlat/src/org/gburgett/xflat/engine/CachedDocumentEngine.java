/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineState;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.gburgett.xflat.transaction.Transaction;
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

    //TODO: can we replace this by taking a table lock on spin-up?
    private final AtomicBoolean operationsReady = new AtomicBoolean(false);
    
    private ConcurrentMap<String, Row> cache = null;
    
    private ConcurrentMap<String, Row> uncommittedRows = null;
    
    private final Object syncRoot = new Object();
    
    private DocumentFileWrapper file;
    public DocumentFileWrapper getFile(){
        return file;
    }
    
    public CachedDocumentEngine(File file, String tableName){
        super(tableName);
        this.file = new DocumentFileWrapper(file);
    }
    
    public CachedDocumentEngine(DocumentFileWrapper file, String tableName){
        super(tableName);
        this.file = file;
    }
    
    private long getTxId(Transaction tx){
        return tx != null ? 
                tx.getTransactionId() :
                //transactionless insert, get a new ID
                this.getTransactionManager().transactionlessCommitId();
    }
    
    //<editor-fold desc="interface methods">
    @Override
    public void insertRow(String id, Element data) throws DuplicateKeyException {
        ensureWriteReady();
        try{
            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            RowData rData = new RowData(txId, data, id);
            if(tx == null){
                //transactionless means auto-commit
                rData.commitId = txId;
            }

            Row row = new Row(id, rData);
            row = this.cache.putIfAbsent(id, row);
            if(row != null){
                synchronized(row){
                    //see if all the data was from after this transaction
                    RowData chosen = row.chooseMostRecentCommitted(tx, txId);
                    if(chosen == null || chosen.data == null){
                        //we're good to insert our transactional data
                        row.rowData.put(txId, rData);
                        
                        if(tx != null || this.getTransactionManager().anyOpenTransactions())
                            this.uncommittedRows.put(id, row);
                    }
                    else{
                        throw new DuplicateKeyException(id);
                    }
                }
            }

            setLastActivity(System.currentTimeMillis());
            dumpCache();
        }finally{
            writeComplete();
        }
    }

    @Override
    public Element readRow(String id) {
        Row row = this.cache.get(id);
        if(row == null){
            return null;
        }
        
        setLastActivity(System.currentTimeMillis());
        
        //lock the row
        synchronized(row){
            Transaction tx = this.getTransactionManager().getTransaction();
            //we want either the most recent for this transaction or, if null, the most recent globally.
            RowData ret = row.chooseMostRecentCommitted(tx, Long.MAX_VALUE);
            
            if(ret == null || ret.data == null){
                return null;
            }
            
            //clone the data
            return ret.data.clone();
        }
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        query.setConversionService(this.getConversionService());
        
        TableCursor ret = new TableCursor(this.cache.values(), query, getTransactionManager().getTransaction());
        
        this.openCursors.put(ret, "");
        setLastActivity(System.currentTimeMillis());
        
        return ret;
    }

    @Override
    public void replaceRow(String id, Element data) throws KeyNotFoundException {
        ensureWriteReady();
        try{
            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            Row row = this.cache.get(id);
            if(row == null){
                throw new KeyNotFoundException(id);
            }

            synchronized(row){
                RowData toReplace = row.chooseMostRecentCommitted(tx, txId);
                if(toReplace == null || toReplace.data == null){
                    throw new KeyNotFoundException(id);
                }

                RowData newData = new RowData(txId, data, id);
                if(tx == null){
                    //transactionless means auto-commit
                    newData.commitId = txId;
                }
                row.rowData.put(txId, newData);
                if(tx != null || this.getTransactionManager().anyOpenTransactions())
                    this.uncommittedRows.put(id, row);
            }

            setLastActivity(System.currentTimeMillis());
            dumpCache();
        
        }finally{
            writeComplete();
        }
    }

    @Override
    public boolean update(String id, XpathUpdate update) throws KeyNotFoundException {
        ensureWriteReady();
        try{
            Row row = this.cache.get(id);
            if(row == null){
                throw new KeyNotFoundException(id);
            }

            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            update.setConversionService(this.getConversionService());

            boolean ret;
            try {
                //lock the row
                synchronized(row){
                    RowData data = row.chooseMostRecentCommitted(tx, txId);
                    if(data == null || data.data == null){
                        throw new KeyNotFoundException(id);
                    }
                    else{
                        //apply to a copy, store the copy as a transactional state.
                        RowData newData = new RowData(txId, data.data.clone(), row.rowId);
                        if(tx == null){
                            //transactionless means auto-commit
                            newData.commitId = txId;
                        }

                        int updates = update.apply(newData.rowElement);
                        ret = updates > 0;
                        if(ret){
                            //no need to put a new version if no data was modified
                            row.rowData.put(txId, newData);
                            if(tx != null || this.getTransactionManager().anyOpenTransactions())
                                this.uncommittedRows.put(id, row);
                        }
                    }
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
        }finally{
            writeComplete();
        }
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        ensureWriteReady();
        try{
            
            query.setConversionService(this.getConversionService());
            update.setConversionService(this.getConversionService());

            Matcher<Element> rowMatcher = query.getRowMatcher();

            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);


            int rowsUpdated = 0;

            for(Row row : this.cache.values()){
                synchronized(row){
                    RowData rData = row.chooseMostRecentCommitted(tx, txId);
                    if(rData == null || rData.data == null){
                        continue;
                    }

                    if(!rowMatcher.matches(rData.rowElement))
                        continue;

                    try {
                        //apply to a copy, store the copy as a transactional state.
                        RowData newData = new RowData(txId, rData.data.clone(), row.rowId);
                        if(tx == null){
                            //transactionless means auto-commit
                            newData.commitId = txId;
                        }

                        int updates = update.apply(newData.rowElement);

                        if(updates > 0){
                            //no need to put a new version if no data was modified
                            row.rowData.put(txId, newData);
                            if(newData.commitId == -1 && (tx != null || this.getTransactionManager().anyOpenTransactions()))
                                this.uncommittedRows.put(row.rowId, row);
                        }

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
        }finally{
            writeComplete();
        }
    }

    @Override
    public boolean upsertRow(String id, Element data) {
        ensureWriteReady();
        try{
            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            RowData newData = new RowData(txId, data, id);
            if(tx == null){
                //transactionless means auto-commit
                newData.commitId = txId;
            }

            Row newRow = new Row(id, newData);

            boolean didInsert = false;
            synchronized(newRow){
                Row existingRow = this.cache.putIfAbsent(id, newRow); //takes care of the insert
                if(existingRow != null){
                    synchronized(existingRow){
                        //we inserted if the most recent committed was null or had null data
                        RowData mostRecent = existingRow.chooseMostRecentCommitted(tx, txId);
                        didInsert = mostRecent == null || mostRecent.data == null;
                        
                        //takes care of the "or update"
                        existingRow.rowData.put(txId, newData);
                        if(tx != null || this.getTransactionManager().anyOpenTransactions())
                            this.uncommittedRows.put(id, existingRow);
                    }
                }
                else{
                    didInsert = true;
                    if(tx != null || this.getTransactionManager().anyOpenTransactions())
                        this.uncommittedRows.put(id, newRow);
                }
            }

            setLastActivity(System.currentTimeMillis());
            dumpCache();

            return didInsert; //if none existed, then we inserted

        }finally{
            writeComplete();
        }
    }

    @Override
    public void deleteRow(String id) throws KeyNotFoundException {
        ensureWriteReady();
        try{

            Row row = this.cache.get(id);

            if(row == null){
                throw new KeyNotFoundException(id);
            }

            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            RowData newData = new RowData(txId, null, id);
            if(tx == null){
                newData.commitId = txId;
            }


            synchronized(row){
                RowData rData = row.chooseMostRecentCommitted(tx, txId);
                if(rData == null || rData.data == null){
                    throw new KeyNotFoundException(id);
                }

                //a RowData that is null means it was deleted.
                row.rowData.put(txId, newData);
                if(tx != null || this.getTransactionManager().anyOpenTransactions())
                    this.uncommittedRows.put(row.rowId, row);
            }

            setLastActivity(System.currentTimeMillis());
            dumpCache();
        }finally{
            writeComplete();
        }
    }

    @Override
    public int deleteAll(XpathQuery query) {
        ensureWriteReady();
        try{

            query.setConversionService(this.getConversionService());

            Transaction tx = this.getTransactionManager().getTransaction();
            long txId = getTxId(tx);

            Matcher<Element> rowMatcher = query.getRowMatcher();
            Iterator<Map.Entry<String, Row>> it = this.cache.entrySet().iterator();

            int numRemoved = 0;

            while(it.hasNext()){
                Map.Entry<String, Row> entry = it.next();

                Row row = entry.getValue();
                synchronized(row){
                    RowData rData = row.chooseMostRecentCommitted(tx, txId);
                    if(rData == null || rData.data == null){
                        continue;
                    }

                    if(rowMatcher.matches(rData.rowElement)){
                        RowData newData = new RowData(txId, null, row.rowId);
                        if(tx == null){
                            newData.commitId = txId;
                        }
                        row.rowData.put(txId, newData);
                        if(tx != null || this.getTransactionManager().anyOpenTransactions())
                            this.uncommittedRows.put(row.rowId, row);

                        numRemoved++;
                    }
                }
            }

            setLastActivity(System.currentTimeMillis());

            if(numRemoved > 0)
                dumpCache();

            return numRemoved;
        }finally{
            writeComplete();
        }
    }
    
    //</editor-fold>
    
    
    
    private void update(){
        synchronized(syncRoot){
            if(this.currentlyCommitting.get() != -1){
                if(this.getTransactionManager().isTransactionCommitted(this.currentlyCommitting.get()) == -1 &&
                        !this.getTransactionManager().isTransactionReverted(this.currentlyCommitting.get())){
                    //the transaction is neither committed nor reverted, it is in the process of committing.
                    //We'll have to come back to this update later when it is finished.
                    return;
                }
            }
            
            Set<Row> rowsToRemove = new HashSet<>();
            Set<Long> remainingTransactions = new HashSet<>();

            Iterator<Row> it = this.uncommittedRows.values().iterator();
            while(it.hasNext()){
                Row row = it.next();
                synchronized(row){
                    it.remove();

                    if(row.cleanup()){
                        rowsToRemove.add(row);
                    }
                    else{
                        //remember the remaining transactions
                        for(RowData data : row.rowData.values()){
                            if(data.commitId == -1){
                                remainingTransactions.add(data.transactionId);
                            }
                        }
                    }
                }
            }

            if(rowsToRemove.size() > 0){
                //we have to lock the table in order to actually remove any rows.
                try{
                    this.getTableLock();

                    for(Row row : rowsToRemove){
                        //doublecheck - do another cleanup, don't want to be sloppy here.
                        if(row.cleanup()){
                            this.cache.remove(row.rowId);
                        }
                        else{
                            //remember the remaining transactions
                            for(RowData data : row.rowData.values()){
                                if(data.commitId == -1){
                                    remainingTransactions.add(data.transactionId);
                                }
                            }
                        }
                    }
                }
                finally{
                    this.releaseTableLock();
                }
            }

            //unbind the engine from all transactions except the remaining transactions
            this.getTransactionManager().unbindEngineExceptFrom(this, remainingTransactions);
        }
    }

    private AtomicLong currentlyCommitting = new AtomicLong(-1);
    
    @Override
    public void commit(Transaction tx){
        synchronized(syncRoot){
            if(!currentlyCommitting.compareAndSet(-1, tx.getTransactionId())){
                //see if this transaction is completely finished committing, or if it reverted
                if(this.getTransactionManager().isTransactionCommitted(tx.getTransactionId()) == -1){
                    throw new IllegalStateException("Cannot commit two transactions simultaneously");
                }
                else{
                    //the transaction successfully committed, we can move on.
                    currentlyCommitting.set(-1);
                }
            }
            
            Iterator<Row> it = this.uncommittedRows.values().iterator();
            while(it.hasNext()){
                Row row = it.next();
                
                this.log.info("committing row " + row.rowId);
                synchronized(row){
                    //don't remove the row, only do that in cleanup.  
                    //We don't want to cleanup cause we still might need the old data,
                    //just set the transaction status to committed.
                    
                    RowData got = row.rowData.get(tx.getTransactionId());
                    if(got != null){
                        got.commitId = tx.getCommitId();
                    }
                }
            }
            
            //we must immediately dump the cache, we cannot say we are committed
            //until the data is on disk.
            dumpCacheNow();
        }
    }
    
    @Override
    public void revert(long txId, boolean isRecovering){
        synchronized(syncRoot){
            boolean mustDump = false;

            Iterator<Row> it = this.uncommittedRows.values().iterator();
            while(it.hasNext()){
                Row row = it.next();
                synchronized(row){
                    //remove the row data, since it's now uncommitted.

                    RowData got = row.rowData.remove(txId);
                    if(got.commitId != -1){
                        //this transaction was persisted to the DB.  We're going to need
                        //to dump the cache at the end.
                        mustDump = true;
                    }
                }
            }

            if(mustDump){
                this.dumpCacheNow();
            }
            //else we can leave dumping the cache for the cleanup task.

            //reset the currently committing if that was set
            currentlyCommitting.compareAndSet(txId, -1);
        }
    }
    
    
    @Override
    protected boolean spinUp() {
        if(!this.state.compareAndSet(EngineState.Uninitialized, EngineState.SpinningUp)){
            return false;
        }
        
        //concurrency level 4 - don't expect to need more than this.
        this.cache = new ConcurrentHashMap<>(16, 0.75f, 4);
        this.uncommittedRows = new ConcurrentHashMap<>(16, 0.75f, 4);
        
        if(file.exists()){
            try {
                Document doc = this.file.readFile();
                List<Element> rowList = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
                
                for(int i = rowList.size() - 1; i >= 0; i--){
                    Element row = rowList.get(i);
                    
                    if(row.getChildren().isEmpty()){
                        continue;
                    }
                    Element data = row.getChildren().get(0).detach();
                    
                    String id = getId(row);
                    //default it to zero so that we know it's committed but if we don't get an actual
                    //value for the commit then we have the lowest value.
                    long txId = 0;
                    long commitId = 0;
                    
                    String a = row.getAttributeValue("tx", XFlatDatabase.xFlatNs);
                    if(a != null && !"".equals(a)){
                        try{
                            txId = Long.parseLong(a);
                        }catch(NumberFormatException ex){
                            //just leave it as 0.
                        }
                    }
                    a = row.getAttributeValue("commit", XFlatDatabase.xFlatNs);
                    if(a != null && !"".equals(a)){
                        try{
                            commitId = Long.parseLong(a);
                        }catch(NumberFormatException ex){
                            //just leave it as 0.
                        }
                    }
                    
                    RowData rData = new RowData(txId, data, id);
                    rData.commitId = commitId;
                    
                    Row newRow = new Row(id, rData);
                    
                    this.cache.put(id, newRow);
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
        
        //schedule the update task
        this.getExecutorService().scheduleWithFixedDelay(new Runnable(){
                @Override
                public void run() {
                    if(state.get() == EngineState.SpinningDown || state.get() == EngineState.SpunDown){
                        throw new RuntimeException("task termination");
                    }
                    
                    update();
                }
            }, 500, 500, TimeUnit.MILLISECONDS);
        
        return true;
    }

    @Override
    protected boolean beginOperations() {
        //could happen before spin up complete, in that case spinUp will handle the notifying
        operationsReady.set(true);
        
        if(this.state.compareAndSet(EngineState.SpunUp, EngineState.Running)){
            synchronized(operationsReady){
                operationsReady.notifyAll();
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Overrides ensureWriteReady to additionally check if the 
     * engine has fully finished spinning up
     */
    @Override
    protected void ensureWriteReady(){
        super.ensureWriteReady();
        
        //check if we're not yet running, if so wait until we are running
        if(!operationsReady.get() || state.get() != EngineState.Running){         
            synchronized(operationsReady){
                while(!operationsReady.get() && this.state.get() != EngineState.Running){
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
    }
    
    
    private ConcurrentMap<Cursor<Element>, String> openCursors = new ConcurrentHashMap<>();
    
    @Override
    protected boolean spinDown(final SpinDownEventHandler completionEventHandler) {
        
        try{
            this.getTableLock();
        
            //not much to do since everything's in the cache, just dump the cache
            //and set read-only mode.
            if(!this.state.compareAndSet(EngineState.Running, EngineState.SpinningDown)){
                //we're in the wrong state.
                return false;
            }

            if(log.isTraceEnabled())
                log.trace("Spinning down");


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

            if(openCursors.isEmpty() && (cacheDumpTask.get() == null || cacheDumpTask.get().isDone())){
                this.state.set(EngineState.SpunDown);

                if(log.isTraceEnabled())
                    log.trace("Spin down complete (immediate)");

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

                        if(log.isTraceEnabled())
                            log.trace(String.format("Spin down complete (task)"));

                        if(completionEventHandler != null)
                            completionEventHandler.spinDownComplete(new SpinDownEvent(CachedDocumentEngine.this));
                        //we're ok to finish our spin down now
                        forceSpinDown();

                        throw new RuntimeException("Scheduled Task Complete");

                    }
                };
            this.getExecutorService().scheduleWithFixedDelay(
                spinDownTask, 5, 10, TimeUnit.MILLISECONDS);

            return true;
        }
        finally{
            this.releaseTableLock();
        }
    }

    @Override
    public boolean forceSpinDown() {
        //drop all remaining references to the cache, replace with a cache
        //that throws exceptions on access.
        this.cache = new InactiveCache<>();
        
        this.state.set(EngineState.SpunDown);
        
        return true;
    }
    
    private boolean isSpinningDown(){
        return this.state.get() == EngineState.SpunDown ||
                this.state.get() == EngineState.SpinningDown;
    }

    private AtomicReference<Future<?>> scheduledDump = new AtomicReference<>(null);
    private AtomicLong lastDump = new AtomicLong(0);
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
            
            long lastDump = System.currentTimeMillis();
            
            //take a 'snapshot' of the detached elements
            Document doc = new Document();
            Element root = new Element("table", XFlatDatabase.xFlatNs)
                    .setAttribute("name", this.getTableName(), XFlatDatabase.xFlatNs);
            doc.setRootElement(root);
            
            //get a transaction ID so we are taking a snapshot of the committed data at this point in time.
            long snapshotId = getTransactionManager().transactionlessCommitId();
            
            for(Row row : this.cache.values()){
                synchronized(row){
                    RowData rData = row.chooseMostRecentCommitted(snapshotId);
                    if(rData == null || rData.data == null){
                        //the data was deleted
                        continue;
                    }                    
                    
                    
                    Element rowEl = new Element("row", XFlatDatabase.xFlatNs);
                    setId(rowEl, row.rowId);
                    rowEl.setAttribute("tx", Long.toString(rData.transactionId), XFlatDatabase.xFlatNs);
                    rowEl.setAttribute("commit", Long.toString(rData.commitId), XFlatDatabase.xFlatNs);
                    
                    rowEl.addContent(rData.data.clone());
                    
                    root.addContent(rowEl);
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
                this.lastDump.set(lastDump);
            }

            //success!
            dumpFailures.set(0);
        }
    }

    @Override
    protected boolean hasUncomittedData() {
        return this.uncommittedRows == null ? false : !this.uncommittedRows.isEmpty();
    }

    
    private class TableCursor implements Cursor<Element>{

        private final Iterable<Row> toIterate;
        private final XpathQuery filter;
        
        private final Transaction tx;
        private final long txId;
        
        public TableCursor(Iterable<Row> toIterate, XpathQuery filter, Transaction tx){
            this.filter = filter;
            this.toIterate = toIterate;
            this.tx = tx;
            this.txId = getTxId(tx);
        }
        
        @Override
        public Iterator<Element> iterator() {
            return new TableCursorIterator(toIterate.iterator(), filter.getRowMatcher(), tx, txId);
        }

        @Override
        public void close() {
            CachedDocumentEngine.this.openCursors.remove(this);
        }
        
    }
    
    private static class TableCursorIterator implements Iterator<Element>{
        private final Iterator<Row> toIterate;
        private final Matcher<Element> rowMatcher;
        
        private final Transaction tx;
        private final long txId;
        
        private Element peek = null;
        private boolean isFinished = false;
        private int peekCount = 0;
        private int returnCount = 0;
        
        public TableCursorIterator(Iterator<Row> toIterate, Matcher<Element> rowMatcher, Transaction tx, long txId){
            this.toIterate = toIterate;
            this.rowMatcher = rowMatcher;
            this.tx = tx;
            this.txId = txId;
        }
        
        private void peekNext(){
            while(toIterate.hasNext()){
                Row next = toIterate.next();
                synchronized(next){
                    RowData rData = next.chooseMostRecentCommitted(tx, txId);
                    if(rData == null || rData.data == null){
                        continue;
                    }
                    
                    if(rowMatcher.matches(rData.rowElement)){
                        //found a matching row
                        peekCount++;
                        this.peek = rData.data.clone();
                        return;
                    }
                }
            }
            
            //no matching row
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
                        
            returnCount++;
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported on cursors.");
        }
    }
}
