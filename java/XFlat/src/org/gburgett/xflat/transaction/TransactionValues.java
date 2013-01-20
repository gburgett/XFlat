/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.transaction;

/**
 *
 * @author Gordon
 */
public class TransactionValues {
    private long transactionId;
    /**
     * Gets the transactionId.
     */
    public long getTransactionId(){
        return this.transactionId;
    }
    private long commitId;
    /**
     * Gets the commitId.
     */
    public long getCommitId(){
        return this.commitId;
    }
    
    public TransactionValues(long transactionId){
        this(transactionId, -1);
    }
    public TransactionValues(long transactionId, long commitId){
        
    }
}
