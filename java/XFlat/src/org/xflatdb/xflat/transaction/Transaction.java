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

/**
 * Represents a transaction in the XFlat database.  <br/>
 * While in context and open, the database will operate with the isolation level specified in 
 * the {@link TransactionOptions} given at the time this transaction was opened.
 * <p/>
 * When committed, the data modified by this transaction will be durably saved
 * to disk, and immediately will be available to transactionless reads as well
 * as new transactions.
 * <p/>
 * When closed, if a transaction has not been committed, it will be automatically
 * reverted.
 * @author Gordon
 */
public interface Transaction extends AutoCloseable {
    
    /**
     * Commits the transaction immediately.  Transactions are committed atomically
     * and durably, so that the instant this method returns successfully, the caller can be
     * assured that the modifications performed by this transaction have been
     * saved to disk.
     * 
     * @throws TransactionException if an error occurred during the commit.  The
     * transaction manager will automatically revert the transaction upon a commit 
     * error.
     * @throws IllegalTransactionStateException if the transaction has already been
     * committed, reverted, or is revert only.
     */
    void commit() throws TransactionException;
    
    /**
     * Reverts the transaction immediately.  When a transaction is reverted,
     * the database acts as though all the modifications performed inside the
     * transaction scope never happened.
     */
    void revert();
    
    /**
     * Sets the transaction to be "Revert Only".  The transaction will continue
     * as normal, but will throw an {@link IllegalStateException} if {@link #commit() }
     * is called.
     */
    void setRevertOnly();
    
    /**
     * Gets the ID of this transaction.  A Transaction's ID is linked to the time
     * it was created, so a transaction with a higher ID is guaranteed to have
     * been created later.  Transaction IDs are also valid across multiple tables.
     * @return The transaction's ID.
     */
    long getTransactionId();
    
    /**
     * Gets the commit ID of this transaction.  A transaction has a commit ID if
     * it has been committed.  This commit ID is also linked to the time it was
     * created, and can be compared to other transaction IDs to see if this
     * transaction was committed before, during, or after another transaction.
     * @return The transaction's commit ID, or -1 if uncommitted.
     */
    long getCommitId();
    
    /**
     * Returns true if the transaction has been committed.
     * @return true iff the transaction successfully committed.
     */
    boolean isCommitted();
    
    /**
     * Returns true if the transaction has been reverted.
     * @return true iff the transaction was reverted.
     */
    boolean isReverted();
    
    /**
     * Gets the options with which this transaction was opened.
     * @Return the TransactionOptions object provided when the transaction
     * was opened.
     */
    TransactionOptions getOptions();
    
    /**
     * Closes the current transaction.  If the transaction has yet to be committed
     * or reverted, the transaction is reverted immediately.
     */
    @Override
    void close();
    
    /**
     * Adds a transaction listener for this transaction, if it does not already exist.
     * @param listener The listener to add to this transaction.
     */
    void putTransactionListener(TransactionListener listener);
    
    /**
     * Removes a transaction listener for this transaction.
     * @param listener The listener to remove from this transaction.
     */
    void removeTransactionListener(TransactionListener listener);
}
