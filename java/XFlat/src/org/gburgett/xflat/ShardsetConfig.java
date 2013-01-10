/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import org.gburgett.xflat.range.RangeProvider;
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
        this.rangeProvider = other.rangeProvider;
    }
    
    private Class<T> shardPropertyClass;
    public Class<T> getShardPropertyClass(){
        return shardPropertyClass;
    }
    
    private XPathExpression<Object> shardPropertySelector;
    public XPathExpression<Object> getShardPropertySelector(){
        return shardPropertySelector;
    }
    
    private RangeProvider<T> rangeProvider;
    public RangeProvider<T> getRangeProvider(){
        return rangeProvider;
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
    public static <U> ShardsetConfig<U> create(String xpathProperty, Class<U> propertyClass, RangeProvider<U> rangeProvider){
        return create(XPathFactory.instance().compile(xpathProperty), propertyClass, rangeProvider);
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
    public static <U> ShardsetConfig<U> create(XPathExpression<Object> xpathProperty, Class<U> propertyClass, RangeProvider<U> rangeProvider){
        ShardsetConfig<U> ret =  new ShardsetConfig<>();
        ret.shardPropertySelector = xpathProperty;
        ret.shardPropertyClass = propertyClass;
        ret.rangeProvider = rangeProvider;
        
        
        
        return ret;
    }
}
