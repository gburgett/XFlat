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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import org.xflatdb.xflat.transaction.TransactionManager;

/**
 * The Base Class of all Transaction Managers.<p/>
 * This class defines the contract required of Transaction Manager implementations
 * by XFlat.  XFlat users do not need to concern themselves with these methods, only
 * the exposed TransactionManager interface.
 * @author Gordon
 */
public abstract class EngineTransactionManager implements TransactionManager, AutoCloseable {
    
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
     * Unbinds the engine from all its bound and closed transactions except the given collection.<br/>
     * The engine will not be unbound from any open transactions.
     * @see #unbindEngineFromTransaction(org.xflatdb.xflat.db.EngineBase, java.lang.Long) 
     * @param engine The engine to unbind.
     * @param transactionIds The transactions to remain bound to.
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

    /**
     * Returns true if any transactions are currently open.
     * @return 
     */
    public abstract boolean anyOpenTransactions();
 
    /**
     * Attempts to recover from an unexpected shutdown if necessary.
     * @param db 
     */
    public abstract void recover(XFlatDatabase db);
    
    /**
     * Closes any resources in use by this transaction manager in preparation
     * for shutdown.  Any exceptions at this point should be logged but not
     * rethrown since this is only used during shutdown.
     */
    public abstract void close();

}
