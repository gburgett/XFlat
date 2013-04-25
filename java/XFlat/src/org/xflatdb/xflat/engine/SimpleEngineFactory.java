/* 
*	Copyright 2013 Gordon Burgett and individual contributors
*
*	Licensed under the Apache License, Version 2.0 (the "License");
*	you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*	Unless required by applicable law or agreed to in writing, software
*	distributed under the License is distributed on an "AS IS" BASIS,
*	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*	See the License for the specific language governing permissions and
*	limitations under the License.
*/
package org.xflatdb.xflat.engine;

import java.io.File;
import org.hamcrest.Matcher;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.util.XPathExpressionEqualityMatcher;

/**
 * The default engine factory, which chooses from among the engines packaged with
 * the core XFlat Jar.
 * @author Gordon
 */
public class SimpleEngineFactory implements EngineFactory {

    private Matcher idPropertyMatcher = new XPathExpressionEqualityMatcher(XPathQuery.Id);
    
    /**
     * Creates either an {@link IdShardedEngine} or a {@link CachedDocumentEngine}
     * based on whether the table is sharded.
     * @param file The file for which an engine.
     * @param tableName The name of the table we are getting an engine for.
     * @param config The configuration of the table we are getting an engine for.
     * @return A new engine implementation.
     */
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
