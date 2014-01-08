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

    private DatabaseConfig config;

    private Map<String, TableConfig> tableConfigs;
    
    private DatabaseProvider<T> provider;

    public DatabaseBuilder(URI location, DatabaseProvider<T> provider){
        this.location = location;
        this.config = DatabaseConfig.DEFAULT;
        this.tableConfigs = Collections.EMPTY_MAP;
        this.provider = provider;
    }

    private DatabaseBuilder(DatabaseBuilder other){
        this.location = other.location;
        this.config = other.config;
        this.tableConfigs = other.tableConfigs;
        this.provider = other.provider;
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
            return provider.construct(location, config, tableConfigs);
        }
        catch(XFlatConfigurationException ex){
            throw ex;
        }
        catch(Exception ex){
            throw new XFlatConfigurationException("Unable to initialize database", ex);
        }
    }

    /**
     * Implementations of this interface construct a database with the appropriate
     * configurations, and initialize them.
     * @param <T> The type of database to construct.
     */
    public interface DatabaseProvider<T extends Database>{
        /**
         * Constructs a database given a location for the database and appropriate
         * configurations.
         * @param uri The location to be managed by this database.
         * @param config The configuration to be used for this database.
         * @param tableConfigs A set of configurations for individual tables.
         * @return A newly constructed and initialized database, ready for use.
         * @throws XFlatConfigurationException if any of the configuration elements
         * are invalid.
         */
        public T construct(URI uri, DatabaseConfig config, Map<String, TableConfig> tableConfigs);
    }
}

