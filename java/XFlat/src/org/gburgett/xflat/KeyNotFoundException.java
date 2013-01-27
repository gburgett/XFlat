/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

/**
 * An XflatException thrown when a given ID is expected to be present but is not.
 * @author gordon
 */
public class KeyNotFoundException extends XFlatException {

    /**
     * Creates a new instance of
     * <code>KeyNotFoundException</code> without detail message.
     */
    public KeyNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs an instance of
     * <code>KeyNotFoundException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public KeyNotFoundException(String msg) {
        super(msg);
    }
}
