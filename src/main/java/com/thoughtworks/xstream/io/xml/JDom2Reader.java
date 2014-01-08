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
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * An XStream document reader which reads XML data out of a JDOM2 Document.
 * This provides the link between XStream and JDOM so that XStream can read
 * and write JDOM documents.
 * @author Gordon Burgett
 */
public class JDom2Reader extends AbstractDocumentReader {

    private Element currentElement;

    public JDom2Reader(Element root) {
        super(root);
    }

    public JDom2Reader(Document document) {
        super(document.getRootElement());
    }

    /**
     * @since 1.4
     */
    public JDom2Reader(Element root, NameCoder nameCoder) {
        super(root, nameCoder);
    }

    /**
     * @since 1.4
     */
    public JDom2Reader(Document document, NameCoder nameCoder) {
        super(document.getRootElement(), nameCoder);
    }

    @Override
    protected void reassignCurrentElement(Object current) {
        currentElement = (Element) current;
    }

    @Override
    protected Object getParent() {
        // JDOM 1.0:
        return currentElement.getParentElement();
    }

    @Override
    protected Object getChild(int index) {
        return currentElement.getChildren().get(index);
    }

    @Override
    protected int getChildCount() {
        return currentElement.getChildren().size();
    }

    @Override
    public String getNodeName() {
        return decodeNode(currentElement.getName());
    }

    @Override
    public String getValue() {
        return currentElement.getText();
    }

    @Override
    public String getAttribute(String name) {
        return currentElement.getAttributeValue(encodeAttribute(name));
    }

    @Override
    public String getAttribute(int index) {
        return ((Attribute) currentElement.getAttributes().get(index)).getValue();
    }

    @Override
    public int getAttributeCount() {
        return currentElement.getAttributes().size();
    }

    @Override
    public String getAttributeName(int index) {
        return decodeAttribute(((Attribute) currentElement.getAttributes().get(index)).getQualifiedName());
    }

    @Override
    public String peekNextChild() {
        List list = currentElement.getChildren();
        if (null == list || list.isEmpty()) {
            return null;
        }
        return decodeNode(((Element) list.get(0)).getName());
    }

}
