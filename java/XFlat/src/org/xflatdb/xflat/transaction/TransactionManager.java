/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

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
    
    
}
