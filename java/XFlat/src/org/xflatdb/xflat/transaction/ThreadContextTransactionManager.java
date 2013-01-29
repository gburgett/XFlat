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
package org.xflatdb.xflat.transaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineTransactionManager;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.util.DocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

/**
 * A {@link TransactionManager} that uses the current thread as the context for transactions.
 * Each transaction opened by this manager will be bound to the current thread, and
 * the {@link #getTransaction() } method will return the transaction open on the current
 * thread, if any.
 * <p/>
 * This is the default TransactionManager used by XFlat.
 * @author Gordon
 */
public class ThreadContextTransactionManager extends EngineTransactionManager {

    private Map<Long, ThreadedTransaction> currentTransactions = new ConcurrentHashMap<>();
    
    private Map<Long, ThreadedTransaction> committedTransactions = new ConcurrentHashMap<>();
    
    private DocumentFileWrapper journalWrapper;
    private Document transactionJournal = null;
    
    private Log log = LogFactory.getLog(getClass());
    
    /**
     * Creates a new ThreadContextTransactionManager, which will manage a mapping
     * of threads to transactions.
     * @param wrapper A wrapper which wraps the file to which this Transaction Manager
     * can save its Transaction Journal, for recovery in case of catastrophic error.
     */
    public ThreadContextTransactionManager(DocumentFileWrapper wrapper){
        this.journalWrapper = wrapper;
    }
    
    /**
     * Gets the Id of the current context, which is the current thread's ID.
     * @return The current thread's ID.
     */
    protected Long getContextId(){
        return Thread.currentThread().getId();
    }
    
    @Override
    public Transaction getTransaction() {
        return currentTransactions.get(getContextId());
    }

    @Override
    public Transaction openTransaction() {
        return openTransaction(TransactionOptions.Default);
    }

    @Override
    public Transaction openTransaction(TransactionOptions options) {
        if(currentTransactions.get(getContextId()) != null){
            throw new IllegalStateException("Transaction already open on current thread.");
        }
        
        ThreadedTransaction ret = new ThreadedTransaction(generateNewId(), options);
        if(currentTransactions.put(getContextId(), ret) != null){
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
        
        ThreadedTransaction tx = currentTransactions.get(getContextId());
        if(tx == null){
            return;
        }
                
        //we can get away with just adding it in an unsynchronized context because
        //this is never going to be called at the same time as unbind, since unbind
        //always happens in the context of a commit or revert (which is the same thread as this)
        //or another thread's cleanup after the transaction is closed.
        tx.boundEngines.add(engine);
    }

    @Override
    public synchronized void unbindEngineExceptFrom(EngineBase engine, Collection<Long> transactionIds) {
                
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

    @Override
    public boolean anyOpenTransactions() {
        return !this.currentTransactions.isEmpty();
    }
    
    private void loadJournal() throws IOException, JDOMException{
        transactionJournal = journalWrapper.readFile();
        if(transactionJournal == null){
            transactionJournal = new Document();
            transactionJournal.setRootElement(new Element("transactionJournal"));
        }
    }
    
    private synchronized void commit(ThreadedTransaction tx){
        //journal the entry so we can recover if catastrophic failure occurs
        TransactionJournalEntry entry = new TransactionJournalEntry();
        entry.txId = tx.id;
        entry.commitId = tx.commitId;
        for(EngineBase e : tx.boundEngines){
            entry.tableNames.add(e.getTableName());
        }
        
        Element entryElement;
        try {
            if(transactionJournal == null){
                loadJournal();
            }
            entryElement = toElement.convert(entry);
            transactionJournal.getRootElement().addContent(entryElement);
            journalWrapper.writeFile(transactionJournal);
        } catch (ConversionException | IOException | JDOMException ex) {
            throw new TransactionException("Unable to commit, could not access journal file " + journalWrapper, ex);
        }
        
        //commit all, and if any fail revert all.
        try{
            for(EngineBase e : tx.boundEngines){
                if(log.isTraceEnabled())
                    log.trace(String.format("committing transaction %d to table %s", tx.id, e.getTableName()));
                e.commit(tx);
            }
        }catch(Exception ex){
            try{
                //uncommit
                tx.commitId = -1;
                tx.revert();
            }catch(TransactionException ex2){
                throw new TransactionException("Unable to commit, " + ex2.getMessage(), ex);
            }
            
            if(ex instanceof TransactionException)
                throw ex;
            
            throw new TransactionException("Unable to commit: " + ex.getMessage(), ex);
        }
        
        //remove it from the transaction journal
        transactionJournal.getRootElement().removeContent(entryElement);
        
        //we're all committed, so we can finally say so.
        committedTransactions.put(tx.id, tx);
    }
    
    private void revert(Iterable<EngineBase> boundEngines, long txId, boolean isRecovering){
        Set<String> failedReverts = null;
        Exception last = null;
        for(EngineBase e : boundEngines){
            
            try{
                e.revert(txId, isRecovering);
            }catch(Exception ex){
                LogFactory.getLog(getClass()).error(ex);
                if(failedReverts == null)
                    failedReverts = new HashSet<>();
                failedReverts.add(e.getTableName());
                last = ex;
            }
        }
        if(failedReverts != null && failedReverts.size() > 0){
            StringBuilder msg = new StringBuilder("Unable to revert all bound engines, the data in the following engines may be corrupt: ");
            for(String s : failedReverts){
                msg.append(s).append(", ");
            }
            throw new TransactionException(msg.toString(), last);
        }
    }

    @Override
    public void close() {
        //all transactions auto-revert now.
        this.currentTransactions.clear();
    }

    @Override
    public void recover(XFlatDatabase db) {
        //open the journal
        
        try {
            if(transactionJournal == null){
                loadJournal();
            }            
        } catch (IOException | JDOMException ex) {
            throw new XFlatException("Unable to recover, could not access journal file " + journalWrapper, ex);
        }
        
        try{
            Iterator<Element> children = transactionJournal.getRootElement().getChildren().iterator();
            while(children.hasNext()){
                TransactionJournalEntry entry;
                try {
                     entry = fromElement.convert(children.next());                     
                } catch (ConversionException ex) {
                    //entry is corrupt, remove and continue
                    children.remove();
                    continue;
                }

                List<EngineBase> toRevert = new ArrayList<>();
                for(String table : entry.tableNames){
                    toRevert.add(db.getEngine(table));
                }
                
                //revert the transaction in all the engines
                revert(toRevert, entry.txId, true);
                
                //successful revert - remove the entry
                children.remove();
                
                //save the journal after each successful revert
                this.journalWrapper.writeFile(transactionJournal);
            }
        }catch(TransactionException | IOException ex){
            throw new XFlatException("Unable to recover", ex);
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
        
        //we can get away with this being an unsynchronized HashSet because it will only ever be added to by one
        //thread, and then only so long as the transaction is open, and then will be removed from
        //by a different thread, but only one at a time, synchronized elsewhere, and after all adds are finished.
        final Set<EngineBase> boundEngines = new HashSet<>();
        
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
            if(this.isCompleted.get()){
                throw new IllegalTransactionStateException("Cannot commit a completed transaction");
            }
            
            commitId = generateNewId();
            ThreadContextTransactionManager.this.commit(this);
            //soon as commit returns, we are committed.
            this.isCompleted.set(true);
            
            fireEvent(TransactionEventObject.COMMITTED);
        }

        @Override
        public void revert() {
            if(!isCompleted.compareAndSet(false, true)){
                throw new IllegalTransactionStateException("Cannot rollback a completed transaction");
            }
            
            ThreadContextTransactionManager.this.revert(this.boundEngines, this.id, false);
            
            fireEvent(TransactionEventObject.REVERTED);
        }

        @Override
        public void setRevertOnly() {
            this.isRollbackOnly.set(true);
        }

        @Override
        public long getTransactionId() {
            return this.id;
        }

        @Override
        public void close() {
            if(isCompleted.compareAndSet(false, true)){
                //we completed in the close, need to revert.
                ThreadContextTransactionManager.this.revert(this.boundEngines, this.id, false);
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
            return this.isCompleted.get() && commitId > -1;
        }

        @Override
        public boolean isReverted() {
            return isCompleted.get() && commitId == -1;
        }

        @Override
        public void putTransactionListener(TransactionListener listener) {
            Set<TransactionListener> l = this.listeners.get();
            if(l == null){
                l = new HashSet<>();
                if(!this.listeners.compareAndSet(null, l)){
                    l = this.listeners.get();
                }
            }
            synchronized(l){
                l.add(listener);
            }
        }

        @Override
        public void removeTransactionListener(TransactionListener listener) {
            Set<TransactionListener> l = this.listeners.get();
            if(l == null){
                return;
            }
            synchronized(l){
                l.remove(listener);
            }
        }

        @Override
        public TransactionOptions getOptions() {
            return this.options;
        }
        
        private void fireEvent(int event){
            Set<TransactionListener> listeners = this.listeners.get();
            if(listeners == null)
                return;
            
            TransactionEventObject evtObj = new TransactionEventObject(ThreadContextTransactionManager.this, this, event);
            synchronized(listeners){
                for(Object l : listeners.toArray()){
                    ((TransactionListener)l).TransactionEvent(evtObj);
                }
            }
        }
    }
    
    
    private class TransactionJournalEntry{
        public long txId;
        public long commitId;
        
        public Set<String> tableNames = new HashSet<>();
    }
    
    private Converter<TransactionJournalEntry, Element> toElement = new Converter<TransactionJournalEntry, Element>(){
        @Override
        public Element convert(TransactionJournalEntry source) throws ConversionException {
            Element ret = new Element("entry");
            ret.setAttribute("txId", Long.toString(source.txId));
            ret.setAttribute("commit", Long.toString(source.commitId));
            
            for(String s : source.tableNames){
                ret.addContent(new Element("table").setText(s));
            }
            
            return ret;
        }
    };
    
    private Converter<Element, TransactionJournalEntry> fromElement = new Converter<Element, TransactionJournalEntry>(){
        @Override
        public TransactionJournalEntry convert(Element source) throws ConversionException {
            TransactionJournalEntry ret = new TransactionJournalEntry();
            
            try{
                String txId = source.getAttributeValue("txId");
                if(txId == null){
                    throw new ConversionException("txId attribute required");
                }
                ret.txId = Long.parseLong(txId);
            
                String commitId = source.getAttributeValue("commit");
                if(commitId != null){
                    ret.commitId = Long.parseLong(commitId);
                }
                
                for(Element e : source.getChildren("table")){
                    ret.tableNames.add(e.getText());
                }
            }
            catch(NumberFormatException ex){
                throw new ConversionException("Conversion failure", ex);
            }
            
            return ret;
        }
    };
}
