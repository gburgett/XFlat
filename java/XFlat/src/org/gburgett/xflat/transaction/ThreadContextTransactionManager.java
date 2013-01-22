/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineTransactionManager;

/**
 *
 * @author Gordon
 */
public class ThreadContextTransactionManager extends EngineTransactionManager {

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

    @Override
    public long getLowestOpenTransaction() {
        long lowest = Long.MAX_VALUE;
        for(Transaction tx : currentTransactions.values()){
            if(tx.getTransactionId() < lowest){
                lowest = tx.getTransactionId();
            }
        }
        
        return lowest;
    }

    @Override
    public void bindEngineToCurrentTransaction(EngineBase engine) {
        
        ThreadedTransaction tx = currentTransactions.get(Thread.currentThread());
        if(tx == null){
            return;
        }
                
        tx.boundEngines.add(engine);
    }

    @Override
    public void unbindEngineFromTransaction(EngineBase engine, Long transactionId) {
        ThreadedTransaction tx = null;
        for(ThreadedTransaction t : this.currentTransactions.values()){
            if(t.getTransactionId() == transactionId){
                tx = t;
                break;
            }
        }
        
        if(tx == null){
            tx = this.committedTransactions.get(transactionId);
        }
        
        if(tx == null){
            //the transaction was reverted, don't bother unbinding.
            return;
        }
        
        tx.boundEngines.remove(engine);
        
        if(tx.boundEngines.isEmpty()){
            //remove it from the committed transactions if it is empty.
            this.committedTransactions.remove(tx.getTransactionId());
        }
    }

    @Override
    public void unbindEngineExceptFrom(EngineBase engine, Collection<Long> transactionIds) {
        for(ThreadedTransaction tx : this.currentTransactions.values()){
            if(transactionIds.contains(tx.getTransactionId())){
                continue;
            }
            
            //try to remove its binding
            tx.boundEngines.remove(engine);
        }
        
        Iterator<ThreadedTransaction> it = this.committedTransactions.values().iterator();
        while(it.hasNext()){
            ThreadedTransaction tx = it.next();
            if(transactionIds.contains(tx.getTransactionId())){
                continue;
            }
            
            //try to remove its binding
            tx.boundEngines.remove(engine);
            
            if(tx.boundEngines.isEmpty()){
                //remove it from the committed transactions if it is empty.
                it.remove();
            }
        }
    }
    
    
        
    /**
     * A Transaction that is meant to exist within the context of one thread.
     * There should be no cross-thread transactional data access, only cross-thread
     * state querying.
     */
    protected class ThreadedTransaction implements Transaction{

        private TransactionOptions options;
        
        private AtomicBoolean isCompleted = new AtomicBoolean(false);
        private AtomicBoolean isRollbackOnly = new AtomicBoolean(false);
        
        private final long id;
        
        private AtomicReference<Set<TransactionListener>> listeners = new AtomicReference<>(null);
        
        final Set<EngineBase> boundEngines = new ConcurrentSkipListSet<>();
        
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

        @Override
        public void putTransactionListener(TransactionListener listener) {
            Set<TransactionListener> l = this.listeners.get();
            if(l == null){
                l = new ConcurrentSkipListSet<>();
                if(!this.listeners.compareAndSet(null, l)){
                    l = this.listeners.get();
                }
            }
            
            l.add(listener);
        }

        @Override
        public void removeTransactionListener(TransactionListener listener) {
            Set<TransactionListener> l = this.listeners.get();
            if(l == null){
                return;
            }
            l.remove(listener);
        }
    }
    
}
