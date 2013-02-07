/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

/**
 *
 * @author Gordon
 */
public class TransactionPropagationException extends TransactionException {

    /**
     * Creates a new instance of
     * <code>TransactionPropagationException</code> without detail message.
     */
    public TransactionPropagationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs an instance of
     * <code>TransactionPropagationException</code> with the specified detail
     * message.
     *
     * @param msg the detail message.
     */
    public TransactionPropagationException(String msg) {
        super(msg);
    }
}
