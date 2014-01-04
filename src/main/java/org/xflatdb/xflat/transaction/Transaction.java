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
public interface Transaction {
    
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
     * Returns true if the transaction scope to which this transaction belongs has been committed. 
     * <p/>
     * This refers to the
     * entire transaction scope; if this transaction's scope was opened within
     * a larger transaction scope (except
     * by {@link Propagation#NESTED}), then calling {@link TransactionScope#commit() } on the scope
     * will not result in a commit and this will return false until the larger
     * scope is committed.
     * @return true iff the entire ambient transaction successfully committed.
     */
    boolean isCommitted();
    
    /**
     * Returns true if the transaction scope to which this transaction belongs has been reverted.
     * <p/>
     * If this transaction's scope was propagated from a larger transaction scope (except
     * by {@link Propagation#NESTED}), then this will return true if {@link TransactionScope#revert() }
     * has been called on ANY transaction scope participating in the transaction.
     * @return true iff the entire ambient transaction was reverted.
     */
    boolean isReverted();
    
    /**
     * Returns true if the current transaction scope was opened with the ReadOnly
     * option set.
     * @see TransactionOptions#getReadOnly() 
     * @return true if the current transaction is read only.
     */
    boolean isReadOnly();
    

}
