/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat;

import org.xflatdb.xflat.query.IntervalProvider;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

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
    public Class<T> getShardPropertyClass(){
        return shardPropertyClass;
    }
    
    private XPathExpression<?> shardPropertySelector;
    public XPathExpression<?> getShardPropertySelector(){
        return shardPropertySelector;
    }
    
    private IntervalProvider<T> intervalProvider;
    public IntervalProvider<T> getIntervalProvider(){
        return intervalProvider;
    }
    
        
    
    /**
     * Creates a ShardsetConfig with the minimum configuration necessary for static-range sharding.
     * The remainder can be left default or set as desired.
     * @param <U>
     * @param xpathProperty An XPath expression selecting a property of the data to shard on.
     * @param propertyClass The class of the property selected by the xpath expression.
     * @param intervalProvider A RangeProvider that determines the static ranges for the
     * property, each range will have its own file.
     * @return A new shardset config.
     */
    public static <U> ShardsetConfig<U> create(String xpathProperty, Class<U> propertyClass, IntervalProvider<U> intervalProvider){
        return create(XPathFactory.instance().compile(xpathProperty), propertyClass, intervalProvider);
    }
    
    /**
     * Creates a ShardsetConfig with the minimum configuration necessary for static-range sharding.
     * The remainder can be left default or set as desired.
     * @param <U>
     * @param xpathProperty An XPath expression selecting a property of the data to shard on.
     * @param propertyClass The class of the property selected by the xpath expression.
     * @param rangeProvider A RangeProvider that determines the static ranges for the
     * property, each range will have its own file.
     * @return A new shardset config.
     */
    public static <U> ShardsetConfig<U> create(XPathExpression<?> xpathProperty, Class<U> propertyClass, IntervalProvider<U> rangeProvider){
        if(xpathProperty == null){
            throw new IllegalArgumentException("xpathProperty cannot be null");
        }
        if(propertyClass == null){
            throw new IllegalArgumentException("propertyClass cannot be null");
        }
        if(rangeProvider == null){
            throw new IllegalArgumentException("rangeProvider cannot be null");
        }
        
        ShardsetConfig<U> ret =  new ShardsetConfig<>();
        ret.shardPropertySelector = xpathProperty;
        ret.shardPropertyClass = propertyClass;
        ret.intervalProvider = rangeProvider;
        
        return ret;
    }
}
