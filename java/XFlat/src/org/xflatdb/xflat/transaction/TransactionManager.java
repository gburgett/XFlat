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
     * Gets the current transaction representing the ambient transaction scope,
     * or null if the current scope is transactionless.
     */
    public Transaction getTransaction();
    
    /**
     * Opens a new transaction scope, using the {@link TransactionOptions#Default} options.
     * If a transaction is already open in this context, an IllegalStateException
     * is thrown.
     * @return A new Transaction object representing the transaction open in this context.
     * @throws IllegalStateException if a transaction is already open in this context.
     */
    public TransactionScope openTransaction();
    
    /**
     * Opens a new transaction scope, using the given TransactionOptions.  A
     * TransactionScope will create or participate in an ambient transaction,
     * depending on the Propagation options.  
     * <p/>
     * The ambient transaction is committed
     * only when all its associated TransactionScopes are committed.<br/>
     * If any TransactionScope is reverted, the entire ambient transaction is reverted.<br/>
     * The ambient transaction is destroyed when the last TransactionScope is closed.
     * @param options The TransactionOptions to apply to this transaction.
     * @return A new Transaction object representing the transaction open in this context.
     * @throws IllegalStateException if a transaction is already open in this context.
     */
    public TransactionScope openTransaction(TransactionOptions options);
    
    
}
