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
package com.thoughtworks.xstream.io.xml;

import com.thoughtworks.xstream.io.naming.NameCoder;
import java.util.List;
import org.jdom2.DefaultJDOMFactory;
import org.jdom2.Element;
import org.jdom2.JDOMFactory;

/**
 * An XStream document writer which writes XML data into a JDOM2 Document.
 * This provides the link between XStream and JDOM so that XStream can read
 * and write JDOM documents.
 * @author Gordon Burgett
 */
public class JDom2Writer extends AbstractDocumentWriter {
    private final JDOMFactory documentFactory;

    public JDom2Writer(
                      final Element container, final JDOMFactory factory,
                      final NameCoder nameCoder) {
        super(container, nameCoder);
        documentFactory = factory;
    }

    public JDom2Writer(final Element container, final JDOMFactory factory) {
        this(container, factory, new XmlFriendlyNameCoder());
    }
    public JDom2Writer(final JDOMFactory factory, final NameCoder nameCoder) {
        this(null, factory, nameCoder);
    }

    public JDom2Writer(final JDOMFactory factory) {
        this(null, factory);
    }

    public JDom2Writer(final Element container, final NameCoder nameCoder) {
        this(container, new DefaultJDOMFactory(), nameCoder);
    }


    public JDom2Writer(final Element container) {
        this(container, new DefaultJDOMFactory());
    }

    public JDom2Writer() {
        this(new DefaultJDOMFactory());
    }

    @Override
    protected Object createNode(final String name) {
        final Element element = documentFactory.element(encodeNode(name));
        final Element parent = top();
        if (parent != null) {
            parent.addContent(element);
        }
        return element;
    }

    @Override
    public void setValue(final String text) {
        top().addContent(documentFactory.text(text));
    }

    @Override
    public void addAttribute(final String key, final String value) {
        top().setAttribute(documentFactory.attribute(encodeAttribute(key), value));
    }

    private Element top() {
        return (Element)getCurrent();
    }
    
    /**
     * Casts the top level nodes as a list of JDOM2 elements.
     * @return A list of the top level nodes
     */
    @Override
    public List<Element> getTopLevelNodes(){
        return (List<Element>)super.getTopLevelNodes();
    }
    
    /**
     * Gets the first top level node, or null if none exists.
     * Useful if you expect only one top level node.
     * @return The first top level node, or null.
     */
    public Element getTopLevelNode(){
        List<Element> top = super.getTopLevelNodes();
        if(top.isEmpty())
            return null;
        
        return top.get(0);
    }
}
