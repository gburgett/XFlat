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
package org.xflatdb.xflat.db;

import java.io.File;
import java.net.URI;
import java.util.Map;
import org.xflatdb.xflat.Database;
import org.xflatdb.xflat.DatabaseBuilder.DatabaseProvider;
import org.xflatdb.xflat.DatabaseConfig;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatConfigurationException;

/**
 * A DatabaseProvider which activates instances of {@link XFlatDatabase}.
 * 
 * @author gordon
 */
class XFlatDatabaseProvider implements DatabaseProvider<XFlatDatabase> {

    @Override
    public XFlatDatabase construct(URI uri, DatabaseConfig config, Map<String, TableConfig> tableConfigs, Map<String, Object> engineReqs) {
        if(!"file".equals(uri.getScheme())){
            throw new XFlatConfigurationException("XFlatDatabase can only manage local directories");
        }
        
        XFlatDatabase ret = new XFlatDatabase(new File(uri));
        
        ret.setConfig(config);
        for(Map.Entry<String, TableConfig> cfg : tableConfigs.entrySet()){
            ret.configureTable(cfg.getKey(), cfg.getValue());
        }
        
        ret.initialize();
        
        return ret;
    }
    
}
