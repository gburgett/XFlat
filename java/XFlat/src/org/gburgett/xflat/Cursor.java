/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

/**
 * A Cursor is an {@link Iterable} that maintains references to a resource,
 * and must be closed in a finally block.  It can also be used in a 
 * Java 7 try-with-resources.
 * @author gordon
 */
public interface Cursor<T> extends Iterable<T>, AutoCloseable {
    
}
