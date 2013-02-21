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
package org.xflatdb.xflat.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.SelfDescribing;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.filter.AttributeFilter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.util.XPathExpressionEqualityMatcher;

/**
 * Represents a query in the XPath Query Language. 
 * <p/>
 * Queries are constructed using XPath expressions associated to values.
 * The XPath expressions select elements or attributes in the row data which
 * are convertible to the query's value type, and then the converted value
 * type is compared to the given value to see if the row matches the query.
 * <p/>
 * The row is matched to the query if any one 
 * @author Gordon
 */
public class XPathQuery {

    
    //<editor-fold desc="properties" >
    private XPathExpression<?> selector;
    /**
     * Gets the XPath expression that is the selector for this query.
     * AND and OR queries have null here, their subqueries have this populated.
     * @return the selecting expression for this query.
     */
    public XPathExpression<?> getSelector(){
        return selector;
    }

    private Matcher<Element> rowMatcher;
    /**
     * Gets the Hamcrest matcher that matches database rows.  This is used to determine
     * if a row is a match for a query.
     * @return The database row hamcrest matcher.
     */
    public Matcher<Element> getRowMatcher(){
        return rowMatcher;
    }
    
    private Object value;
    /**
     * Gets the value to which the element selected by the {@link #getSelector() selector}
     * should be compared, according to this query's type. <br/>
     * AND and OR queries have null here, their subqueries have this populated.
     * @return the value that will be compared to the selected element.
     */
    public Object getValue(){
        return value;
    }
    
    private Class<?> valueType;
    /**
     * Gets the type of {@link #getValue() the value}, to which the selected element
     * should be convertible so it can be compared to the value.
     * @return The type of the value, or null if the value is null.
     */
    public Class<?> getValueType(){
        return valueType;
    }
    
    private QueryType queryType;
    /**
     * Gets the type of this query, ie. EQ, NE, AND, OR, etc.
     * @return The type of query.
     */
    public QueryType getQueryType(){
        return queryType;
    }
    
    /**
     * Gets the sub queries of this query.  This is only populated for
     * AND and OR queries.
     * @return A list of all the sub queries, or an empty list if this is not an AND
     * or OR query.
     */
    public List<XPathQuery> getSubQueries(){
        return queryChain == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(queryChain);
    }
    
    private List<XPathQuery> queryChain;

    //</editor-fold>
    
    
    //<editor-fold desc="dependencies">
    private ConversionService conversionService;
    /**
     * Sets the conversion service for the entire query chain.  Necessary for
     * matchers that match against non-JDOM types.
     * <p/>
     * JDOM Engines will set this automatically when they execute the query.
     * @param service The conversion service to use to convert selected data
     * to the comparable value type.
     */
    public void setConversionService(ConversionService service){
        this.conversionService = service;
        
        if(this.queryChain != null){
            for(XPathQuery q : this.queryChain){
                q.setConversionService(service);
            }
        }
    }

    private XPathExpression<Object> alternateIdExpression = null;
    /**
     * Sets an expression that represents the DOM node where the object's ID is stored.
     * Some engines can make use of queries on ID for indexing, so they need to 
     * know the alternate ID expression in case the user queries on that rather than
     * {@link #Id}.
     * <p/>
     * This is automatically populated by {@link org.xflatdb.xflat.db.ConvertingTable}.
     * @param expression The expression which represents an alternate way to select the ID.
     */
    public void setAlternateIdExpression(XPathExpression<Object> expression){
        this.alternateIdExpression = expression;
    }
    
    //</editor-fold>
    
    
    //<editor-fold desc="constructors" >
    private XPathQuery(XPathExpression<?> selector, QueryType type, Object value, Class<?> valueType,
            Matcher<?> valueMatcher)
    {
        this.selector = selector;
        this.rowMatcher = new ValueMatcher(selector, valueType, valueMatcher);
        this.queryType = type;
        this.value = value;
        this.valueType = valueType;
    }

    private XPathQuery(QueryType type, Matcher<Element> rowMatcher, XPathQuery... queries){
        this.queryType = type;
        this.rowMatcher = rowMatcher;
        this.queryChain = new ArrayList<>();
        this.queryChain.addAll(Arrays.asList(queries));
    }
    
    private XPathQuery(){
    }
    
    //</editor-fold>
    
    
    //<editor-fold desc="methods"> 
    
    /**
     * A special overload of dissect that is based on an ID index.  Takes advantage
     * of an {@link #setAlternateIdExpression(org.jdom2.xpath.XPathExpression) alternate ID expression}
     * if it exists.
     * @param <U>
     * @param comparer A comparer comparing instances of idClass.
     * @param idClass The class to which the ID is convertible.
     * @return An IntervalSet representing the values on the index to search.
     * @throws InvalidQueryException if the query expects the ID to be of a different class.
     */
    public <U> IntervalSet<U> dissectId(Comparator<U> comparer, Class<U> idClass){
        Matcher<XPathExpression> index = new XPathExpressionEqualityMatcher(Id);
        if(this.alternateIdExpression != null){
            index = org.hamcrest.Matchers.anyOf(index, new XPathExpressionEqualityMatcher(this.alternateIdExpression));
        }
        
        return this.dissect(index, comparer, idClass);
    }
    
    /**
     * Dissects the query based on the given index.  This returns the Intervals on the index to which
     * this query should be applied.  If the query is invalid for the index applied, an {@link InvalidQueryException} is thrown.
     * @param <U>
     * @param index The XPathExpression describing an index on a table.
     * @param comparer A Comparator for the expected values of the index.
     * @param indexClass The class to which the value selected by the index is expected to be convertible.
     * @return An IntervalSet representing the values on the index to search.
     * @throws InvalidQueryException if the query is not valid for the given index and index class.
     */
    public <U> IntervalSet<U> dissect(XPathExpression index, Comparator<U> comparer, Class<U> indexClass){
        return dissect(new XPathExpressionEqualityMatcher(index), comparer, indexClass);
    }
    
    /**
     * Dissects the query based on the given index.  This returns the Intervals on the index to which
     * this query should be applied.  If the query is invalid for the index applied, an {@link InvalidQueryException} is thrown.
     * @param <U>
     * @param index A matcher matching an XPathExpression describing an index on a table.
     * @param comparer A Comparator for the expected values of the index.
     * @param indexClass The class to which the value selected by the index is expected to be convertible.
     * @return An IntervalSet representing the values on the index to search.
     * @throws InvalidQueryException if the query is not valid for the given index and index class.
     */
    public <U> IntervalSet<U> dissect(Matcher<XPathExpression> index, Comparator<U> comparer, Class<U> indexClass){
        
        if(this.selector != null && !index.matches(this.selector)){
            //we don't care about this part of the query, as far as it's concerned a full table scan is in order.
            return IntervalSet.all();
        }
        
        U convertedValue;
        if(this.value != null && !indexClass.isAssignableFrom(this.valueType)){
            try {
                //is it convertible?
                convertedValue = this.conversionService.convert(value, indexClass);
            } catch (ConversionException ex) {
                //nope
                throw new InvalidQueryException(this, indexClass);
            }
        }
        else{
            //it's assignable, go ahead and assign it.
            convertedValue = (U)value;
        }
        
        switch(this.queryType){
            case EQ:
                if(this.value == null){
                    //we want one that doesn't exist.  Maybe this index is sparse?
                    //if so none of the values in this index will contain what we want.
                    return IntervalSet.none();
                }

                return IntervalSet.eq(convertedValue);
                
            case NE:
                if(this.value == null){
                    //we just want one that exists.  Maybe this index is sparse?
                    //if so all the values in this index are fair game.
                    return IntervalSet.all();
                }

                return IntervalSet.ne(convertedValue);
                
            case LT:
                return IntervalSet.lt(convertedValue);
                
            case LTE:
                return IntervalSet.lte(convertedValue);
                
            case GT:
                return IntervalSet.gt(convertedValue);
                
            case GTE:
                return IntervalSet.gte(convertedValue);
                
            case EXISTS:
            case MATCHES:
            case ANY:
                //always a full table scan
                return IntervalSet.all();
                
            case AND:
                IntervalSet ret = null;
                for(XPathQuery q : this.queryChain){
                    if(ret == null){
                        ret = q.dissect(index, comparer, indexClass);
                    }
                    else{
                        //AND means intersection
                        ret = ret.intersection(q.dissect(index, comparer, indexClass), comparer);
                    }
                }
                return ret;
                
            case OR:
                ret = null;
                for(XPathQuery q : this.queryChain){
                    if(ret == null){
                        ret = q.dissect(index, comparer, indexClass);
                    }
                    else{
                        //OR means union
                        ret = ret.union(q.dissect(index, comparer, indexClass), comparer);
                    }
                }
                return ret;
                
            default:
                throw new UnsupportedOperationException("Unknown query type " + this.queryType);
        }
    }
    
    //</editor-fold>
    
    
    //<editor-fold desc="constants">
    /**
     * An XPath expression selecting the database ID of a row.
     * This can be used to build queries matching the row's ID.
     * <p/>
     * Currently this is the expression "@db:id".
     * 
     */
    public static final XPathExpression<Attribute> Id = XPathFactory.instance().compile("@db:id", new AttributeFilter(), null, XFlatDatabase.xFlatNs);
    //</editor-fold>
    
    //<editor-fold desc="builders">

    /**
     * Creates an XPath query that matches the single element at the selector to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be equal to.
     * @return An XpathQuery object
     */
    public static <U> XPathQuery eq(XPathExpression<?> selector, U object){
        Matcher<U> eq = Matchers.equalTo(object);
        
        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        return new XPathQuery(selector, QueryType.EQ, object, valueType, eq);
    }
    /**
     * Creates an XPath query that matches when the single element at the selector
     * does not exist or is not equal to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should not be equal to.
     * @return An XpathQuery object
     */
    public static <U> XPathQuery ne(XPathExpression<?> selector, U object){
        Matcher<U> ne = Matchers.not(Matchers.equalTo(object));
        
        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        return new XPathQuery(selector, QueryType.NE, object, valueType, ne);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XPathQuery lt(XPathExpression<?> selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
                
        Matcher<U> lt = Matchers.lessThan(object);
        return new XPathQuery(selector, QueryType.LT, object, valueType, lt);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XPathQuery lte(XPathExpression<?> selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> lte = Matchers.lessThanOrEqualTo(object);
        return new XPathQuery(selector, QueryType.LTE, object, valueType, lte);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XPathQuery gt(XPathExpression<?> selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> gt = Matchers.greaterThan(object);
        return new XPathQuery(selector, QueryType.GT, object, valueType, gt);
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XPathQuery gte(XPathExpression<?> selector, U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        Class<?> valueType = null;
        if(object != null)
            valueType = object.getClass();
        
        Matcher<U> gte = Matchers.greaterThanOrEqualTo(object);
        return new XPathQuery(selector, QueryType.GTE, object, valueType, gte);
    }

    /**
     * Create an XPath query that matches when all of the provided queries match.
     * @param queries The queries that all must match.
     * @return  an XpathQuery object
     */
    public static XPathQuery and(XPathQuery... queries){
        if(queries.length < 2){
            throw new IllegalArgumentException("AND requires at least 2 queries");
        }
        
        List<Matcher<? super Element>> ms = new ArrayList<>();

        for(XPathQuery q : queries){
            ms.add(q.getRowMatcher());
        }

        Matcher<Element> ret = Matchers.allOf(ms);
        return new XPathQuery(QueryType.AND, ret, queries);
    }

    /**
     * Create an XpathQuery that matches when any of the provided queries match.
     * @param queries the queries, one of which must match.
     * @return an XpathQuery object.
     */
    public static XPathQuery or(XPathQuery... queries){
        if(queries.length < 2){
            throw new IllegalArgumentException("OR requires at least 2 queries");
        }
        
        List<Matcher<? super Element>> ms = new ArrayList<>();

        for(XPathQuery q : queries){
            ms.add(q.getRowMatcher());
        }

        Matcher<Element> ret = Matchers.anyOf(ms);
        return new XPathQuery(QueryType.OR, ret, queries);
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
    public static <U> XPathQuery matches(XPathExpression<?> selector, Matcher<U> matcher, Class<U> clazz){
        return new XPathQuery(selector, QueryType.MATCHES, matcher, clazz, matcher);
    }

    /**
     * functions the same as ne(selector, null), but does not invoke the conversion
     * service.
     * @param selector The selector to test whether it exists.
     * @return An XpathQuery object.
     */
    public static XPathQuery exists(XPathExpression<?> selector){
        Matcher<Object> m = Matchers.notNullValue();
        
        return new XPathQuery(selector, QueryType.EXISTS, true, Object.class, m);
    }
    
    /**
     * An XPathQuery that matches any and all rows regardless of content.
     * useful for find all or delete all.
     * @return A new XPathQuery that will match all rows.
     */
    public static XPathQuery any(){
        return new XPathQuery(QueryType.ANY, Matchers.anyOf(Matchers.nullValue(), Matchers.any(Element.class)));
    }

    //</editor-fold>

    //<editor-fold desc="inner classes">
    
    /**
     * The type of the query.
     * Can be used by engines to inspect the query in order to generate an
     * execution plan.
     */
    public enum QueryType {
        /** Represents an equals query. */
        EQ,
        /** Represents a not equals query. */
        NE,
        /** Represents a less than query. */
        LT,
        /** Represents a less than or equals query. */
        LTE,
        /** Represents a greater than query. */
        GT,
        /** Represents a greater than or equals query. */
        GTE,
        /** Represents the intersection of several sub-queries. */
        AND,
        /** Represents the union of several sub-queries. */
        OR,
        /** Represents an exists query. */
        EXISTS,
        /** Represents a matches query. */
        MATCHES,
        /** Represents an Any query, which matches all rows. */
        ANY
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
            boolean anyMatches = false;
            for(Object selected : selector.evaluate(item)){
                anyMatches = true;
                System.out.println("selected: " + selected);
                if(expectedType != null){
                    if(!expectedType.isAssignableFrom(selected.getClass())){
                        //need to convert
                        if(conversionService != null && conversionService.canConvert(selected.getClass(), expectedType)){
                            try{
                                selected = conversionService.convert(selected, expectedType);
                            }catch(ConversionException ex){
                                //if we can't convert then the data is in the wrong format
                                //and probably was not intended to be selected
                                continue;
                            }
                        }
                        else{
                            continue;
                        }
                    }
                }
                
                if(subMatcher.matches(selected)){
                    return true;
                }
            }
            
            if(anyMatches){
                //we didn't match this row
                return false;
            }
            
            //not a single match in all the selected nodes,
            //check to see if we are matching null (as in, not exists)
            if(expectedType != null && conversionService != null && 
                    conversionService.canConvert(null, expectedType)){
                try {
                    return subMatcher.matches(conversionService.convert(null, expectedType));
                } catch (ConversionException ex) {
                    //if we can't convert then the data is in the wrong format
                                //and probably was not intended to be selected
                    return false;
                }
            }

            return subMatcher.matches(null);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a value at ")
                    .appendText(this.selector.getExpression())
                    .appendText(" that is ")
                    .appendDescriptionOf(this.subMatcher);
        }
    }
    
    //</editor-fold>
    
    //<editor-fold desc="utility">
    
    /**
     * Returns the string representation of this query.
     */
    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        this.toString(str);
        return str.toString();
    }
    
    private void toString(StringBuilder str){
        str.append("{");
        
        if(this.queryChain != null && this.queryChain.size() > 0){
            str.append(this.queryType.toString());
            str.append(": [");
            boolean first = true;
            for(XPathQuery q : queryChain){
                if(first)
                    first = false;
                else
                    str.append(", ");
                
                q.toString(str);
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
    
    //</editor-fold>
}
