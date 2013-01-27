/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.engine;

import java.io.File;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.util.XPathExpressionEqualityMatcher;
import org.hamcrest.Matcher;

/**
 * The default engine factory, which chooses from among the engines available to 
 * the core of XFlat.
 * @author Gordon
 */
public class DefaultEngineFactory implements EngineFactory {

    private Matcher idPropertyMatcher = new XPathExpressionEqualityMatcher(XPathQuery.Id);
    
    @Override
    public EngineBase newEngine(File file, String tableName, TableConfig config) {
        if(config.getShardsetConfig() != null){
            if(idPropertyMatcher.matches(config.getShardsetConfig().getShardPropertySelector())){
                return new IdShardedEngine(file, tableName, config.getShardsetConfig());
            }
            throw new XFlatException("Tables sharded on other values than Id are not supported");
        }
        
        return new CachedDocumentEngine(file, tableName);
    }
    
}
