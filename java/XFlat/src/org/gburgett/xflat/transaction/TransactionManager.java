/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 *
 * @author Gordon
 */
public interface TransactionManager {
    
    /**
     * Gets the current transaction, or null if none exists.  The current
     * transaction is defined as the transaction retrieved by the last call to
     * {@link #openTransaction(org.gburgett.xflat.transaction.TransactionOptions) }
     * in this context (usually a thread context).
     * @return The current transaction, or null.
     */
    public Transaction getTransaction();
    
    /**
     * Opens a new transaction, using the {@link TransactionOptions#Default} options.
     * If a transaction is already open in this context, an IllegalStateException
     * is thrown.
     * @return 
     */
    public Transaction openTransaction();
    
    /**
     * Opens a new transaction, using the given TransactionOptions.  If a 
     * transaction is already open in this context, an IllegalStateException
     * is thrown.
     * @param options
     * @return 
     */
    public Transaction openTransaction(TransactionOptions options);
    
    /**
     * Gets a new commit ID for a transactionless write operation.
     * All transactionless writes can be thought of as transactions that are
     * automatically committed.  This allows us to provide isolation between
     * transactions and transactionless writes.
     * @return 
     */
    public long transactionlessCommitId();
    
    /**
     * Checks to see if the given transaction ID has been committed.  If so,
     * returns the transaction's commit ID.  Otherwise returns -1.
     * @param transactionId The ID of the transaction to check.
     * @return the transaction's commit ID if committed, -1 otherwise.
     */
    public long isTransactionCommitted(long transactionId);
    
    /**
     * Checks to see if the given transaction ID has been reverted.  If so,
     * returns true, otherwise false.
     * @param transactionId The ID of the transaction to check.
     * @return true if the transaction is reverted, false otherwise.
     */
    public boolean isTransactionReverted(long transactionId);
}
