/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

/**
 * an EventObject for Transaction events.
 * The source is the TransactionManager that managed the transaction.
 * @author Gordon
 */
public class TransactionEventObject extends java.util.EventObject {
    /**
     * Indicates a transaction committed event
     */
    public static final int COMMITTED = 1;
    /**
     * Indicates a transaction reverted event
     */
    public static final int REVERTED = 2;
    
    private int eventType;
    /**
     * Gets the transaction event type.
     * one of {@link #COMMITTED} or {@link #REVERTED}
     * @return 
     */
    public int getEventType(){
        return eventType;
    }
    
    private Transaction tx;
    /**
     * Gets the transaction in which the event occurred.
     * @return 
     */
    public Transaction getTransaction(){
        return tx;
    }
    
    public TransactionEventObject(TransactionManager source, Transaction tx, int eventType){
        super(source);
        this.tx = tx;
        this.eventType = eventType;
    }
}
