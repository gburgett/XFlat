/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat;

/**
 * An XflatException thrown when a unique ID is duplicated in a table.
 * @author gordon
 */
public class DuplicateKeyException extends XFlatException {

    /**
     * Creates a new instance of
     * <code>DuplicateKeyException</code> without detail message.
     */
    public DuplicateKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs an instance of
     * <code>DuplicateKeyException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public DuplicateKeyException(String msg) {
        super(msg);
    }
}
