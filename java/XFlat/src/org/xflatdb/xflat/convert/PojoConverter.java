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
package org.xflatdb.xflat.convert;

import org.jdom2.xpath.XPathExpression;

/**
 * The interface for a PojoConverter.  The implementation is loaded dynamically
 * if needed by the database.  By default it uses JAXB, but this
 * can be changed in the configuration.
 * @author gordon
 */
public interface PojoConverter {
    
    /**
     * Extends the given conversion service to provide it the ability to convert
     * POJOs.  This can be done by adding converters or by returning a new
     * conversion service chained to the given one.
     * @param service The current conversion service used by the database.
     * @return The new conversion service that the database should use.
     */
    public ConversionService extend(ConversionService service);
    
    /**
     * Gets an XPath expression which will select the ID property
     * of the converted data inside a row.  Can be null if the class has no ID property.
     * <p/>
     * The context of the expression is the root of the row.  For example, if your
     * class "Foo" has a property "FooInt" which is your ID, and the class is converted
     * to the following XML:<br/>
     * {@code 
     * <foo>
     *  <fooInt>17</FooInt>
     * </foo>
     * }
     * then the expression should be "foo/fooInt".
     * @param clazz
     * @return 
     */
    public XPathExpression<Object> idSelector(Class<?> clazz);
}
