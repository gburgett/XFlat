/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Gordon
 */
public class XPathExpressionEqualityMatcherTest {
    
    public static final XPathFactory xpath = XPathFactory.instance();
    
    public XPathExpressionEqualityMatcherTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testMatches_WrongValues_DoesNotMatch() throws Exception {
        System.out.println("testMatches_WrongValues_DoesNotMatch");
        
        XPathExpression e1 = xpath.compile("abc");
        XPathExpression e2 = xpath.compile("def");
        
        XPathExpressionEqualityMatcher instance = new XPathExpressionEqualityMatcher(e1);
        
        boolean matches = instance.matches(e2);
        
        assertFalse("Should not match", matches);
    }
    
    @Test
    public void testMatches_SameValues_Matches() throws Exception {
        System.out.println("testMatches_SameValues_Matches");
        
        XPathExpression e1 = xpath.compile("abc");
        XPathExpression e2 = xpath.compile("abc");
        
        XPathExpressionEqualityMatcher instance = new XPathExpressionEqualityMatcher(e1);
        
        boolean matches = instance.matches(e2);
        
        assertTrue("Should match", matches);
    }
    
    @Test
    public void testMatches_SameValuesWrongNs_DoesNotMatch() throws Exception {
        System.out.println("testMatches_SameValuesWrongNs_DoesNotMatch");
        
        XPathExpression e1 = xpath.compile("a:abc", Filters.fpassthrough(), null, Namespace.getNamespace("a", "http://abc"));
        XPathExpression e2 = xpath.compile("a:abc", Filters.fpassthrough(), null, Namespace.getNamespace("a", "http://def"));
        
        XPathExpressionEqualityMatcher instance = new XPathExpressionEqualityMatcher(e1);
        
        boolean matches = instance.matches(e2);
        
        assertFalse("Should not match", matches);
    }
    
    @Test
    public void testMatches_DifferentPrefixSameNs_Matches() throws Exception {
        System.out.println("testMatches_DifferentPrefixSameNs_Matches");
        
        XPathExpression e1 = xpath.compile("a:abc", Filters.fpassthrough(), null, Namespace.getNamespace("a", "http://abc"));
        XPathExpression e2 = xpath.compile("b:abc", Filters.fpassthrough(), null, Namespace.getNamespace("b", "http://abc"));
        
        XPathExpressionEqualityMatcher instance = new XPathExpressionEqualityMatcher(e1);
        
        boolean matches = instance.matches(e2);
        
        assertTrue("Should match", matches);
    }
    
    @Test
    public void testMatches_MultipleNs_Matches() throws Exception {
        System.out.println("testMatches_MultipleNs_Matches");
        
        XPathExpression e1 = xpath.compile("ac:abc/c:def", Filters.fpassthrough(), null, Namespace.getNamespace("ac", "http://abc"), Namespace.getNamespace("c", "http://def"));
        XPathExpression e2 = xpath.compile("ac:abc/d:def", Filters.fpassthrough(), null, Namespace.getNamespace("ac", "http://abc"), Namespace.getNamespace("d", "http://def"));
        
        XPathExpressionEqualityMatcher instance = new XPathExpressionEqualityMatcher(e1);
        
        boolean matches = instance.matches(e2);
        
        assertTrue("Should match", matches);
    }
}
