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
package org.xflatdb.xflat;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A builder which is used to configure the database before initializing it.
 */
public class DatabaseBuilder<T extends Database>{

    private URI location; 

    private Map<String, Object> requirements;

    private DatabaseConfig config;

    private Map<String, TableConfig> tableConfigs;
    
    private DatabaseProvider<T> provider;

    public DatabaseBuilder(URI location, DatabaseProvider<T> provider){
        this.location = location;
        this.requirements = Collections.EMPTY_MAP;
        this.config = DatabaseConfig.DEFAULT;
        this.tableConfigs = Collections.EMPTY_MAP;
        this.provider = provider;
    }

    private DatabaseBuilder(DatabaseBuilder other){
        this.location = other.location;
        this.requirements = other.requirements;
        this.config = other.config;
        this.tableConfigs = other.tableConfigs;
        this.provider = other.provider;
    }

    /**
     * Adds requirements of the database to be instantiated.
     * @param requirements The requirements to use
     * @return A new DatabaseBuilder with the given requirements
     */
    public DatabaseBuilder<T> withRequirements(Map<String, Object> requirements){
        DatabaseBuilder ret = new DatabaseBuilder(this);
        Map<String, Object> reqs = new HashMap<>(ret.requirements);
        reqs.putAll(requirements);
        ret.requirements = Collections.unmodifiableMap(reqs);
        return ret;
    }

    /**
     * Adds requirements of the database to be instantiated.  The requirements
     * are given as keys only, their values are assumed to be boolean true.
     * <p/>
     * This is a convenience for withRequirement(key, true)
     * @param requirements The set of requirements which should be "true"
     * @return A new DatabaseBuilder with the given requirements
     */
    public DatabaseBuilder<T> withRequirements(String... requirements){
        DatabaseBuilder ret = new DatabaseBuilder(this);
        Map<String, Object> reqs = new HashMap<>(ret.requirements);
        for(String s : requirements){
            reqs.put(s, true);
        }
        ret.requirements = Collections.unmodifiableMap(reqs);
        return ret;
    }

    /**
     * Adds a requirement of the database to be instantiated.
     * @param name the named key of the requirement.
     * @param value the value of the requirement.
     * @return A new DatabaseBuilder with the given requirement
     */
    public DatabaseBuilder<T> withRequirement(String name, Object value){
        DatabaseBuilder ret = new DatabaseBuilder(this);
        Map<String, Object> reqs = new HashMap<>(ret.requirements);
        reqs.put(name, value);
        ret.requirements = Collections.unmodifiableMap(reqs);
        return ret;
    }

    /**
     * Sets the database configuration to use with the instantiated database.
     * This overwrites any existing configuration.
     * @param config The configuration to use.
     * @return A new DatabaseBuilder with the given configuration.
     */
    public DatabaseBuilder<T> withDatabaseConfig(DatabaseConfig config){
        DatabaseBuilder ret = new DatabaseBuilder(this);
        ret.config = config;
        return ret;
    }

    /**
     * Sets the table configuration to use with a given named table.
     * This overwrites any previous configuration for the given table.
     * @param tableName The table to configure.
     * @param config The configuration for the table.
     * @return A new DatabaseBuilder with the given configuration.
     */
    public DatabaseBuilder<T> withTableConfig(String tableName, TableConfig config){
        DatabaseBuilder ret = new DatabaseBuilder(this);
        Map<String, TableConfig> cfgs = new HashMap<>(ret.tableConfigs);
        cfgs.put(tableName, config);
        ret.tableConfigs = Collections.unmodifiableMap(cfgs);
        return ret;
    }
    
    /**
     * Creates a new database with the current configuration.  The database
     * must satisfy the current requirements.
     * @return A new instance of a database implementation satisfying the requirements.
     * @throws XFlatConfigurationException if no database could be found satisfying
     * the requirements, or if an error occurred when constructing the database.
     */
    public T create() throws XFlatConfigurationException{
        try{
            return provider.construct(location, config, tableConfigs, requirements);
        }
        catch(XFlatConfigurationException ex){
            throw ex;
        }
        catch(Exception ex){
            throw new XFlatConfigurationException("Unable to initialize database", ex);
        }
    }

    public interface DatabaseProvider<T extends Database>{
        public T construct(URI uri, DatabaseConfig config, Map<String, TableConfig> tableConfigs, Map<String, Object> engineReqs);
    }
}

