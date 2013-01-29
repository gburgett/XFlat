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
 * The TransactionManager opens transactions and manages their lifecycle.  Different
 * TransactionManagers provide different contexts for their transactions.
 * @author Gordon
 */
public interface TransactionManager {
    
    /**
     * Gets the current transaction, or null if none exists.  The current
     * transaction is defined as the transaction retrieved by the last call to
     * {@link #openTransaction(org.xflatdb.xflat.transaction.TransactionOptions) }
     * in this context (usually a thread context).
     * @return The current transaction, or null.
     */
    public Transaction getTransaction();
    
    /**
     * Opens a new transaction, using the {@link TransactionOptions#Default} options.
     * If a transaction is already open in this context, an IllegalStateException
     * is thrown.
     * @return A new Transaction object representing the transaction open in this context.
     * @throws IllegalStateException if a transaction is already open in this context.
     */
    public Transaction openTransaction();
    
    /**
     * Opens a new transaction, using the given TransactionOptions.  If a 
     * transaction is already open in this context, an IllegalStateException
     * is thrown.
     * @param options The TransactionOptions to apply to this transaction.
     * @return A new Transaction object representing the transaction open in this context.
     * @throws IllegalStateException if a transaction is already open in this context.
     */
    public Transaction openTransaction(TransactionOptions options);
    
    
}
