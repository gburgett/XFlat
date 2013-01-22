/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

import java.io.Closeable;

/**
 *
 * @author Gordon
 */
public interface Transaction extends AutoCloseable {
    
    void commit() throws TransactionException;
    
    void rollback();
    
    void setRollbackOnly();
    
    /**
     * Gets the ID of this transaction.  A Transaction's ID is linked to the time
     * it was created, so a transaction with a higher ID is guaranteed to have
     * been created later.  Transaction IDs are also valid across 
     * @return 
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
    
    boolean isCommitted();
    
    boolean isReverted();
    
    @Override
    void close();
    
    /**
     * Adds a transaction listener for this transaction, if it does not already exist.
     * @param listener 
     */
    void putTransactionListener(TransactionListener listener);
    
    /**
     * Removes a transaction listener for this transaction.
     * @param listener 
     */
    void removeTransactionListener(TransactionListener listener);
}
