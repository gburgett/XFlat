/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

/**
 *
 * @author gordon
 */
public class DuplicateKeyException extends XflatException {

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
