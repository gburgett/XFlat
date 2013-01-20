/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.transaction.Transaction;
import org.gburgett.xflat.transaction.TransactionManager;
import org.jdom2.Attribute;
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
     * still outstanding cursors.  Cursors that continue iterating will throw an
     * exception that indicates the engine has spun down.
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
    
    private TransactionManager transactionManager;
    /**
     * Gets the transactionManager.
     */
    public TransactionManager getTransactionManager(){
        return this.transactionManager;
    }
    /**
     * Sets the transactionManager.
     */
    public void setTransactionManager(TransactionManager transactionManager){
        this.transactionManager = transactionManager;
    }
    
    //</editor-fold>

    
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
    
       
       
    /**
     * Checks whether this engine has any transactional updates in an uncommitted 
     * or unreverted state.
     * If so, returns true.
     * @return true if this engine has uncommitted transactional data, false otherwise.
     */
    protected abstract boolean hasUncomittedData();
    
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
         * @return The most recent committed RowData in this row, committed before the transaction.
         */
        public RowData chooseMostRecentCommitted(Transaction currentTransaction){
            if(currentTransaction == null){
                return chooseMostRecentCommitted(null, Long.MAX_VALUE);
            }
            
            return chooseMostRecentCommitted(currentTransaction, currentTransaction.getTransactionId());
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
        
        private RowData chooseMostRecentCommitted(Transaction currentTransaction, long currentTxId){
        
            RowData ret = null;
            long retCommitId = -1;

            Iterator<RowData> it = rowData.values().iterator();
            while(it.hasNext()){
                RowData data = it.next();
                
                //if we're in a transaction, see if this row is the version for this transaction.
                //if the transaction is reverted we don't want that, we want the most recent
                //committed version
                if(currentTransaction != null && !currentTransaction.isReverted()){
                    
                    if(data.transactionId > -1 && currentTxId == data.transactionId){
                        //this row data is the data in the current transaction
                        return data;
                    }
                }

                if(data.commitId == -1){
                    //uncommitted row data - doublecheck with the transaction manager

                    data.commitId = transactionManager.isTransactionCommitted(data.transactionId);                    
                }

                if(data.commitId > -1){
                    //this row data has been committed
                    if(currentTxId > data.commitId){
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

        
    }
    
    protected class RowData{
        /**
         * A snapshot of the data in the row, possibly uncommitted.
         */
        public Element data = null;
        
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
        
        public RowData(long txId, Element data){
            this.data = data;
            this.transactionId = txId;
        }
    }
    
    
}
