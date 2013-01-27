/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

/**
 * A Listener for Transaction Events.
 * @author Gordon
 */
public interface TransactionListener {
    /**
     * Signals a transaction event.  The event object describes the event.
     * @param event 
     */
    public void TransactionEvent(TransactionEventObject event);
}
