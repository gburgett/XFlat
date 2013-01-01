/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.CDATA;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author gordon
 */
public class JDOMStreamWriterTest {
    
    public JDOMStreamWriterTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testEmptyRootElement_DocumentHasEmptyRoot() throws Exception {
        System.out.println("testEmptyRootElement_DocumentHasEmptyRoot");
        
        Document doc;
        try(JDOMStreamWriter writer = new JDOMStreamWriter()){
            writer.writeStartDocument();
            
            writer.writeEmptyElement("testroot");
            
            writer.writeEndDocument();
            
            doc = writer.getDocument();
        }
        
        assertNotNull("Should have root element", doc.getRootElement());
        assertEquals("Should have correct name", "testroot", doc.getRootElement().getName());
        assertEquals("Should have no content", 0, doc.getRootElement().getContentSize());
    }//end testEmptyRootElement_DocumentHasEmptyRoot
    
    @Test
    public void testMultipleElementsWithText_DocHasElements() throws Exception {
        System.out.println("testMultipleElementsWithText_DocHasElements");
        
        Document doc;
        try(JDOMStreamWriter writer = new JDOMStreamWriter()){
            writer.writeStartDocument();
            writer.writeStartElement("testroot");
            
                writer.writeStartElement("testdata");
                    writer.writeAttribute("id", "1");
                writer.writeEndElement();
                
                writer.writeStartElement("testdata");
                    writer.writeAttribute("id", "2");
                    writer.writeStartElement("deep");
                        writer.writeStartElement("deeper");
                            writer.writeCData("Textual CDATA");
                        writer.writeEndElement();
                    writer.writeEndElement();
                writer.writeEndElement();
                
            writer.writeEndElement();
            writer.writeEndDocument();
            
            doc = writer.getDocument();
        }
        
        assertNotNull("Should have root element", doc.getRootElement());
        assertEquals("Should have correct name", "testroot", doc.getRootElement().getName());
        assertEquals("should have content", 2, doc.getRootElement().getContentSize());
        
        List<Element> content = doc.getRootElement().getChildren();
        assertThat("should have correct names", content, Matchers.contains(
                hasName("testdata"), hasName("testdata")));
        assertThat("should have correct ID attributes", content, Matchers.containsInAnyOrder(
                hasAttribute("id", "1"), hasAttribute("id", "2")));
        
        List<Content> deepContent = content.get(1).getChild("deep").getChild("deeper").getContent();
        assertThat("should have CDATA element", deepContent,
                Matchers.contains(Matchers.instanceOf(CDATA.class)));
        assertEquals("should have super deep data", "Textual CDATA", deepContent.get(0).getValue());
    }//end testMultipleElementsWithText_DocHasElements
    
    
    private Matcher<Element> hasName(final String name){
        return new TypeSafeMatcher<Element>(){

            @Override
            protected boolean matchesSafely(Element item) {
                return item.getName().equals(name);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an element that has name ").appendText(name);
            }
            
        };
    }

    private Matcher<Element> hasAttribute(final String name, final String value){
        return new TypeSafeMatcher<Element>(){

            @Override
            protected boolean matchesSafely(Element item) {
                return value.equals(item.getAttributeValue(name));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an element that has attribute ").appendText(name)
                        .appendText(" with value ").appendText(value);
            }
            
        };
    }
}
