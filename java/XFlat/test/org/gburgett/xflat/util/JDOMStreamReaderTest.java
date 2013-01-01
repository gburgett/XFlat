/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.input.StAXStreamBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import test.Baz;

/**
 *
 * @author gordon
 */
public class JDOMStreamReaderTest {
    
    public JDOMStreamReaderTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testSimpleDocument_BuildsFromReader_SameDocument() throws Exception {
        System.out.println("testSimpleDocument_BuildsFromReader_SameDocument");
        
        Document doc = new Document();
        doc.setRootElement(new Element("simple"));
        
        Document read;
        try(JDOMStreamReader instance = new JDOMStreamReader(doc)){
            StAXStreamBuilder builder = new StAXStreamBuilder();
            read = builder.build(instance);
        }
        
        assertNotNull("Should have built a doc", read);
        assertEquals("Should have correct root element", "simple", read.getRootElement().getName());
    }//end testSimpleDocument_BuildsFromReader_SameDocument
    
    @Test
    public void testSimpleDocWithNamespace_BuildsFromReader_SameDocument() throws Exception {
        System.out.println("testSimpleDocWithNamespace_BuildsFromReader_SameDocument");
        
        Document doc = new Document();
        doc.setRootElement(new Element("simple", Namespace.getNamespace("testns")));
        doc.getRootElement().addNamespaceDeclaration(Namespace.getNamespace("tst", "test2"));
        
        Element content = new Element("test2ns", Namespace.getNamespace("tst", "test2"));
        content.setText("some text");
        doc.getRootElement().addContent(content);
        
        content = new Element("testNoNs", Namespace.NO_NAMESPACE);
        content.setText("other text");
        doc.getRootElement().addContent(content);
        
        Document read;
        try(JDOMStreamReader instance = new JDOMStreamReader(doc)){
            StAXStreamBuilder builder = new StAXStreamBuilder();
            read = builder.build(instance);
        }
        
        assertNotNull("Should have built a doc", read);
        assertEquals("Should have correct root element", "simple", read.getRootElement().getName());
        assertEquals("Should have correct root element", "testns", read.getRootElement().getNamespaceURI());
        
        List<Namespace> additional = read.getRootElement().getAdditionalNamespaces();
        assertEquals("Should have declared the additional ns", 2, additional.size());
        assertEquals("Should be right ns", "test2", additional.get(1).getURI());
        assertEquals("Should be right prefix", "tst", additional.get(1).getPrefix());
        
        List<Element> children = read.getRootElement().getChildren();
        assertEquals("Should have 2 kids", 2, children.size());
        assertEquals("Should be right kids in right order", "test2ns", children.get(0).getName());
        assertEquals("Should be right kids in right order", "test2", children.get(0).getNamespaceURI());
        assertEquals("Should be right kids in right order", "tst", children.get(0).getNamespacePrefix());
        assertEquals("Should be right kids in right order", "some text", children.get(0).getText());
        
        assertEquals("Should be right kids in right order", "testNoNs", children.get(1).getName());
        assertEquals("Should be right kids in right order", "", children.get(1).getNamespaceURI());
        assertEquals("Should be right kids in right order", "", children.get(1).getNamespacePrefix());
        assertEquals("Should be right kids in right order", "other text", children.get(1).getText());
    }//end testSimpleDocWithNamespace_BuildsFromReader_SameDocument
    
    @Test
    public void testDocWithAttributes_HasAttributes() throws Exception {
        System.out.println("testDocWithAttributes_HasAttributes");
        
        Document doc = new Document();
        doc.setRootElement(new Element("simple"));
        
        Element content = new Element("hasAtt");
        content.setAttribute("nons", "nons value");
        content.setAttribute("testns", "testns value", Namespace.getNamespace("tst", "testNs"));
        doc.getRootElement().addContent(content);
        
        Document read;
        try(JDOMStreamReader instance = new JDOMStreamReader(doc)){
            StAXStreamBuilder builder = new StAXStreamBuilder();
            read = builder.build(instance);
        }
        
        assertNotNull("Should have built a doc", read);
        assertEquals("Should have correct root element", "simple", read.getRootElement().getName());
        
        Element hasAtt = read.getRootElement().getChild("hasAtt");
        assertEquals("should have no-ns attr", "nons value", hasAtt.getAttributeValue("nons"));
        assertEquals("should have test-ns attr", "testns value", hasAtt.getAttributeValue("testns", Namespace.getNamespace("testNs")));
    }//end testDocWithAttributes_HasAttributes
    
    
    @Test
    public void testDocWithMixedContent_HandlesMixedContent() throws Exception {
        System.out.println("testDocWithMixedContent_HandlesMixedContent");
        
        
        Document doc = new Document();
        doc.setRootElement(new Element("simple"));
        
        doc.getRootElement().addContent(new Text("pre-element text"));
        Element el = new Element("junk");
        el.setText("in-element text");
        doc.getRootElement().addContent(el);
        doc.getRootElement().addContent(new CDATA("post-element text"));
        
        Document read;
        try(JDOMStreamReader instance = new JDOMStreamReader(doc)){
            StAXStreamBuilder builder = new StAXStreamBuilder();
            read = builder.build(instance);
        }
        
        assertNotNull("Should have built a doc", read);
        assertEquals("Should have correct root element", "simple", read.getRootElement().getName());
        
        List<Content> content = read.getRootElement().getContent();
        assertEquals("first content should be text", Content.CType.Text, content.get(0).getCType());
        assertEquals("second content should be element", Content.CType.Element, content.get(1).getCType());
        assertEquals("third content should be CDATA", Content.CType.CDATA, content.get(2).getCType());
        
        assertEquals("pre-element text", content.get(0).getValue());
        assertEquals("in-element text", content.get(1).getValue());
        assertEquals("post-element text", content.get(2).getValue());
    }//end testDocWithMixedContent_HandlesMixedContent
    
    @Test
    public void testJaxbUnmarshalling_UnmarshallsFromReader() throws Exception {
        System.out.println("testJaxbUnmarshalling_UnmarshallsFromReader");
        
        JAXBContext context = JAXBContext.newInstance(Baz.class);
        Unmarshaller um = context.createUnmarshaller();
        
        Document doc = new Document();
        doc.setRootElement(new Element("baz"));
        
        Attribute attrInt = new Attribute("attrInt", "34");
        doc.getRootElement().setAttribute(attrInt);
        
        Element testData = new Element("testData").setText("text 1");
        doc.getRootElement().addContent(testData);
        testData = new Element("testData").setText("text 2");
        doc.getRootElement().addContent(testData);
        
        Baz baz;
        try(JDOMStreamReader instance = new JDOMStreamReader(doc)){
            baz = (Baz)um.unmarshal(instance);
        }
        
        assertNotNull("Should unmarshal the baz", baz);
        assertEquals("Should get right data", 34, baz.getAttrInt());
        
        assertEquals("Should get right data", 2, baz.getTestData().size());
        assertEquals("Should get right data", "text 1", baz.getTestData().get(0));
        assertEquals("Should get right data", "text 2", baz.getTestData().get(1));
        
    }//end testJaxbUnmarshalling_UnmarshallsFromReader
}


