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

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The DatabaseFactory is a singleton factory which locates and initializes
 * databases.  It uses the Java {@link ServiceLoader} to discover databases
 * on the classpath, and instantiates the first one it finds which matches
 * the requirements.
 * <p/>
 * The requirements consist of a named key and an object value.
 * Some examples may include:<br/>
 * <pre>
 * "transactional" : true
 * "replicatesTo" : "../backup1dir, ../backup2dir"
 * "customRequirement" : 42
 * </pre>
 * The instantiated database must satisfy all the requirements in the requirements
 * map.
 * <p/>
 * In addition to the requirements, the database must be able to manage the 
 * provided URI.  If the URI represents a directory, the database must manage the
 * directory (this is the default implementation).  An implementation of a database
 * could also manage a remote database, in which case the URI would represent
 * a remote database connection.
 * <p/>
 * If no databases are found which satisfy the requirements, an {@link XFlatConfigurationException}
 * is thrown.
 * <p/>
 * In order to instantiate a database via this factory, a {@link DatabaseProvider}
 * must be registered in the Jar's META-INF/services directory, as specified by the
 * {@link ServiceLoader} documentation.  This provider will be queried to see if it
 * supports the requirements, and if so, it will be asked to construct an instance
 * of the database.<br/>
 * This architecture makes it simple to use alternate database implementations:
 * simply include the Jar containing the database implementation, and then 
 * build the database with some requirements that it supports.
 * @author gordon
 */
public class DatabaseFactory {
    
    private ServiceLoader<DatabaseProvider> dbProviderLoader =
            ServiceLoader.load(DatabaseProvider.class);
    
    private DatabaseFactory(){
        
    }
    
    /**
     * Creates a local database managing the given directory, with no requirements.
     * The first database on the classpath which can manage a local directory is used.
     * @param directory The local directory to be managed as a database.
     * @return A new database instance managing the directory.
     */
    public Database createDatabase(File directory){
        return buildDatabase(directory).create();
    }
    
    /**
     * Builds a local database managing the given directory.  The builder returned
     * can be used to configure the database, and set requirements.
     * @param directory The local directory to be managed as a database.
     * @return A builder which can fluently set configuration information and requirements.
     */
    public DatabaseBuilder buildDatabase(File directory){
        return new DatabaseBuilder(directory.toURI());
    }
    
    /**
     * Creates a database managing the resource, with no requirements.
     * The resource can be any valid URI, which can be managed by a database
     * implementation on the classpath.
     * <p/>
     * For example, if you wish to use an implementation that calls out to
     * a remote database, then you might use a "HTTP://" URI and include
     * a "RemoteHttpXFlatDatabase" on the classpath.
     * @param location The resource to be managed by the database.
     * @return A new instance of the database.
     */
    public Database createDatabase(URI location){
        return buildDatabase(location).create();
    }
    
    /**
     * Builds a database managing the resource.  The builder returned
     * can be used to configure the database, and set requirements.
     * The resource can be any valid URI, which can be managed by a database
     * implementation on the classpath.
     * <p/>
     * For example, if you wish to use an implementation that calls out to
     * a remote database, then you might use a "HTTP://" URI and include
     * a "RemoteHttpXFlatDatabase" on the classpath.
     * @param location The resource to be managed by the database.
     * @return A builder which can fluently set configuration information and requirements.
     */
    public DatabaseBuilder buildDatabase(URI location){
        return new DatabaseBuilder(location);
    }
    
    
    private DatabaseProvider locateDatabase(URI location, Map<String, Object> requirements){
        for(DatabaseProvider provider : dbProviderLoader){
            if(provider.canSatisfy(location, requirements)){
                return provider;
            }
        }       
        
        throw new XFlatConfigurationException("Unable to load a database satisfying the requirements for the URI " + location);
    }
    
    /**
     * The singleton instance of the database factory.
     */
    public static final DatabaseFactory instance = new DatabaseFactory();
    
    /**
     * A builder which is used to configure the database before initializing it.
     */
    public class DatabaseBuilder{
        
        private URI location; 
        
        private Map<String, Object> requirements;
        
        private DatabaseConfig config;
        
        private Map<String, TableConfig> tableConfigs;
        
        DatabaseBuilder(URI location){
            this.location = location;
            this.requirements = Collections.EMPTY_MAP;
            this.config = DatabaseConfig.DEFAULT;
            this.tableConfigs = Collections.EMPTY_MAP;
        }
        
        private DatabaseBuilder(DatabaseBuilder other){
            this.location = other.location;
            this.requirements = other.requirements;
            this.config = other.config;
            this.tableConfigs = other.tableConfigs;
        }
        
        /**
         * Adds requirements of the database to be instantiated.
         * @param requirements The requirements to use
         * @return A new DatabaseBuilder with the given requirements
         */
        public DatabaseBuilder withRequirements(Map<String, Object> requirements){
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
        public DatabaseBuilder withRequirements(String... requirements){
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
        public DatabaseBuilder withRequirement(String name, Object value){
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
        public DatabaseBuilder withDatabaseConfig(DatabaseConfig config){
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
        public DatabaseBuilder withTableConfig(String tableName, TableConfig config){
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
        public Database create(){
            
            DatabaseProvider provider = locateDatabase(location, requirements);
            
            try{
                return provider.construct(location, config, tableConfigs, requirements);
            }
            catch(Exception ex){
                throw new XFlatConfigurationException("Error creating a database for URI " + location, ex);
            }
        }
    }
}
