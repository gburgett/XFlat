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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xflatdb.xflat.Database;
import org.xflatdb.xflat.DatabaseConfig;
import org.xflatdb.xflat.DatabaseProvider;
import org.xflatdb.xflat.TableConfig;

/**
 * A DatabaseProvider which activates instances of {@link LocalTransactionalDatabase}.
 * 
 * @author gordon
 */
public class LocalTransactionalDatabaseProvider implements DatabaseProvider {

    @Override
    public boolean canSatisfy(URI uri, Map<String, Object> requirements) {
        //only local files
        if(!"file".equals(uri.getScheme())){
            return false;
        }
        
        for(Map.Entry<String, Object> entry : requirements.entrySet()){
            if("transactional".equalsIgnoreCase(entry.getKey())){
                if(!checkTransactional(entry.getValue().toString())){
                    return false;
                }
            }
            else if("local".equalsIgnoreCase(entry.getKey())){
                if(!Boolean.valueOf(entry.getValue().toString()))
                    return false;
            }
            else if("threadsafe".equalsIgnoreCase(entry.getKey())){
                if(!Boolean.valueOf(entry.getValue().toString()))
                    return false;
            }
            else{
                //an unknown requirement
                return false;
            }
        }
        
        //satisfied all requirements
        return true;
    }
    
    private Pattern acidRegex = Pattern.compile("^[ACIDacid,.\\s]*$");
    private boolean checkTransactional(String value){
        if(Boolean.valueOf(value))
            return true;
        
        //could be a string specifying "ACID" properties,
        //in which case we satisfy all of A, C, I, and D.
        Matcher m = acidRegex.matcher(value);
        return m.find();
    }

    @Override
    public Database construct(URI uri, DatabaseConfig config, Map<String, TableConfig> tableConfigs, Map<String, Object> requirements) {
        LocalTransactionalDatabase ret = new LocalTransactionalDatabase(new File(uri));
        
        ret.setConfig(config);
        for(Map.Entry<String, TableConfig> cfg : tableConfigs.entrySet()){
            ret.configureTable(cfg.getKey(), cfg.getValue());
        }
        
        ret.initialize();
        return ret;
    }
    
}
