/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 * Enumerates the different transaction isolation levels supported in XFlat.
 * @author Gordon
 */
public enum Isolation {
    /**
     * Snapshot isolation level.
     * <p/>
     * http://en.wikipedia.org/wiki/Snapshot_isolation
     */
    SNAPSHOT,
}
