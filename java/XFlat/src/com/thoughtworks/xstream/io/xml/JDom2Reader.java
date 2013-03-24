/*
 * Copyright (C) 2004, 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2009, 2011 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 03. September 2004 by Joe Walnes
 */
package com.thoughtworks.xstream.io.xml;

import com.thoughtworks.xstream.io.naming.NameCoder;
import java.util.List;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * @author Laurent Bihanic
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
