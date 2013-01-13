/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.db.XFlatDatabase;
import org.hamcrest.Matchers;
import org.hamcrest.SelfDescribing;
import org.hamcrest.StringDescription;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.AttributeFilter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

/**
 *
 * @author Gordon
 */
public class XpathQuery {

    
    
    private XPathExpression<?> selector;
    public XPathExpression<?> getSelector(){
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

    private XpathQuery(XPathExpression<?> selector, QueryType type, Object value, Class<?> valueType,
            Matcher<?> valueMatcher)
    {
        this.selector = selector;
        this.rowMatcher = new ValueMatcher(selector, valueType, valueMatcher);
        this.queryType = type;
        this.value = value;
        this.valueType = valueType;
    }

    
    
    private XpathQuery(QueryType type, Matcher<Element> rowMatcher, XpathQuery... queries){
        this.queryType = type;
        this.rowMatcher = rowMatcher;
        this.queryChain = new ArrayList<>();
        this.queryChain.addAll(Arrays.asList(queries));
    }
    
    private XpathQuery(){
    }
    
    private static class XPathExpressionEqualsMatcher<U> extends TypeSafeMatcher<XPathExpression<U>>{
        private final XPathExpression<U> toMatch;
        
        public XPathExpressionEqualsMatcher(XPathExpression<U> toMatch){
            this.toMatch = toMatch;
        }
        
        @Override
        protected boolean matchesSafely(XPathExpression<U> item) {
            if(toMatch == null){
                return item == null; 
            }
            
            if(toMatch.getNamespaces().length != item.getNamespaces().length){
                return false;
            }
            
            String itemExp = item.getExpression();
            
            //match namespaces by prefix
            for(Namespace n : toMatch.getNamespaces()){
                boolean didFind = false;
                for(Namespace i : item.getNamespaces()){
                    if(n.getURI().equals(i.getURI())){
                        didFind = true;
                        if(!n.getPrefix().equals(i.getPrefix())){
                            //allow for different namespace prefixes
                            itemExp = itemExp.replace(i.getPrefix() + ":", n.getPrefix() + ":");
                        }
                    }
                }
                if(!didFind){
                    return false;
                }
            }
            
            if(!toMatch.getExpression().equals(itemExp)){
                return false;
            }
            
            return true;
        }

        @Override
        public void describeTo(Description description) {
            if(toMatch == null){
                description.appendText("null XPath expression");
                return;
            }
            description.appendText("XPath expression equal to ").appendText(toMatch.getExpression());
        }
        
    
    }
    
    /**
     * Returns a new query in which the given selector expression is replaced whenever it is found
     * with the new selector expression.
     * This is used primarily to replace expressions selecting the Id property of an object
     * with the {@link #Id} expression, so that engines can take advantage of ID indexes.
     * @param <U>
     * @param selector The selector to replace.
     * @param newSelector The new selector to use.
     * @return A new XpathQuery with the replaced values.
     */
    public <U> XpathQuery replaceExpression(XPathExpression<U> selector, XPathExpression<U> newSelector){
        return replaceExpression(new XPathExpressionEqualsMatcher<>(selector), newSelector);
    }
    
    /**
     * Returns a new query in which the given selector expression is replaced whenever it is found
     * with the new selector expression.
     * This is used primarily to replace expressions selecting the Id property of an object
     * with the {@link #Id} expression, so that engines can take advantage of ID indexes.
     * @param <U>
     * @param selector A matcher matching the selector expressions to replace.
     * @param newSelector The new selector to use.
     * @return A new XpathQuery with the replaced values.
     */
    public <U> XpathQuery replaceExpression(Matcher<XPathExpression<U>> selector, XPathExpression<U> newSelector){
        XpathQuery ret = new XpathQuery();
        ret.conversionService = this.conversionService;
        ret.queryType = this.queryType;
        ret.value = this.value;
        ret.valueType = this.valueType;
        ret.rowMatcher = this.rowMatcher;
        
        //match expression
        if(selector.matches(this.selector)){
            ret.selector = newSelector;
        }
        else{
            ret.selector = this.selector;
        }
        
        //check sub-expressions
        if(this.queryChain != null){
            ret.queryChain = new ArrayList<>(this.queryChain.size());
            for(XpathQuery q : this.queryChain){
                ret.queryChain.add(q.replaceExpression(selector, newSelector));
            }
        }
        
        return ret;
    }
    
    /**
     * An XPath expression selecting the database ID of a row.
     * This can be used to build queries matching the row's ID.
     * <p/>
     * Currently this is the expression "@db:id".
     * 
     */
    public static final XPathExpression<Attribute> Id = XPathFactory.instance().compile("@db:id", new AttributeFilter(), null, XFlatDatabase.xFlatNs);
    
    //<editor-fold desc="builders">

    /**
     * Creates an XPath query that matches the single element at the selector to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be equal to.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery eq(XPathExpression<?> selector, U object){
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
    public static <U> XpathQuery ne(XPathExpression<?> selector, U object){
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
    public static <U  extends Comparable<U>> XpathQuery lt(XPathExpression<?> selector, U object){
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
    public static <U  extends Comparable<U>> XpathQuery lte(XPathExpression<?> selector, U object){
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
    public static <U  extends Comparable<U>> XpathQuery gt(XPathExpression<?> selector, U object){
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
    public static <U  extends Comparable<U>> XpathQuery gte(XPathExpression<?> selector, U object){
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
    public static <U> XpathQuery matches(XPathExpression<?> selector, Matcher<U> matcher, Class<U> clazz){
        return new XpathQuery(selector, QueryType.MATCHES, matcher, clazz, matcher);
    }

    /**
     * functions the same as ne(selector, null), but does not invoke the conversion
     * service.
     * @param selector The selector to test whether it exists.
     * @return An XpathQuery object.
     */
    public static XpathQuery exists(XPathExpression<?> selector){
        Matcher<Object> m = Matchers.notNullValue();
        
        return new XpathQuery(selector, QueryType.EXISTS, true, Object.class, m);
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

        private XPathExpression<?> selector;
        private Class<T> expectedType;
        private Matcher<T> subMatcher;

        public ValueMatcher(XPathExpression<?> selector, Class<T> expectedType, Matcher<T> subMatcher){
            super(Element.class);

            this.selector = selector;
            this.expectedType = expectedType;
            this.subMatcher = subMatcher;
        }

        @Override
        protected boolean matchesSafely(Element item) {
            Object selected;
            
            selected = selector.evaluateFirst(item);
            
            if(selected == null){
                if(expectedType != null && conversionService != null && 
                        conversionService.canConvert(null, expectedType)){
                    try {
                        return subMatcher.matches(conversionService.convert(null, expectedType));
                    } catch (ConversionException ex) {
                        Log log = LogFactory.getLog(getClass());
                        log.warn("Unable to convert null to " + expectedType);
                        return false;
                    }
                }

                return subMatcher.matches(null);
            }
            
            if(expectedType != null){
                if(!expectedType.isAssignableFrom(selected.getClass())){
                    //need to convert
                    if(conversionService != null && conversionService.canConvert(selected.getClass(), expectedType)){
                        try{
                            selected = conversionService.convert(selected, expectedType);
                        }catch(ConversionException ex){
                            //if we can't convert then the data is in the wrong format
                            //and probably was not intended to be selected
                            return false;
                        }
                    }
                    else{
                        return false;
                    }
                }
            }

            return subMatcher.matches(selected);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("value at ")
                    .appendText(this.selector.getExpression())
                    .appendText(" that ")
                    .appendDescriptionOf(this.subMatcher);
        }
    }

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        this.appendToString(str);
        return str.toString();
    }
    
    private void appendToString(StringBuilder str){
        str.append("{");
        
        if(this.queryChain != null && this.queryChain.size() > 0){
            str.append(this.queryType.toString());
            str.append(": [");
            boolean first = true;
            for(XpathQuery q : queryChain){
                if(first)
                    first = false;
                else
                    str.append(", ");
                
                q.appendToString(str);
            }
            str.append("]");
        }
        else{
            str.append(" '")
                .append(this.getSelector().getExpression())
                .append("': {")
                .append(this.queryType).append(": ");
            
            if(this.queryType == QueryType.MATCHES){
                StringDescription d = new StringDescription();
                ((SelfDescribing)this.getValue()).describeTo(d);
                str.append(d.toString());
            }
            else if(String.class.equals(this.valueType)){
                str.append("'").append(this.value).append("'");
            }
            else
                str.append(this.value);

            str.append(" }");
        }
        str.append("}");
    }
}
