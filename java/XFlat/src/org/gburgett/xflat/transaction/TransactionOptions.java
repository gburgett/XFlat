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
    /**
     * Sets whether this transaction is Read Only.  A ReadOnly transaction
     * cannot be committed.  Write operations during a ReadOnly transaction
     * throw an exception.
     */
    public TransactionOptions setReadOnly(boolean readOnly){
        TransactionOptions ret = new TransactionOptions(this);
        ret.readOnly = readOnly;
        return ret;
    }
    
    private TransactionOptions(){        
    }
    
    private TransactionOptions(TransactionOptions other){
        
    }
    
    /**
     * The default transaction options.
     */
    public static final TransactionOptions Default = new TransactionOptions()
                    .setReadOnly(false);
    
}
