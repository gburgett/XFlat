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
package org.xflatdb.xflat.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.xpath.XPathExpression;

/**
 * Implements a Hamcrest {@link org.hamcrest.Matcher} for {@link XPathExpression} objects
 * that is namespace-aware and can translate namespace prefixes.
 * <p/>
 * According to this matcher, in the following example the two XPath expressions are equal:
 * <p/>
 * <code>
 * a:abc xmlns:a = "http://www.example.com"<br/>
 * b:abc xmlns:b = "http://www.example.com"
 * </code>
 * 
 * @author Gordon
 */
public class XPathExpressionEqualityMatcher<U> extends TypeSafeMatcher<XPathExpression<U>> {
    private final XPathExpression<U> toMatch;
    
    private List<String> myExpTokens = null;
    
    private static final Pattern nsPattern = Pattern.compile("([a-zA-Z0-9\\.]+):");
    
    /**
     * Creates a new XPathExpressionEqualityMatcher whose {@link #matches(java.lang.Object) }
     * method will return true iff the given XPathExpression is equal to the one provided here.
     * @param toMatch The expression to match.
     */
    public XPathExpressionEqualityMatcher(XPathExpression<U> toMatch) {
        this.toMatch = toMatch;        
    }

    private static List<String> tokenizeExpression(XPathExpression<?> expression){
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
    
    /**
     * Returns true if the matcher matches the given XPath expression.
     * @param item The XPath expression to match.
     * @return true if they are a match.
     */
    @Override
    protected boolean matchesSafely(XPathExpression<U> item) {
        if (item == null) {
            return toMatch == null;
        }
        if(toMatch == null){
            return false;
        }
        
        if(myExpTokens == null){
            myExpTokens = tokenizeExpression(toMatch);
        }
        
        return equals(toMatch, myExpTokens, item, tokenizeExpression(item));
    }

    /**
     * Describes this matcher to the given description.
     * @param description 
     */
    @Override
    public void describeTo(Description description) {
        if (toMatch == null) {
            description.appendText("null XPath expression");
            return;
        }
        description.appendText("XPath expression equal to ").appendText(toMatch.getExpression());
    }
    
    private static boolean equals(XPathExpression<?> left, List<String> leftSideTokens, XPathExpression<?> right, List<String> rightSideTokens){
        
        
        
        if(rightSideTokens.size() != leftSideTokens.size()){
            return false;
        }
        
        for(int i = 0; i < leftSideTokens.size(); i++){
            if(!leftSideTokens.get(i).equals(rightSideTokens.get(i))){
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Compares two XPath expressions for equality by tokenizing their expressions
     * and expanding namespace prefixes.
     * @param left One XPath expression to compare for equality
     * @param right The other XPath expression to compare for equality.
     * @return true iff the two expressions are equal.
     */
    public static boolean equals(XPathExpression<?> left, XPathExpression<?> right){
        if (left == null) {
            return right == null;
        }
        if(right == null){
            return false;
        }
        
        return equals(left, tokenizeExpression(left), right, tokenizeExpression(right));
    }
}
