/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.List;
import lombok.Functions.Function1;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Matchers;
import org.hamcrest.SelfDescribing;
import org.hamcrest.StringDescription;
import propel.core.utils.Linq;

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


    private XpathQuery(XPath selector, Matcher<Element> rowMatcher){
        this.selector = selector;
        this.rowMatcher = rowMatcher;
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
        if(object == null)
            return eq(selector, object, null);

        return eq(selector, object, defaultConverter((Class<U>)object.getClass()));
    }

    /**
     * Creates an XPath query that matches the single element at the selector to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be equal to.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery eq(XPath selector, final U object, Function1<String, U> converter){
        Matcher<U> m = Matchers.equalTo(object);

        return new XpathQuery(selector,
                new ValueMatcher<>(selector, m, converter));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * does not exist or is not equal to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should not be equal to.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery ne(XPath selector, final U object){
        return new XpathQuery(selector,
                Matchers.not(XpathQuery.eq(selector, object).getRowMatcher()));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * does not exist or is not equal to the value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should not be equal to.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U> XpathQuery ne(XPath selector, final U object, Function1<String, U> converter){
        return new XpathQuery(selector,
                Matchers.not(XpathQuery.eq(selector, object, converter).getRowMatcher()));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery lt(XPath selector, final U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        return lt(selector, object, defaultConverter((Class<U>)object.getClass()));
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
    public static <U  extends Comparable<U>> XpathQuery lt(XPath selector, final U object, Function1<String, U> converter){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        final Matcher lt = Matchers.lessThan(object);
        return new XpathQuery(selector,
                new ValueMatcher<>(selector, lt, converter));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery lte(XPath selector, final U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        return lt(selector, object, defaultConverter((Class<U>)object.getClass()));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is less than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be less than or equal to.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery lte(XPath selector, final U object, Function1<String, U> converter){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        final Matcher lte = Matchers.lessThanOrEqualTo(object);
        return new XpathQuery(selector,
                new ValueMatcher<>(selector, lte, converter));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gt(XPath selector, final U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        return gt(selector, object, defaultConverter((Class<U>)object.getClass()));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gt(XPath selector, final U object, Function1<String, U> converter){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        final Matcher lt = Matchers.greaterThan(object);
        return new XpathQuery(selector,
                new ValueMatcher<>(selector, lt, converter));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than or equal to the given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than or equal to.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gte(XPath selector, final U object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        return gt(selector, object, defaultConverter((Class<U>)object.getClass()));
    }

    /**
     * Creates an XPath query that matches when the single element at the selector
     * has a value that is greater than or equal tothe given value.
     * @param <U> the type of the value
     * @param selector The xpath selector to query
     * @param object The object that the result of the xpath selection should be greater than or equal to.
     * @param converter A function to convert a string to the appropriate type for comparison.
     * @return An XpathQuery object
     */
    public static <U  extends Comparable<U>> XpathQuery gte(XPath selector, final U object, Function1<String, U> converter){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null");

        final Matcher lt = Matchers.greaterThanOrEqualTo(object);
        return new XpathQuery(selector,
                new ValueMatcher<>(selector, lt, converter));
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
        return new XpathQuery(null, ret);
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
        return new XpathQuery(null, ret);
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
        return new XpathQuery(selector, new ValueMatcher<>(selector, matcher, defaultConverter(clazz)));
    }

    /**
     * Create an XpathQuery that matches when the given matcher matches the value
     * selected by the Xpath selector.
     * @param <U> The type of the value expected at the end of the selector.
     * @param selector The Xpath expression that selects a value in a row.
     * @param matcher The matcher that decides whether this row is a match.
     * @param converter A function that converts from strings into the expected value.
     * @return an XpathQuery object.
     */
    public static <U> XpathQuery matches(XPath selector, Matcher<U> matcher, Function1<String, U> converter){
        return new XpathQuery(selector, new ValueMatcher<>(selector, matcher, converter));
    }

    //</editor-fold>

    //gets the default converter for several primitive and commonly-used types
    private static <U> Function1<String, U> defaultConverter(final Class<U> clazz){
        if(String.class.equals(clazz)){
            return null;
        }

        if(Integer.class.equals(clazz)){
            return new Function1<String, U>(){
                @Override
                public U apply(String t1) {
                    return (U)Integer.valueOf(t1);
                }
            };
        }

        if(Long.class.equals(clazz)){
            return new Function1<String, U>(){
                @Override
                public U apply(String t1) {
                    return (U)Long.valueOf(t1);
                }
            };
        }

        if(Double.class.equals(clazz)){
            return new Function1<String, U>(){
                @Override
                public U apply(String t1) {
                    return (U)Double.valueOf(t1);
                }
            };
        }

        if(Float.class.equals(clazz)){
            return new Function1<String, U>(){
                @Override
                public U apply(String t1) {
                    return (U)Float.valueOf(t1);
                }
            };
        }

        if(Boolean.class.equals(clazz)){
            return new Function1<String, U>(){
                @Override
                public U apply(String t1) {
                    return (U)Boolean.valueOf(t1);
                }
            };
        }

        //default
        return new Function1<String, U>(){
            @Override
            public U apply(String t1) {
                throw new UnsupportedOperationException("Conversion for " + clazz.toString() + " not yet supported.");
            }
        };
    }


    /**
     * The Hamcrest Matcher that matches a row by evaluating the Xpath expression,
     * and invoking the matcher for the result of the Xpath expression.
     * @param <T> The type of the expected value at the end of the Xpath expression.
     */
    private static class ValueMatcher<T> extends TypeSafeMatcher<Element>{

        private XPath selector;
        private Matcher<T> subMatcher;
        private Function1<String, T> objectConverter;

        public ValueMatcher(XPath selector, Matcher<T> subMatcher, Function1<String, T> objectConverter){
            super(Element.class);

            this.selector = selector;
            this.subMatcher = subMatcher;
            this.objectConverter = objectConverter;
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
                return subMatcher.matches(null);
            }

            if(selected instanceof String && objectConverter != null){
                //special case for strings, since we don't know when and if
                //JDOM converts content to strings
                selected = objectConverter.apply((String)selected);
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
