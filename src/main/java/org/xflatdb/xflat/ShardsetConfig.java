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

import org.xflatdb.xflat.query.IntervalProvider;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.util.XPathExpressionEqualityMatcher;

/**
 * Represents the configuration for a sharded table.  A sharded table is one which
 * splits its data up among several files.
 * @author Gordon
 */
public class ShardsetConfig<T> {
    
    private ShardsetConfig(){
        
    }
    
    private ShardsetConfig(ShardsetConfig other){
        this.shardPropertyClass  = other.shardPropertyClass;
        this.shardPropertySelector = other.shardPropertySelector;
        this.intervalProvider = other.intervalProvider;
        
    }
    
    private Class<T> shardPropertyClass;
    /**
     * Gets the class to which the string value of the property selected by the
     * shard property selector must be convertible.  The converted value is fed
     * to the Interval Provider to determine the correct shard for a data row.
     */
    public Class<T> getShardPropertyClass(){
        return shardPropertyClass;
    }
    
    private XPathExpression<?> shardPropertySelector;
    /**
     * Gets the XPath expression that selects a property of a row which determines
     * the shard where the row will be placed.
     */
    public XPathExpression<?> getShardPropertySelector(){
        return shardPropertySelector;
    }
    
    private IntervalProvider<T> intervalProvider;
    /**
     * Gets the interval provider used to choose which shard a row will be placed in.
     */
    public IntervalProvider<T> getIntervalProvider(){
        return intervalProvider;
    }
    
    private boolean isId = false;
    /**
     * Gets a flag indicating whether the shard property selector is the {@link org.xflatdb.xflat.query.XPathQuery#Id} expression.
     */
    public boolean isShardedById(){
        return this.isId;
    }
    
    /**
     * Creates a ShardsetConfig describing a table sharded by ID.  The shard selector
     * will use the {@link XPathQuery#Id} expression.
     * @param <U> The generic type of the ID property; all objects in the table must have the same property.
     * @param idClass The class of the property selected by the xpath expression.
     * @param idIntervalProvider A IntervalProvider that determines the static ranges for the
     * property, each range will have its own file.
     * @return A new shardset config.
     */
    public static <U> ShardsetConfig<U> byId(Class<U> idClass, IntervalProvider<U> idIntervalProvider){
        if(idClass == null){
            throw new IllegalArgumentException("idClass cannot be null");
        }
        if(idIntervalProvider == null){
            throw new IllegalArgumentException("idIntervalProvider cannot be null");
        }
        
        ShardsetConfig<U> ret =  new ShardsetConfig<>();
        ret.shardPropertySelector = XPathQuery.Id;
        ret.shardPropertyClass = idClass;
        ret.intervalProvider = idIntervalProvider;
        ret.isId = true;
        
        return ret;
    }
        
    
    /**
     * Creates a ShardsetConfig with the minimum configuration necessary for static-range sharding.
     * The remainder can be left default or set as desired.
     * @param <U> The generic type of the ID property; all objects in the table must have the same property.
     * @param xpathProperty An XPath expression selecting a property of the data to shard on.
     * @param propertyClass The class of the property selected by the xpath expression.
     * @param intervalProvider A IntervalProvider that determines the static ranges for the
     * property, each range will have its own file.
     * @return A new shardset config.
     */
    public static <U> ShardsetConfig<U> by(String xpathProperty, Class<U> propertyClass, IntervalProvider<U> intervalProvider){
        return by(XPathFactory.instance().compile(xpathProperty), propertyClass, intervalProvider);
    }
    
    /**
     * Creates a ShardsetConfig with the minimum configuration necessary for static-range sharding.
     * The remainder can be left default or set as desired.
     * @param <U> The generic type of the ID property; all objects in the table must have the same property.
     * @param xpathProperty An XPath expression selecting a property of the data to shard on.
     * @param propertyClass The class of the property selected by the xpath expression.
     * @param intervalProvider An {@link IntervalProvider} that determines the static ranges for the
     * property, each range will have its own file.
     * @return A new shardset config.
     */
    public static <U> ShardsetConfig<U> by(XPathExpression<?> xpathProperty, Class<U> propertyClass, IntervalProvider<U> intervalProvider){
        if(xpathProperty == null){
            throw new IllegalArgumentException("xpathProperty cannot be null");
        }
        if(propertyClass == null){
            throw new IllegalArgumentException("propertyClass cannot be null");
        }
        if(intervalProvider == null){
            throw new IllegalArgumentException("intervalProvider cannot be null");
        }
        
        ShardsetConfig<U> ret =  new ShardsetConfig<>();
        ret.shardPropertySelector = xpathProperty;
        ret.shardPropertyClass = propertyClass;
        ret.intervalProvider = intervalProvider;
        
        if(XPathExpressionEqualityMatcher.equals(xpathProperty, XPathQuery.Id)){
            ret.isId = true;
        }
        
        return ret;
    }
}
