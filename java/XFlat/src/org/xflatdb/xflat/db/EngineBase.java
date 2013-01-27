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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xflatdb.xflat.EngineStateException;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.db.EngineBase.RowData;
import org.xflatdb.xflat.transaction.Transaction;
import org.xflatdb.xflat.transaction.TransactionException;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.jdom2.Element;

/**
 * The base class for Engine objects.  The Database uses the functionality
 * described here to manage engines, so all engine implementations must extend
 * this base class.
 * @author gordon
 */
public abstract class EngineBase implements Engine {
    
    final private String tableName;
    @Override
    public String getTableName(){
        return tableName;
    }
    
    protected Log log = LogFactory.getLog(getClass());
    
    protected EngineBase(String tableName){
        this.tableName = tableName;
    }
    
    //<editor-fold desc="transition functions">
    
    protected AtomicReference<EngineState> state = new AtomicReference<>(EngineState.Uninitialized);
    public EngineState getState(){
        return state.get();
    }
    
    /**
     * Initializes the engine and instructs it to begin acquiring the resources
     * necessary to function.  At this point the engine may begin to respond to
     * read-only requests, but any write requests MUST block until {@link #beginOperations() )
     * is called.
     */
    protected abstract boolean spinUp();
    
    /**
     * Instructs the engine to begin full read/write operations.  At this point this
     * engine has full control over the data files.
     */
    protected abstract boolean beginOperations();
    
    /**
     * Instructs this Engine to wrap up its operations in preparation for being
     * switched out.  The engine must be set to read-only mode (write operations
     * should throw an exception).  The engine can stay alive until its outstanding
     * cursors are closed, but must allow concurrent reads of the file.
     * @param completionEventHandler An event handler that is notified when
     * the Engine has finished spinning down.
     */
    protected abstract boolean spinDown(SpinDownEventHandler completionEventHandler);
    
    /**
     * Forces this engine to immediately release all resources, even if there are
     * still outstanding cursors or uncommitted data.  Cursors that continue iterating will throw an
     * exception that indicates the engine has spun down.  This will be called after a normal
     * spin down, or in case of some kind of error to reclaim resources.
     */
    protected abstract boolean forceSpinDown();
    
    private AtomicLong lastActivity = new AtomicLong();
    
    /**
     * Gets the date at which the last operation was performed on the engine.
     * This is used by the Database to determine whether this engine needs to
     * remain in its cache or if it can be spun down for later.
     * @return The date at which the last write or read has occurred, or the date
     * of engine creation if no operations have occurred.
     */
    public long getLastActivity(){
        return lastActivity.get();
    }
    
    /**
     * Concurrently updates the lastActivity property to the greater of the
     * existing value or the given time.
     * @param time the time to update lastActivity to, if greater than the existing
     * value.
     */
    protected void setLastActivity(long time){
        long existing;
        do{
            existing = lastActivity.get();
            if(existing >= time){
                //no need to update
                return;
            }
            //ensure we compared to the latest value before setting.
        }while(!lastActivity.compareAndSet(existing, time));
    }
    
    public static interface SpinDownEventHandler{
        /**
         * Called when the engine is completely finished spinning down,
         * and is ready to release all filesystem references.
         * @param event 
         */
        public void spinDownComplete(SpinDownEvent event);
    }
    
    public static class SpinDownEvent extends java.util.EventObject{
        @Override
        public Engine getSource(){
            return (Engine)super.getSource();
        }
        
        public SpinDownEvent(Engine source){
            super(source);
        }
    }

    //</editor-fold>
    
    //<editor-fold desc="dependencies">
    private ScheduledExecutorService executorService;
    protected ScheduledExecutorService getExecutorService(){
        return executorService;
    }
    
    protected void setExecutorService(ScheduledExecutorService service){
        this.executorService = service;
    }
    
    private ConversionService conversionService;
    protected ConversionService getConversionService() {
        return this.conversionService;
    }
    
    protected void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    private EngineTransactionManager transactionManager;
    /**
     * Gets the transactionManager.
     */
    protected EngineTransactionManager getTransactionManager(){
        return this.transactionManager;
    }
    /**
     * Sets the transactionManager.
     */
    protected void setTransactionManager(EngineTransactionManager transactionManager){
        this.transactionManager = transactionManager;
    }
    
    //</editor-fold>

    
    private final AtomicLong tableLock = new AtomicLong(-1);
    private int tableLockCount = 0;
    private final Object tableLockSync = new Object();
    
    private final AtomicInteger writesInProgress = new AtomicInteger(0);
    
    /**
     * Called before every write to ensure we are ready to write. <br/>
     * This method also checks if there is a current table lock, and increments
     * the {@link #writesInProgress} counter.
     * <p/>
     * If the engine is spinning down then we throw because engines are read-only
     * when spinning down.
     */
    protected Transaction ensureWriteReady(){
        //check if there is a write lock on the table
        long tblLock = tableLock.get();
        if(tblLock != -1 && tblLock != Thread.currentThread().getId()){
            synchronized(tableLockSync){
                tblLock = tableLock.get();
                while(tblLock != -1 && tblLock != Thread.currentThread().getId()){                    
                    try {
                        tableLockSync.wait();
                    } catch (InterruptedException ex) {                        
                    }
                    
                    tblLock = tableLock.get();
                }
            }
        }
        
        Transaction tx = this.transactionManager.getTransaction();
        if(tx != null && tx.getOptions().getReadOnly()){
            throw new TransactionException("Cannot write in a read-only transaction");
        }
        
        //check the engine state
        EngineState state = this.state.get();
        if(state == EngineState.SpunDown ||
                state == EngineState.SpinningDown){
            throw new EngineStateException("Write operations not supported on an engine that is spinning down", state);
        }
        
        //we're about to write, so the engine must be bound to the current transaction
        this.transactionManager.bindEngineToCurrentTransaction(this);
        
        //increment the number of writes in progress
        int inprog = this.writesInProgress.incrementAndGet();
        if(inprog < 1){
            //dunno how we got here, try to correct
            this.writesInProgress.compareAndSet(inprog, 1);
            if(log.isTraceEnabled())
                log.trace(String.format("Writes in progress was less than 1: %d", inprog));
        }
        
        return tx;
    }
    
    /**
     * Called inside a finally block within every write operation -
     * this is a synchronizing measure for write locks
     */
    protected void writeComplete(){
        //decrement the number of writes in progress
        int inprog = this.writesInProgress.decrementAndGet();
        if(inprog < 0){
            this.writesInProgress.compareAndSet(inprog, 0);
            if(log.isTraceEnabled())
                log.trace(String.format("Writes in progress was less than 1: %d", inprog));
        }
    }
    
    /**
     * Obtains a write lock on the table for this thread.
     * <p/>
     * New write operations will block until the lock is released with {@link #releaseTableLock() }.
     * This method will wait after obtaining the lock until all in-progress write operations
     * have terminated.
     * <p/>
     * Since I don't exactly trust this to never throw an exception, it would of
     * course be good practice to always use the following pattern:
     * <pre>
     * try{
     *      engine.getTableLock();
     * 
     *      //do stuff
     * }
     * finally{
     *      engine.releaseTableLock();
     * }
     * </pre>
     */
    protected void getTableLock(){
        long thread = Thread.currentThread().getId();
        
        if(this.tableLock.get() == thread){
            this.tableLockCount++;
            return;
        }
        
        synchronized(tableLockSync){
            while(!this.tableLock.compareAndSet(-1, thread)){
                if(this.tableLock.get() == thread){
                    this.tableLockCount++;
                    return;
                }
                
                try {
                    //wait until we can obtain the lock for this thread.
                    tableLockSync.wait();
                } catch (InterruptedException ex) {
                }
            }
            this.tableLockCount++;
            
            //spin wait on writes in progress - this should only decrement while we have a write lock            
            long start = System.currentTimeMillis();
            long nanos = System.nanoTime();
            while(this.writesInProgress.get() > 0){
                
                //if we've been waiting longer than 500ms something is amiss
                if(System.currentTimeMillis() - start > 500){
                    //release the lock before throwing
                    this.tableLock.compareAndSet(thread, -1);
                    this.tableLockCount--;
                    throw new XFlatException(String.format("Cannot obtain table lock - %d long running writes in progress", this.writesInProgress.get()));
                }
                
                //if we've been spin-waiting longer than 500ns then sleep the thread
                if(System.nanoTime() - nanos > 500){
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
    }
    
    /**
     * Releases a write lock on the table that was obtained by this thread.
     * If the current thread did not own the lock then this method does nothing.
     * <p/>
     * ALWAYS call this in a finally block after calling {@link #getTableLock() }
     */
    protected void releaseTableLock(){
        if(this.tableLock.get() != Thread.currentThread().getId()){
            return;
        }
        
        synchronized(tableLockSync){
            if(this.tableLock.get() != Thread.currentThread().getId()){
                return;
            }
            
            if(--this.tableLockCount == 0){
                //last reentrant release encountered
                if(this.tableLock.compareAndSet(Thread.currentThread().getId(), -1)){
                    //notify of lock released
                    tableLockSync.notifyAll();
                }
            }
        }
    }
    
    
    /**
     * Saves metadata to the given element.  Metadata is things like indexes
     * and other configuration.
     * @param metadataElement The element from which metadata should be loaded.
     */
    protected void saveMetadata(Element metadataElement){
        
    }
    /**
     * Loads metadata from the given element.  Metadata is things like indexes
     * and other configurations the engine may need to save.
     * @param metatdataElement The element to which metadata should be saved.
     */
    protected void loadMetadata(Element metatdataElement){
        
    }
    
    /**
     * gets the string ID from a row element.
     * @param row The row whose ID is needed
     * @return The ID attached to the row
     */
    protected String getId(Element row) {
        return row.getAttributeValue("id", XFlatDatabase.xFlatNs);
    }
    
    /**
     * set the Id of the given row element to the given ID
     * @param row The row whose Id to set
     * @param id The new value of the ID.
     */
    protected void setId(Element row, String id){
        row.setAttribute("id", id, XFlatDatabase.xFlatNs);
    }
    
    //<editor-fold desc="transactions">

    /**
     * Checks whether this engine has any transactional updates in an uncommitted 
     * or unreverted state.
     * If so, returns true.
     * @return true if this engine has uncommitted transactional data, false otherwise.
     */
    protected abstract boolean hasUncomittedData();
    
    /**
     * Called when a transaction is committed to write the committed data to disk.
     * After this method returns, the data should be stored in non-volatile storage.
     * @param tx 
     */
    public void commit(Transaction tx){
        
    }
    
    /**
     * Called when a transaction is committed to revert the given transaction ID.
     * This may be called even if a transaction was previously committed in this engine,
     * because it was not fully committed across all engines.
     * @param tx 
     * @param isRecovering true if this transaction is being reverted during recovery
     * at startup.
     */
    public void revert(long tx, boolean isRecovering){
        
    }
    
    //</editor-fold>
    
    /**
     * Represents one row in the database.  The row contains a set of
     * {@link RowData} which represents the committed and uncommitted data in
     * the row.  The row data is mapped by its transaction ID.
     * <p/>
     * The Row should always be locked before any reading or modification of
     * the data.
     */
    protected class Row{
        /**
         * The ID of this row.
         */
        public final String rowId;
        
        /**
         * A SortedMap of the committed and uncommitted data in the row.
         * Always lock the row before accessing this data.
         */
        public final SortedMap<Long, RowData> rowData = new TreeMap<>();
        
        public Row(String id){
            this.rowId = id;
        }
        
        public Row(String id, RowData data){
            this.rowId = id;
            this.rowData.put(data.transactionId, data);
        }
        
        /**
         * Chooses the most recent committed RowData that was committed before the given transaction.
         * If the transaction is null, this will choose the most recent committed
         * RowData globally.
         * <p/>
         * ALWAYS invoke this while synchronized on the Row.
         * @param currentTransaction The current transaction, or null.
         * @param transactionId The transaction ID to use if the current transaction is null
         * @return The most recent committed RowData in this row, committed before the transaction.
         */
        public RowData chooseMostRecentCommitted(Transaction currentTransaction, long transactionId){
            if(currentTransaction != null){
                //override the given transaction ID just in case
                transactionId = currentTransaction.getTransactionId();
            }
            
            RowData ret = null;
            long retCommitId = -1;

            Iterator<RowData> it = rowData.values().iterator();
            while(it.hasNext()){
                RowData data = it.next();
                
                //if we're in a transaction, see if this row is the version for this transaction.
                //if the transaction is reverted we don't want that, we want the most recent
                //committed version
                if(currentTransaction != null && !currentTransaction.isReverted()){
                    
                    if(data.transactionId > -1 && transactionId == data.transactionId){
                        //this row data is in the current transaction
                        return data;
                    }
                }

                if(data.commitId == -1){
                    //uncommitted row data - doublecheck with the transaction manager

                    data.commitId = transactionManager.isTransactionCommitted(data.transactionId);                    
                }

                if(data.commitId > -1){
                    //this row data has been committed
                    if(transactionId > data.commitId){
                        //the current transaction is null or began after the transaction was committed

                        if(retCommitId < data.commitId){
                            //the last valid version we saw was before this version.

                            ret = data;
                            retCommitId = data.commitId;
                        }
                    }
                }
                else{
                    //check if reverted
                    if(transactionManager.isTransactionReverted(data.transactionId)){
                        //remove it from the row
                        it.remove();
                    }
                }
            }

            return ret;
        }
        
        /**
         * Chooses the most recent committed RowData that was committed before the given transaction ID.
         * This prevents dirty reads in a non-transactional context by having a synchronizing transaction ID
         * which can be obtained from {@link TransactionManager#transactionlessCommitId() }
         * <p/>
         * ALWAYS invoke this while synchronized on the Row.
         * @param snapshotId The Transaction ID representing the time at which a snapshot of the data should be obtained.
         * @return The most recent committed RowData in this row, committed before the given snapshot.
         */
        public RowData chooseMostRecentCommitted(Long snapshotId){
            return chooseMostRecentCommitted(null, snapshotId);
        }

        /**
         * Cleans up the transactional data in this row.
         * Returns true if this row can then be removed because it contains no data.
         * @param (optional) A set of transaction IDs that is added to when it is discovered
         * that a transaction has been newly committed (and the associated RowData's commit ID
         * is updated).
         * @return true if this row has no RowData or its only RowData is "nothing".
         */
        public boolean cleanup(){
            
            RowData mostRecent = null;
            long lowest = transactionManager.getLowestOpenTransaction();
            
            Set<RowData> toRemove = null;
                    
            Iterator<RowData> it = rowData.values().iterator();
            while(it.hasNext()){
                RowData data = it.next();
                
                if(data.commitId == -1){
                    data.commitId = transactionManager.isTransactionCommitted(data.transactionId);
                    if(data.commitId == -1){
                        //the data is uncommitted
                        
                        if(transactionManager.isTransactionReverted(data.transactionId)){
                            //don't need this anymore
                            it.remove();
                        }
                        continue;
                    }
                }
                
                //the data is committed
                
                if(mostRecent == null){
                    mostRecent = data;
                }
                else{
                    if(data.commitId <= mostRecent.commitId){
                        //the most recent data is newer
                        if(mostRecent.commitId < lowest){
                            //there is no open transaction that would see this data instead of mostRecent
                            it.remove();
                        }
                    }
                    else{
                        //the data is newer
                        if(data.commitId < lowest){
                            //there is no open transaction that would see mostRecent instead of this data
                            if(toRemove == null){
                                toRemove = new HashSet<>();
                            }
                            toRemove.add(mostRecent);
                            mostRecent = data;
                        }
                    }
                }
            }
            
            //remove the ones we couldn't remove during the iteration
            if(toRemove != null && toRemove.size() > 0){
                for(RowData data : toRemove){
                    rowData.remove(data.commitId);                    
                }
            }
            
            //if there's no more row datas, or there is only one row data and it's value is "nothing", then return true.
            return rowData.isEmpty() || (rowData.size() == 1 && rowData.values().iterator().next().data == null);
        }
    }
    
    protected class RowData{
        /**
         * A snapshot of the data in the row, possibly uncommitted.
         */
        public Element data = null;
        
        /**
         * A "db:row" element that wraps the data.  This is useful for queries.
         */
        public Element rowElement = null;
        
        /**
         * The ID of the transaction that created this data snapshot
         */
        public long transactionId = -1;
        
        /**
         * The ID of the transaction commit that caused this row data to become
         * committed.  If the data is uncommitted, this is -1.
         */
        public long commitId = -1;
     
        public RowData(long txId){
            this.transactionId = txId;
        }
        
        public RowData(long txId, Element data, String id){
            if(data != null){            
                this.data = data;
                this.rowElement = new Element("row", XFlatDatabase.xFlatNs)
                        .setAttribute("id", id, XFlatDatabase.xFlatNs)
                        .setContent(data);
            }
            this.transactionId = txId;
        }
        
    }
    
    
}
