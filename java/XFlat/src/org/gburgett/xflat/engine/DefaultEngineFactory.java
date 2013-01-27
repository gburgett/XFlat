/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import org.gburgett.xflat.TableConfig;
import org.gburgett.xflat.XFlatException;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineFactory;
import org.gburgett.xflat.query.XPathQuery;
import org.gburgett.xflat.util.XPathExpressionEqualityMatcher;
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
