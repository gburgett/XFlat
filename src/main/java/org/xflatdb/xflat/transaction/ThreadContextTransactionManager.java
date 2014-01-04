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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineTransactionManager;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.util.DocumentFileWrapper;

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

    private Map<Long, AmbientThreadedTransactionScope> currentTransactions = new ConcurrentHashMap<>();
    
    private Map<Long, AmbientThreadedTransactionScope> committedTransactions = new ConcurrentHashMap<>();
    
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
        TransactionBase tx = currentTransactions.get(getContextId());
        if(tx == null || tx.getOptions().getPropagation() == Propagation.NOT_SUPPORTED)
            return null;
        
        return tx.getTransaction();
    }

    @Override
    public TransactionScope openTransaction() throws TransactionPropagationException {
        return openTransaction(TransactionOptions.DEFAULT);
    }

    @Override
    public TransactionScope openTransaction(TransactionOptions options) throws TransactionPropagationException {
        
        AmbientThreadedTransactionScope ret;
        long contextId = getContextId();
        
        switch(options.getPropagation()){
            case MANDATORY:
                ret = currentTransactions.get(contextId);
                if(ret == null || ret.getOptions().getPropagation() == Propagation.NOT_SUPPORTED){
                    throw new TransactionPropagationException("propagation MANDATORY, but no current transaction.");
                }
                //use the current transaction, with a wrapper to prevent
                    //this instance from closing it prematurely.
                return new WrappingTransactionScope(ret, options.getReadOnly());
                
            case NESTED:
                throw new UnsupportedOperationException("Nested transactions not yet supported");
                
            case NEVER:
                ret = currentTransactions.get(contextId);
                if(ret != null && ret.getOptions().getPropagation() != Propagation.NOT_SUPPORTED){
                    throw new TransactionPropagationException("propagation NEVER, but current transaction exists");
                }
                //return a shell object representing the non-transactional operation.
                return new EmptyTransactionScope(options);
                
            case NOT_SUPPORTED:
                ret = currentTransactions.remove(contextId);
                if(ret == null || ret.getOptions().getPropagation() == Propagation.NOT_SUPPORTED){
                    //we are already operating non-transactionally, just need
                    //to return a shell object.
                    return new EmptyTransactionScope(options);
                }
                //need to return a shell that will also replace the suspended
                //transaction when it is closed.                
                return new NotSupportedTransaction(ret, options);
                
            case REQUIRED:
                ret = currentTransactions.get(contextId);
                if(ret == null || ret.getOptions().getPropagation() == Propagation.NOT_SUPPORTED){
                    //no current transaction, create a new one
                    //suspending the NOT_SUPPORTED transaction if it exists.
                    ret = new AmbientThreadedTransactionScope(generateNewId(), ret, options);
                    currentTransactions.put(contextId, ret);
                }
                
                //use the current transaction, with a wrapper to prevent
                //this instance from closing it prematurely.
                return new WrappingTransactionScope(ret, options.getReadOnly());

                
            case REQUIRES_NEW:
                //create a new transaction, suspending the current one if it exists.
                ret = currentTransactions.get(contextId);
                ret = new AmbientThreadedTransactionScope(generateNewId(), ret, options);
                currentTransactions.put(contextId, ret);
                
                //use the current transaction, with a wrapper to prevent
                //this instance from closing it prematurely.
                return new WrappingTransactionScope(ret, options.getReadOnly());
                
            case SUPPORTS:
                ret = currentTransactions.get(contextId);
                if(ret == null || ret.getOptions().getPropagation() == Propagation.NOT_SUPPORTED){
                    //we are already operating non-transactionally, just need
                    //to return a shell object.
                    return new EmptyTransactionScope(options);
                }
                
                //use the current transaction, with a wrapper to prevent
                //this instance from closing it prematurely.
                return new WrappingTransactionScope(ret, options.getReadOnly());
                
            default:
                throw new UnsupportedOperationException("Propagation behavior not supported: " + options.getPropagation().toString());
        }        
    }

    @Override
    public long isTransactionCommitted(long transactionId) {
        TransactionBase tx = committedTransactions.get(transactionId);
        return tx == null ? -1 : tx.commitId;
    }
    
    @Override
    public boolean isTransactionReverted(long transactionId) {
        //if we find it in the current transactions, check the transaction
        for(TransactionBase tx : currentTransactions.values()){
            if(tx.id == transactionId){
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
        for(TransactionBase tx : currentTransactions.values()){
            if(tx.id < lowest){
                lowest = tx.id;
            }
        }
        
        return lowest;
    }

    @Override
    public void bindEngineToCurrentTransaction(EngineBase engine) {
        
        TransactionBase tx = currentTransactions.get(getContextId());
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
                
        Iterator<AmbientThreadedTransactionScope> it = this.committedTransactions.values().iterator();
        while(it.hasNext()){
            TransactionBase tx = it.next();
            if(transactionIds.contains(tx.id)){
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
    
    private synchronized void commit(AmbientThreadedTransactionScope tx) throws TransactionException {
        //journal the entry so we can recover if catastrophic failure occurs
        TransactionJournalEntry entry = new TransactionJournalEntry();
        entry.txId = tx.id;
        entry.commitId = tx.commitId;
        for(EngineBase e : tx.boundEngines){
            entry.tableNames.add(e.getTableName());
        }
        
        Element entryElement = null;
        if(tx.options.isDurable()){
            //use the transaction journal to ensure durability
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
        }
        
        //commit all, and if any fail revert all.
        try{
            for(EngineBase e : tx.boundEngines){
                if(log.isTraceEnabled())
                    log.trace(String.format("committing transaction %d to table %s", tx.id, e.getTableName()));
                e.commit(tx.getTransaction(), tx.getOptions());
            }
        }
        catch(Exception ex){
            try{
                //uncommit
                tx.commitId = -1;
                tx.revert();
            }catch(XFlatException ex2){
                throw new TransactionException("Unable to commit, and another error occured during revert: " + ex2.getMessage(), ex);
            }
            
            //we were able to revert all, no need to keep the transaction in the journal.
            if(tx.options.isDurable()){
                transactionJournal.getRootElement().removeContent(entryElement);
                try {
                    journalWrapper.writeFile(transactionJournal);
                } catch (IOException ioEx) {
                    //this is not the most important exception
                }
            }
            
            
            if(ex instanceof TransactionException)
                throw ex;
            
            throw new TransactionException("Unable to commit: " + ex.getMessage(), ex);
        }
        
        //remove it from the transaction journal
        if(tx.options.isDurable()){
            transactionJournal.getRootElement().removeContent(entryElement);
            try {
                journalWrapper.writeFile(transactionJournal);
            } catch (IOException ex) {
                throw new TransactionException("Unable to commit, could not access journal file " + journalWrapper, ex);
            }
        }
        
        //we're all committed, so we can finally say so.
        committedTransactions.put(tx.id, tx);
    }
    
    private void revert(Iterable<EngineBase> boundEngines, long txId, boolean isRecovering) {
        Set<String> failedReverts = null;
        RuntimeException last = null;
        for(EngineBase e : boundEngines){
            
            try{
                e.revert(txId, isRecovering);
            }catch(RuntimeException ex){
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
            //the exceptions we caught were all runtime exceptions, so we are going to throw a runtime exception
            throw new XFlatException(msg.toString(), last);
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
        }catch(XFlatException | IOException ex){
            throw new XFlatException("Unable to recover", ex);
        }
    }

    @Override
    public boolean isCommitInProgress(long transactionId) {
        TransactionBase tx = this.currentTransactions.get(transactionId);
        if(tx == null)
            return false;
        
        //in-progress if the commit ID was assigned and the tx was not yet committed.
        return tx.commitId != -1 && !tx.isCommitted();
    }
    
    /**
     * The base class for the different types of transactions handled by this
     * transaction manager.  The different implementations are dependent on
     * the propagation level used when the transaction was opened.
     */
    protected abstract class TransactionBase implements TransactionScope {
        protected TransactionOptions options;
        
        protected AtomicBoolean isCompleted = new AtomicBoolean(false);
        protected AtomicBoolean isRollbackOnly = new AtomicBoolean(false);
        
        protected volatile boolean isClosed = false;
        
        protected final long id;
        
        protected AmbientThreadedTransactionScope suspended;
        
        //we can get away with this being an unsynchronized HashSet because it will only ever be added to by one
        //thread, and then only so long as the transaction is open, and then will be removed from
        //by a different thread, but only one at a time, synchronized elsewhere, and after all adds are finished.
        final Set<EngineBase> boundEngines = new HashSet<>();
        
        protected AtomicReference<Set<TransactionListener>> listeners = new AtomicReference<>(null);
    
        protected long commitId = -1;
        
        //The transaction representing this transaction scope.
        private final Transaction transaction = new Transaction(){
            @Override
            public long getTransactionId() {
                return id;
            }

            @Override
            public long getCommitId() {
                return commitId;
            }

            @Override
            public boolean isCommitted() {
                return TransactionBase.this.isCommitted();
            }

            @Override
            public boolean isReverted() {
                return TransactionBase.this.isReverted();
            }

            @Override
            public boolean isReadOnly() {
                return options.getReadOnly();
            }
            
        };
        
        public Transaction getTransaction(){
            return transaction;
        }
        
        
        protected TransactionBase(long id, AmbientThreadedTransactionScope suspended, TransactionOptions options){
            this.id = id;
            this.suspended = suspended;
            
            this.options = options;
            if(this.options.getReadOnly()){
                this.isRollbackOnly.set(true);
            }
        }
        
        protected void fireEvent(int event){
            Set<TransactionListener> listeners = this.listeners.get();
            if(listeners == null)
                return;
            
            TransactionEventObject evtObj = new TransactionEventObject(ThreadContextTransactionManager.this, this.transaction, event);
            synchronized(listeners){
                for(Object l : listeners.toArray()){
                    ((TransactionListener)l).TransactionEvent(evtObj);
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
        public void setRevertOnly() {
            this.isRollbackOnly.set(true);
        }

        @Override
        public TransactionOptions getOptions() {
            return this.options;
        }
        
        
        @Override
        public void close() {
            if(suspended != null && !suspended.isClosed){
                //need to put back the suspended transaction
                ThreadContextTransactionManager.this.currentTransactions.put(getContextId(), suspended);
                suspended = null;
            }
            
            this.isClosed = true;
        }
    }
    
    /**
     * A Transaction that is meant to exist within the context of one thread.
     * There should be no cross-thread transactional data access, only cross-thread
     * state querying.
     */
    protected class AmbientThreadedTransactionScope extends TransactionBase {

        private List<WrappingTransactionScope> uncommittedScopes = new LinkedList<>();
        private List<WrappingTransactionScope> wrappingScopes = new LinkedList<>();
        
        protected AmbientThreadedTransactionScope(long id, AmbientThreadedTransactionScope suspended, TransactionOptions options){
            super(id, suspended, options);
        }
        
        @Override
        public void commit() throws TransactionException {
            throw new UnsupportedOperationException("should not be called directly");
        }
        
        private void doCommit() throws TransactionException {
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
        
        void completeWrappingScope(WrappingTransactionScope scope) throws TransactionException {
            if(uncommittedScopes.remove(scope) && uncommittedScopes.isEmpty()){
                //all wrapping transaction scopes have completed, we can commit
                doCommit();
            }
            //otherwise we do nothing, simply mark the wrapping scope as completed by removing it from the list.
        }
        
        void addWrappingScope(WrappingTransactionScope scope){
            synchronized(this){
                //add them at the beginning because the most recent ones
                //are most likely to close first.
                if(!scope.getOptions().getReadOnly()){
                    //only add it to uncommitted scopes if it can actually write.
                    //ReadOnly scopes can close without commit, but an explicit revert
                    //will still revert the entire ambient scope.
                    uncommittedScopes.add(0, scope);
                }
                wrappingScopes.add(0, scope);
            }
        }
        
        
        @Override
        public void revert() {
            if(!isCompleted.compareAndSet(false, true)){
                throw new IllegalTransactionStateException("Cannot rollback a completed transaction");
            }
            
            if(!this.options.getReadOnly()){
                doRevert();
            }
            
            fireEvent(TransactionEventObject.REVERTED);
        }

        protected void doRevert() {            
            ThreadContextTransactionManager.this.revert(this.boundEngines, this.id, false);
        }
        
        void closeWrappingScope(WrappingTransactionScope scope){
            synchronized(this){
                if(uncommittedScopes.remove(scope) && !this.isCompleted.get()){
                    //the scope was uncommitted, need to revert the transaction
                    revert();
                }

                if(wrappingScopes.remove(scope) && wrappingScopes.isEmpty()){
                    //all wrapping transaction scopes have closed, we can close.
                    close();
                }
                //otherwise, can't close yet, still have some open scopes.
            }
        }
        
        @Override
        public void close(){
            if(isCompleted.compareAndSet(false, true)){
                //we completed in the close, need to revert.
                doRevert();
            }
            
            //remove the transaction scope from the current transactions map
            Iterator<AmbientThreadedTransactionScope> it = currentTransactions.values().iterator();
            while(it.hasNext()){
                //Object equality because we don't know which 
                if(it.next() == this){
                    it.remove();
                    break;
                }
            }
            
            super.close();
        }
    }
    
    /** 
     * A transaction that implements the {@link Propagation#NOT_SUPPORTED} behavior,
     * maintaining a reference to the suspended transaction so that it can be
     * replaced when this is closed.
     */
    protected class NotSupportedTransaction extends TransactionBase {
        public NotSupportedTransaction(AmbientThreadedTransactionScope suspended, TransactionOptions options){
            super(-1, suspended, options);
        }
                
        @Override
        public void commit() throws TransactionException {
            throw new IllegalTransactionStateException("Cannot commit a transaction opened with propagation " + 
                    "NEVER or NOT_SUPPORTED");
        }

        @Override
        public void revert() {
            throw new IllegalTransactionStateException("Cannot revert a transaction opened with propagation " + 
                    "NEVER or NOT_SUPPORTED");
        }
        
    }
        
    /**
     * A transaction object that represents no open transaction.  This is
     * created by opening a transaction with the {@link Propagation#NEVER} or
     * with {@link Propagation#NOT_SUPPORTED} when the 
     */
    protected class EmptyTransactionScope implements TransactionScope{

        private TransactionOptions options;
        
        private volatile boolean isCommitted = false;
        private volatile boolean isReverted = false;
        private volatile boolean isClosed = false;        
        
        
        public EmptyTransactionScope(TransactionOptions options){
            this.options = options;
        }
        
        
        @Override
        public void commit() throws TransactionException {
            throw new IllegalTransactionStateException("Cannot commit a transaction opened with propagation " + 
                    "NEVER or NOT_SUPPORTED");
        }

        @Override
        public void revert() {
            throw new IllegalTransactionStateException("Cannot revert a transaction opened with propagation " + 
                    "NEVER or NOT_SUPPORTED");
        }

        @Override
        public void setRevertOnly() {
            
        }
        
        @Override
        public boolean isCommitted() {
            return isCommitted;
        }

        @Override
        public boolean isReverted() {
            return isReverted;
        }

        @Override
        public TransactionOptions getOptions() {
            return options;
        }

        @Override
        public void close() {
            //nothing to do
            isClosed = true;
        }

        @Override
        public void putTransactionListener(TransactionListener listener) {

        }

        @Override
        public void removeTransactionListener(TransactionListener listener) {

        }
        
        
    }
    
    /**
     * A TransactionScope object that provides a view onto the ambient scope.
     * There may be multiple wrapping transaction scopes all pointing to the
     * same ambient transaction scope.  When ALL of these is committed, the
     * underlying ambient transaction is committed.
     */
    protected class WrappingTransactionScope implements TransactionScope {

        private AmbientThreadedTransactionScope wrapped;
        
        private TransactionOptions options;
        
        protected WrappingTransactionScope(AmbientThreadedTransactionScope wrapped, boolean isReadOnly){
            this.wrapped = wrapped;
            this.options = wrapped.getOptions().withReadOnly(isReadOnly);
            
            wrapped.addWrappingScope(this);
        }
        
        @Override
        public void commit() throws TransactionException {
            wrapped.completeWrappingScope(this);
        }

        @Override
        public void revert() {
            wrapped.revert();
        }

        @Override
        public void setRevertOnly() {
            wrapped.setRevertOnly();
        }

        @Override
        public boolean isCommitted() {
            return wrapped.isCommitted();
        }

        @Override
        public boolean isReverted() {
            return wrapped.isReverted();
        }

        @Override
        public TransactionOptions getOptions() {
            return this.options;
        }

        @Override
        public void close() {
            wrapped.closeWrappingScope(this);
        }

        @Override
        public void putTransactionListener(TransactionListener listener) {
            wrapped.putTransactionListener(listener);
        }

        @Override
        public void removeTransactionListener(TransactionListener listener) {
            wrapped.removeTransactionListener(listener);
        }
        
    }
    
    //<editor-fold desc="transaction journal">
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

    //</editor-fold>
}
