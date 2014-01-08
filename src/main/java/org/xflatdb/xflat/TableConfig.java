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

import java.util.Objects;
import org.jdom2.Element;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.db.IdGenerator;

/**
 * Represents the configuration of one table. 
 * Specifies indexes, an ID generator, sharding, etc.
 * 
 * This class is immutable: all set methods return new objects.
 * @author gordon
 */
public class TableConfig {
 
    /**
     * Creates a TableConfig with the default values.
     */
    public TableConfig(){
        this.idGenerator = null;
        this.inactivityShutdownMs = 3000;
        this.shardsetConfig = null;
    }
    
    private TableConfig(TableConfig other){
        this.idGenerator = other.idGenerator;
        this.inactivityShutdownMs = other.inactivityShutdownMs;
        this.shardsetConfig = other.shardsetConfig;
    }
    
    private Class<? extends IdGenerator> idGenerator;
    /**
     * Gets the ID generator class used for this table.
     * @see #withIdGenerator(java.lang.Class)
     */
    public Class<? extends IdGenerator> getIdGenerator(){
        return this.idGenerator;
    }
    /**
     * Sets the ID generator class used for this table.  Overrides the default
     * ID generation strategy to specify a specific ID generator.
     * 
     * A Null value indicates that the Database should select the appropriate
     * generator based on its IdGenerator Strategy.
     * @param idGenerator The class of ID generator to use.
     * @return A new instance with the idGenerator property set.
     */
    public TableConfig withIdGenerator(Class<? extends IdGenerator> idGenerator){
        TableConfig ret = new TableConfig(this);
        ret.idGenerator = idGenerator;
        return ret;
    }
    
    private long inactivityShutdownMs;
    /**
     * Gets the inactivity shutdown duration.  A table that has been inactive for
     * this many milliseconds will be spun down to conserve resources, and spun
     * up again when next needed.
     * @return The shutdown duration in milliseconds.
     */
    public long getInactivityShutdownMs() {
        return this.inactivityShutdownMs;
    }
    /** @see #getInactivityShutdownMs()  */
    public TableConfig withInactivityShutdownMs(long inactivityShutdownMs) {
        TableConfig ret = new TableConfig(this);
        ret.inactivityShutdownMs = inactivityShutdownMs;
        return ret;
    }
    
    private ShardsetConfig<?> shardsetConfig;
    public ShardsetConfig<?> getShardsetConfig(){
        return shardsetConfig;
    }
    /**
     * Sets that this table will be sharded according to the given sharding configuration.
     * Sharding is when the table splits its data up into multiple files to reduce the amount
     * in-memory at any time.
     * @param config The sharding configuration
     * @return A new table config with this sharding configuration.
     */
    public TableConfig sharded(ShardsetConfig<?> config){
        TableConfig ret = new TableConfig(this);
        ret.shardsetConfig = config;
        return ret;
    }
    
    //TODO: future configuration options
    
    /**
     * Gets the default table config.  Equivalent to instantiating
     * a new instance, but this is a singleton.
     */
    public static TableConfig DEFAULT = new TableConfig();
    

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.idGenerator);
        hash = 29 * hash + (int) (this.inactivityShutdownMs ^ (this.inactivityShutdownMs >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TableConfig other = (TableConfig) obj;
        if (!Objects.equals(this.idGenerator, other.idGenerator)) {
            return false;
        }
        if (this.inactivityShutdownMs != other.inactivityShutdownMs) {
            return false;
        }
        return true;
    }

    
    public static Converter<TableConfig, Element> ToElementConverter = new Converter<TableConfig, Element>(){
        @Override
        public Element convert(TableConfig source) throws ConversionException {
            Element ret = new Element("config", XFlatConstants.xFlatNs);
            if(source.idGenerator != null)
                ret.setAttribute("idGenerator", source.idGenerator.getName(), XFlatConstants.xFlatNs);
            return ret;
        }
    };
    
    public static Converter<Element, TableConfig> FromElementConverter = new Converter<Element, TableConfig>(){
        @Override
        public TableConfig convert(Element source) throws ConversionException {
            if(!"config".equals(source.getName()) || !XFlatConstants.xFlatNs.equals(source.getNamespace())){
                throw new ConversionException("Expected element named config in namespace " + XFlatConstants.xFlatNs);
            }
            
            TableConfig ret = new TableConfig();
            String idGenerator = source.getAttributeValue("idGenerator", XFlatConstants.xFlatNs);
            if(idGenerator != null){
                try {
                    Class<?> cl = this.getClass().getClassLoader().loadClass(idGenerator);
                    ret.idGenerator = (Class<? extends IdGenerator>)cl;
                } catch (ClassNotFoundException ex) {
                    throw new ConversionException("Could not load ID generator", ex);
                }
            }
            return ret;
        }
    };
}
