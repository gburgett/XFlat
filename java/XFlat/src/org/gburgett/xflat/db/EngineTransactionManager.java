/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import org.gburgett.xflat.transaction.TransactionManager;

/**
 *
 * @author Gordon
 */
public abstract class EngineTransactionManager implements TransactionManager {
    
    /**
     * Gets a new commit ID for a transactionless write operation.
     * All transactionless writes can be thought of as transactions that are
     * automatically committed.  This allows us to provide isolation between
     * transactions and transactionless writes.
     * @return 
     */
    public abstract long transactionlessCommitId();
    
    /**
     * Gets the ID of the earliest open transaction.
     * @return The ID of the earliest open transaction.
     */
    public abstract long getLowestOpenTransaction();
    
    /**
     * Called by an engine in order to bind itself to a transaction.  This means
     * that the engine has transactional data for this transaction, so the 
     * transaction manager will not forget about the transaction so long as
     * engine remains bound to it.
     * <p/>  
     * If the engine is already bound to the transaction, this method does nothing.
     * @param engine The engine to bind to a transaction.
     */
    public abstract void bindEngineToCurrentTransaction(EngineBase engine);
    
    /**
     * Called by an engine in order to unbind itself from a transaction.  This means
     * that the engine no longer has transactional data for this transaction; either
     * the data in the transaction has been fully committed or fully cleaned.  In either
     * case the engine will no longer need to ask the transaction manager about
     * the status of the transaction.
     * @param engine The engine to unbind from a transaction.
     * @param transactionId The ID of the transaction to unbind.
     */
    public abstract void unbindEngineFromTransaction(EngineBase engine, Long transactionId);
    
    /**
     * Unbinds the engine from all its bound transactions except the given collection.
     * @see #unbindEngineFromTransaction(org.gburgett.xflat.db.EngineBase, java.lang.Long) 
     * @param engine
     * @param transactionIds 
     */
    public abstract void unbindEngineExceptFrom(EngineBase engine, Collection<Long> transactionIds);
    
    /**
     * Checks to see if the given transaction ID has been committed.  If so,
     * returns the transaction's commit ID.  Otherwise returns -1.
     * <p/>
     * This method is only valid so long as at least one engine is bound
     * to the transaction.  If no engines are bound to the transaction,
     * this may return erroneous data.
     * @param transactionId The ID of the transaction to check.
     * @return the transaction's commit ID if committed, -1 otherwise.
     */
    public abstract long isTransactionCommitted(long transactionId);
    
    /**
     * Checks to see if the given transaction ID has been reverted.  If so,
     * returns true, otherwise false.
     * </p>
     * This method is only valid so long as at least one engine is bound
     * to the transaction.  If no engines are bound to the transaction,
     * this may return erroneous data.
     * @param transactionId The ID of the transaction to check.
     * @return true if the transaction is reverted, false otherwise.
     */
    public abstract boolean isTransactionReverted(long transactionId);
 
    
    private AtomicLong lastId = new AtomicLong();
    
    /**
     * Generates a new Transaction ID.  The ID is composed of the lower
     * 48 bits of {@link System#currentTimeMillis() } plus a 16-bit uniquifier.
     * unfortunately this means we have a y6k problem :P  I'll let my descendants
     * deal with it. (seriously, I calculated it and we will run out of IDs on
     * 10/17/6429 at around 3am).
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
    
}
