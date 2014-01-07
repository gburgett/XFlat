/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

import org.xflatdb.xflat.XFlatException;

/**
 * Thrown when an attempt is made to perform some action inside a transaction
 * that is not in an appropriate state for that action.
 * <p/>
 * An example would be attempting to write new data in a read-only transaction.
 * @author gordon
 */
public class TransactionStateException extends XFlatException {

    /**
     * Creates a new instance of
     * <code>TransactionStateException</code> without detail message.
     */
    public TransactionStateException(String msg, Throwable inner) {
        super(msg, inner);
    }

    /**
     * Constructs an instance of
     * <code>TransactionStateException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public TransactionStateException(String msg) {
        super(msg);
    }
}
