/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

/**
 * Represents a TransactionScope.
 * @author Gordon
 */
public interface TransactionScope extends AutoCloseable  {
    
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
     * Returns true if the transaction scope has been committed. 
     * <p/>
     * This refers to the
     * entire transaction scope; if this transaction object was opened within
     * a larger transaction scope, then calling {@link #commit() } on this object
     * will not result in a commit and this will return false until the larger
     * scope is committed.
     * @return true iff the entire ambient transaction successfully committed.
     */
    boolean isCommitted();
    
    /**
     * Returns true if the transaction scope has been reverted.
     * <p/>
     * If this transaction was propagated from a larger transaction scope (except
     * by {@link Propagation#NESTED}), then this will return true if {@link #revert() }
     * has been called on ANY transaction object participating in the transaction.
     * @return true iff the entire ambient transaction was reverted.
     */
    boolean isReverted();
    
    /**
     * Gets the options with which this transaction was opened.
     * @Return the TransactionOptions object provided when the transaction
     * was opened.
     */
    TransactionOptions getOptions();
    
    
    /**
     * Adds a transaction listener for the ambient transaction, if it does not already exist.
     * @param listener The listener to add to the entire ambient transaction.
     */
    void putTransactionListener(TransactionListener listener);
    
    /**
     * Removes a transaction listener for the ambient transaction.
     * @param listener The listener to remove from the entire ambient transaction.
     */
    void removeTransactionListener(TransactionListener listener);
    
    /**
     * Closes the current transaction scope.  If the transaction has yet to be committed
     * or reverted, and the transaction is the last participant in the ambient transaction scope
     * (i.e. the root scope), the entire transaction is reverted immediately.
     */
    @Override
    void close();
}
