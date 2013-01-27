/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 * This exception is thrown when a write conflict occurs upon committing a transaction,
 * in an isolation level that can cause write conflicts.
 * @author Gordon
 */
public class WriteConflictException extends TransactionException {

    /**
     * Creates a new instance of
     * <code>WriteConflictException</code> without detail message.
     */
    public WriteConflictException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs an instance of
     * <code>WriteConflictException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public WriteConflictException(String msg) {
        super(msg);
    }
}
