/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import org.xflatdb.xflat.db.TableMetadataFactory;
import org.xflatdb.xflat.db.ShardedEngineBase;

/**
 *
 * @author Gordon
 */
public abstract class ShardedEngineTestsBase<TEngine extends ShardedEngineBase> extends EngineTestsBase<TEngine> {
    
    
    protected void setMetadataFactory(TEngine engine, TableMetadataFactory factory){
        engine.setMetadataFactory(factory);
    }
}
