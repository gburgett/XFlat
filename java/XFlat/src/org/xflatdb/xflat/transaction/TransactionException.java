/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

/**
 *
 * @author Gordon
 */
public class TransactionException extends RuntimeException {

    /**
     * Constructs an instance of
     * <code>TransactionException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public TransactionException(String msg, Throwable inner) {
        super(msg, inner);
    }
    
    public TransactionException(String msg) {
        super(msg);
    }
}
