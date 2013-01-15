/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

/**
 *
 * @author Gordon
 */
public abstract class ShardedEngineTestsBase<TEngine extends ShardedEngineBase> extends EngineTestsBase<TEngine> {
    
    
    protected void setMetadataFactory(TEngine engine, TableMetadataFactory factory){
        engine.setMetadataFactory(factory);
    }
}
