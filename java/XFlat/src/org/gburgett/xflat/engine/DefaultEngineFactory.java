/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import org.gburgett.xflat.TableConfig;
import java.io.File;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineFactory;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.util.XPathExpressionEqualityMatcher;
import org.hamcrest.Matcher;
import org.jdom2.xpath.XPathExpression;

/**
 * The default engine factory, which chooses from among the engines available to 
 * the core of XFlat.
 * @author Gordon
 */
public class DefaultEngineFactory implements EngineFactory {

    private Matcher idPropertyMatcher = new XPathExpressionEqualityMatcher(XpathQuery.Id);
    
    @Override
    public EngineBase newEngine(File file, String tableName, TableConfig config) {
        if(config.getShardsetConfig() != null){
            if(idPropertyMatcher.matches(config.getShardsetConfig().getShardPropertySelector())){
                return new IdShardedEngine(file, tableName, config.getShardsetConfig());
            }
            throw new XflatException("Tables sharded on other values than Id are not supported");
        }
        
        return new CachedDocumentEngine(file, tableName);
    }
    
}
