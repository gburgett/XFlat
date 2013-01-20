/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jdom2.Element;

/**
 *
 * @author Gordon
 */
public class ThreadContextTransactionManager implements TransactionManager {

    AtomicLong lastId = new AtomicLong();
    
    /**
     * Generates a new Transaction ID.  The ID is composed of the lower
     * 48 bits of {@link System#currentTimeMillis() } plus a 16-bit uniquifier.
     * unfortunately this means we have a y10k problem :P  I'll let my descendants
     * deal with it.
     * @return A new ID for a transaction.
     */
    protected long generateNewId(){
        long id;
        long last;
        do{
            //bitshifting current time millis still gets us at least to the year 10,000 before it overflows.
            id = System.currentTimeMillis() << 16;
            last = lastId.get();
            if((last & 0xFFFFFFFFFFFF0000l) == (id & 0xFFFFFFFFFFFF0000l)){
                //the last ID was at the same millisecond as our new ID, need to use uniquifier.
                int u = (int)(last & 0xFFFFl) + 1;
                if(u > 0xFFFF){
                    try {
                        //we can't roll over, need to slow down rate of transaction generation.
                         Thread.sleep(1);
                    } catch (InterruptedException ex) {
                        //don't care
                    }
                    //try again, hopefully currentTimeMillis rolled over.
                    continue;
                }
                
                id = id | u;
            }
        }while(!lastId.compareAndSet(last, id));
        
        return id;
    }
    
    private Map<Thread, ThreadedTransaction> currentTransactions = new ConcurrentHashMap<>();
    
    private Map<Long, ThreadedTransaction> committedTransactions = new ConcurrentHashMap<>();
    
    
    @Override
    public Transaction getTransaction() {
        return currentTransactions.get(Thread.currentThread());
    }

    @Override
    public Transaction openTransaction() {
        return openTransaction(TransactionOptions.Default);
    }

    @Override
    public Transaction openTransaction(TransactionOptions options) {
        if(currentTransactions.get(Thread.currentThread()) != null){
            throw new IllegalStateException("Transaction already open on current thread.");
        }
        
        ThreadedTransaction ret = new ThreadedTransaction(generateNewId(), options);
        if(currentTransactions.put(Thread.currentThread(), ret) != null){
            //how could this happen? I dunno, programs surprise me all the time.
            throw new IllegalStateException("Transaction already open on current thread");
        }
        
        return ret;
    }

    @Override
    public long isTransactionCommitted(long transactionId) {
        ThreadedTransaction tx = committedTransactions.get(transactionId);
        return tx == null ? -1 : tx.getCommitId();
    }

    @Override
    public boolean isTransactionReverted(long transactionId) {
        //if we find it in the current transactions, check the transaction
        for(Transaction tx : currentTransactions.values()){
            if(tx.getTransactionId() == transactionId){
                return tx.isReverted();
            }
        }
        
        //otherwise it might be in the committed transactions, if so it is not reverted.
        if(committedTransactions.get(transactionId) != null){
            return false;
        }
        
        //if we lost it then it's reverted.
        return true;
    }

    @Override
    public long transactionlessCommitId() {
        return generateNewId();
    }
    
        
    
    protected class ThreadedTransaction implements Transaction{

        private TransactionOptions options;
        
        private AtomicBoolean isCompleted = new AtomicBoolean(false);
        private AtomicBoolean isRollbackOnly = new AtomicBoolean(false);
        
        private final long id;
        
        private long commitId = -1;
        @Override
        public long getCommitId(){
            return commitId;
        }
        
        protected ThreadedTransaction(long id, TransactionOptions options){
            this.options = options;
            if(this.options.getReadOnly()){
                this.isRollbackOnly.set(true);
            }
            this.id = id;
        }
        
        @Override
        public void commit() throws TransactionException {
            if(this.isRollbackOnly.get()){
                throw new IllegalTransactionStateException("Cannot commit a rollback-only transaction");
            }
            if(!this.isCompleted.compareAndSet(false, true)){
                throw new IllegalTransactionStateException("Cannot commit a completed transaction");
            }
            
            commitId = generateNewId();
            committedTransactions.put(id, this);
        }

        @Override
        public void rollback() {
            if(this.isCompleted.get()){
                throw new IllegalTransactionStateException("Cannot rollback a completed transaction");
            }
            
            doRollback();
        }
        
        private void doRollback(){
            //do nothing
        }

        @Override
        public void setRollbackOnly() {
            this.isRollbackOnly.set(true);
        }

        @Override
        public long getTransactionId() {
            return this.id;
        }

        @Override
        public void close() {
            if(isCompleted.compareAndSet(false, true)){
                doRollback();
            }
            
            //remove the transaction from the current transactions map
            Iterator<ThreadedTransaction> it = currentTransactions.values().iterator();
            while(it.hasNext()){
                if(it.next() == this){
                    it.remove();
                }
            }
        }

        @Override
        public boolean isCommitted() {
            return commitId > -1;
        }

        @Override
        public boolean isReverted() {
            return isCompleted.get() && commitId > -1;
        }
    }
    
}
