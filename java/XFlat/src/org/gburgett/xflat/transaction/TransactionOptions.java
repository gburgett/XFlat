/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 *
 * @author Gordon
 */
public class TransactionOptions {
    
    private boolean readOnly;
    /**
     * Gets whether this transaction is Read Only.  A ReadOnly transaction
     * cannot be committed.  Write operations during a ReadOnly transaction
     * throw an exception.
     */
    public boolean getReadOnly(){
        return this.readOnly;
    }
    
    private Isolation isolation;
    /**
     * Gets the transaction isolation level.
     */
    public Isolation getIsolationLevel(){
        return this.isolation;
    }
    
    
    /**
     * Sets whether this transaction is Read Only.  A ReadOnly transaction
     * cannot be committed.  Write operations during a ReadOnly transaction
     * throw an exception.
     */
    public TransactionOptions withReadOnly(boolean readOnly){
        TransactionOptions ret = new TransactionOptions(this);
        ret.readOnly = readOnly;
        return ret;
    }
    
    public TransactionOptions withIsolationLevel(Isolation level){
        TransactionOptions ret = new TransactionOptions(this);
        ret.isolation = level;
        return ret;
    }
    
    private TransactionOptions(){        
    }
    
    private TransactionOptions(TransactionOptions other){
        this.readOnly = other.readOnly;
        this.isolation = other.isolation;
    }
    
    /**
     * The default transaction options.
     * <p/>
     * ReadOnly: false, <br/>
     * IsolationLevel: SNAPSHOT <br/>
     */
    public static final TransactionOptions Default = new TransactionOptions()
                .withReadOnly(false)
                .withIsolationLevel(Isolation.SNAPSHOT);
    
}
