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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.query.XPathQuery;
import org.jdom2.Element;

/**
 * Marks one java bean property as the ID.  The ID will be uniquely generated
 * and assigned if it does not already exist.
 * <p/>
 * The {@link #value() } property instructs XFlat that this object serializes
 * its ID property to the XML Element, and gives the XPath expression that should
 * be treated as equivalent to {@link XPathQuery#Id} when XFlat inspects a query.
 * <p/>
 * Example (inside class Foo):<br/>
 * <pre>
 * @Id(value="foo/@t:id", namespaces={"xmlns:t='http://www.example.com/ns'"})
 * public String getId(){
 * </pre>
 * @author gordon
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {
    /**
     * The XPath expression which selects this property once it has been converted to an {@link Element}.
     * <br/>
     * This tells XFlat that this expression is equivalent to {@link XPathQuery#Id}, so that XFlat can
     * apply optimizations based on ID if it encounters this expression in a query.<br/>
     * This must be the exact same expression used in queries in order for XFlat to match it.
     * <p/>
     * If this is not set, the {@link PojoConverter} will be invoked to take a "best guess".
     * @return The string value of the raw XPath selecting the ID in a query.
     */
    String value() default "";
    
    /**
     * A set of XML namespace declarations that declare the namespaces used
     * in the XPath expression.  If the {@link #value() } expression includes
     * a namespace, this must declare the namespace.<br/>
     * Example:<br/>
     * <pre>
     * xmlns:a="http://www.example.com/ns"
     * </pre>
     * 
     * @return An array of namespaces used in the XPath expression.
     */
    String[] namespaces() default "";
}
