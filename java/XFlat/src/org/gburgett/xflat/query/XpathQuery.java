/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionService;
import org.hamcrest.Matchers;
import org.hamcrest.StringDescription;

/**
 *
 * @author Gordon
 */
public class XpathQuery {

    private XPath selector;
    public XPath getSelector(){
        return selector;
    }

    private Matcher<Element> rowMatcher;
    public Matcher<Element> getRowMatcher(){
        return rowMatcher;
    }
    
    private Object value;
    public Object getValue(){
        return value;
    }
    
    private Class<?> valueType;
    public Class<?> getValueType(){
        return valueType;
    }
    
    private QueryType queryType;
    public QueryType getQueryType(){
        return queryType;
    }

    private List<XpathQuery> queryChain;
    
    private ConversionService conversionService;
    /**
     * Sets the conversion service for the entire query chain.  Necessary for
     * matchers that match against non-JDOM types
     * @param service 
     */
    public void setConversionService(ConversionService service){
        this.conversionService = service;
        
        if(this.queryChain != null){
            for(XpathQuery q : this.queryChain){
                q.setConversionService(service);
            }
        }
    }

    private XpathQuery(XPath selector, QueryType type, Object value, Class<?> valueType,
            Matcher<?> valueMatcher)
    {
        this.selector = selector;
        this.rowMatcher = new ValueMatcher(selector, valueType, valueMatcher);
        this.queryType = type;
        this.value = value;
    }

    
    
    private XpathQuery(QueryType type, Matcher<Element> rowMatcher, XpathQuery... queries){
        this.queryType = type;
        this.rowMatcher = rowMatcher;
        this.queryChain = new ArrayList<>();
        this.queryChain.addAll(Arrays.asList(queries));
    }
    
    //<editor-fold desc="builders">

    /**
     * Creates an XPath query that matches the single element at the selector to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be equal to.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery eq(XPath selector, U object){
        Matcher<U> eq = Matchers.equalTo(object);
        
        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        return new XpathQuery(selector, QueryType.EQ, object, valueType, eq);
    }
    /**
     * Creates an XPath query that matches when the single element at the selector
     * does not exist or is not equal to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should not be equal to.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery ne(XPath selector, U object){
        Matcher<U> ne = Matchers.not(Matchers.equalTo(object));
        
        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        return new XpathQuery(selector, QueryType.NE, object, valueType, ne);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery lt(XPath selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
                
        Matcher<U> lt = Matchers.lessThan(object);
        return new XpathQuery(selector, QueryType.LT, object, valueType, lt);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery lte(XPath selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> lte = Matchers.lessThanOrEqualTo(object);
        return new XpathQuery(selector, QueryType.LTE, object, valueType, lte);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gt(XPath selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> gt = Matchers.greaterThan(object);
        return new XpathQuery(selector, QueryType.GT, object, valueType, gt);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gte(XPath selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> gte = Matchers.greaterThanOrEqualTo(object);
        return new XpathQuery(selector, QueryType.GTE, object, valueType, gte);
    }

    /**
     * Create an XPath query that matches when all of the provided queries match.
     * @param queries The queries that all must match.
     * @return  an XpathQuery object
     */
    public static XpathQuery and(XpathQuery... queries){
        List<Matcher<? super Element>> ms = new ArrayList<>();

        for(XpathQuery q : queries){
            ms.add(q.getRowMatcher());
        }

        Matcher<Element> ret = Matchers.allOf(ms);
        return new XpathQuery(QueryType.AND, ret, queries);
    }

    /**
     * Create an XpathQuery that matches when any of the provided queries match.
     * @param queries the queries, one of which must match.
     * @return an XpathQuery object.
     */
    public static XpathQuery or(XpathQuery... queries){
        List<Matcher<? super Element>> ms = new ArrayList<>();

        for(XpathQuery q : queries){
            ms.add(q.getRowMatcher());
        }

        Matcher<Element> ret = Matchers.anyOf(ms);
        return new XpathQuery(QueryType.OR, ret, queries);
    }

    /**
     * Create an XpathQuery that matches when the given matcher matches the value
     * selected by the Xpath selector.
     * @param <U> The type of the value expected at the end of the selector.
     * @param selector The Xpath expression that selects a value in a row.
     * @param matcher The matcher that decides whether this row is a match.
     * @param clazz The type of the value expected at the end of the selector.
     * @return an XpathQuery object.
     */
    public static <U> XpathQuery matches(XPath selector, Matcher<U> matcher, Class<U> clazz){
        return new XpathQuery(selector, QueryType.MATCHES, null, clazz, matcher);
    }

    /**
     * functions the same as ne(selector, null), but does not invoke the conversion
     * service.
     * @param selector The selector to test whether it exists.
     * @return An XpathQuery object.
     */
    public static XpathQuery exists(XPath selector){
        Matcher<Object> m = Matchers.notNullValue();
        
        return new XpathQuery(selector, QueryType.EXISTS, null, Object.class, m);
    }

    //</editor-fold>

    /**
     * The type of the query.
     * Can be used by engines to inspect the query in order to generate an
     * execution plan.
     */
    public enum QueryType {
        EQ,
        NE,
        LT,
        LTE,
        GT,
        GTE,
        AND,
        OR,
        EXISTS,
        MATCHES
    }
    
    /**
     * The Hamcrest Matcher that matches a row by evaluating the Xpath expression,
     * and invoking the matcher for the result of the Xpath expression.
     * @param <T> The type of the expected value at the end of the Xpath expression.
     */
    private class ValueMatcher<T> extends TypeSafeMatcher<Element>{

        private XPath selector;
        private Class<T> expectedType;
        private Matcher<T> subMatcher;

        public ValueMatcher(XPath selector, Class<T> expectedType, Matcher<T> subMatcher){
            super(Element.class);

            this.selector = selector;
            this.expectedType = expectedType;
            this.subMatcher = subMatcher;
        }

        @Override
        protected boolean matchesSafely(Element item) {
            Object selected;
            try {
                 selected = selector.selectSingleNode(item);
            } catch (JDOMException ex) {
                Log log = LogFactory.getLog(XpathQuery.class);
                if(log.isTraceEnabled()){
                    StringDescription desc = new StringDescription();
                    this.describeTo(desc);
                    log.trace("issue matching \"" + desc.toString() + "\" due to exception", ex);
                }

                //treat this as null
                selected = null;
            }
            
            if(selected == null){
                if(expectedType != null && conversionService != null && 
                        conversionService.canConvert(null, expectedType)){
                    return subMatcher.matches(conversionService.convert(null, expectedType));
                }

                return subMatcher.matches(null);
            }
            
            if(expectedType != null){
                if(!expectedType.isAssignableFrom(selected.getClass())){
                    //need to convert
                    if(conversionService != null && conversionService.canConvert(selected.getClass(), expectedType)){
                        selected = conversionService.convert(selected, expectedType);
                    }
                    else{
                        throw new XflatException("XPath " + selector.getXPath() + 
                                " matched to non-convertible type " + selected.getClass() +
                                ", expected " + expectedType);
                    }
                }
            }

            return subMatcher.matches(selected);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("value at ")
                    .appendText(this.selector.getXPath())
                    .appendText(" that ")
                    .appendDescriptionOf(this.subMatcher);
        }
    }
}
