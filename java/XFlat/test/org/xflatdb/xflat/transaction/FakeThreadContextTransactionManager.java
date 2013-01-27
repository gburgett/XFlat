/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

import org.xflatdb.xflat.transaction.ThreadContextTransactionManager;
import org.xflatdb.xflat.util.DocumentFileWrapper;

/**
 * Overrides the ThreadContextTransactionManager so the context ID can be swapped.
 * The context ID is normally bound to the current thread's ID, this fake transaction manager
 * can swap that out within a single thread.
 * @author Gordon
 */
public class FakeThreadContextTransactionManager extends ThreadContextTransactionManager {
    private long currentContextId = 0;
    
    public FakeThreadContextTransactionManager(DocumentFileWrapper wrapper){
        super(wrapper);
    }
    
    @Override
    public Long getContextId(){
        return currentContextId;
    }
    
    public void setContextId(Long contextId){
        this.currentContextId = contextId;
    }
}
