/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 * An exception that occurs on an attempt to commit or roll back a transaction that
 * has already been resolved.
 * @author Gordon
 */
public class IllegalTransactionStateException extends TransactionException {
    
    /**
     * Constructs an instance of
     * <code>TransactionException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    IllegalTransactionStateException(String msg) {
        super(msg);
    }
}
