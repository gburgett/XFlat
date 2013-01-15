/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Namespace;
import org.jdom2.xpath.XPathExpression;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements a Hamcrest {@link org.hamcrest.Matcher} for {@link XPathExpression} objects
 * that is namespace-aware and can translate namespace prefixes.
 * <p/>
 * Therefore in the following example, the two XPath expressions are equal:
 * <code>
 * a:abc xmlns:a = "http://www.example.com"
 * b:abc xmlns:b = "http://www.example.com"
 * </code>
 * 
 * @author Gordon
 */
public class XPathExpressionEqualityMatcher<U> extends TypeSafeMatcher<XPathExpression<U>> {
    private final XPathExpression<U> toMatch;
    
    private List<String> myExpTokens = null;
    
    private static final Pattern nsPattern = Pattern.compile("([a-zA-Z0-9\\.]+):");
    
    public XPathExpressionEqualityMatcher(XPathExpression<U> toMatch) {
        this.toMatch = toMatch;        
    }

    private List<String> tokenizeExpression(XPathExpression<U> expression){
        String exp = expression.getExpression();
        
        List<String> ret = new ArrayList<>();
        Matcher matcher = nsPattern.matcher(exp);
        int index = 0;
        while(matcher.find()){
            if(matcher.start() > index){
                ret.add(exp.substring(index, matcher.start() - index));
            }
            
            //translate namespace prefix to full ns 
            String prefix = matcher.group(1);
            ret.add(expression.getNamespace(prefix).getURI());
            
            index = matcher.end();
        }
        
        if(index < exp.length()){
            ret.add(exp.substring(index));
        }
        
        return ret;
    }
    
    @Override
    protected boolean matchesSafely(XPathExpression<U> item) {
        if (toMatch == null) {
            return item == null;
        }
        
        if(myExpTokens == null){
            myExpTokens = tokenizeExpression(toMatch);
        }
        List<String> itemTokens = tokenizeExpression(item);
        
        
        if(itemTokens.size() != myExpTokens.size()){
            return false;
        }
        
        for(int i = 0; i < myExpTokens.size(); i++){
            if(!myExpTokens.get(i).equals(itemTokens.get(i))){
                return false;
            }
        }
        
        return true;
    }

    @Override
    public void describeTo(Description description) {
        if (toMatch == null) {
            description.appendText("null XPath expression");
            return;
        }
        description.appendText("XPath expression equal to ").appendText(toMatch.getExpression());
    }
    
}
