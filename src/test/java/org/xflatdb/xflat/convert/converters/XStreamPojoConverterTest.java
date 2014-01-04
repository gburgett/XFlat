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
package org.xflatdb.xflat.convert.converters;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.JDom2Reader;
import com.thoughtworks.xstream.io.xml.JDom2Writer;
import java.util.List;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.junit.Test;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.DefaultConversionService;
import test.Baz;
import test.Foo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Gordon
 */
public class XStreamPojoConverterTest {
        
    @Test
    public void testXStream_PojoToXml_MarshalsCorrectly() throws Exception {
        System.out.println("testXStream_PojoToXml_MarshalsCorrectly");
        
        JDom2Writer writer = new JDom2Writer();
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        foo.setId("blah");
        
        XStream xstream = new XStream();
        
        xstream.marshal(foo, writer);
        
        Element element = writer.getTopLevelNode();
        
        XMLOutputter outputter = new XMLOutputter();
        System.out.println(outputter.outputString(element));
        
        assertNotNull(element);
        assertEquals("test.Foo", element.getName());
        assertEquals("17", element.getChildText("fooInt"));
    }    
    
    @Test
    public void testXStream_XmlToPojo_UnmarshalsCorrectly() throws Exception {
        System.out.println("testXStream_XmlToPojo_UnmarshalsCorrectly");
        
        Element element = new Element("test.Foo");
        element.addContent(new Element("id").setText("blah"));
        element.addContent(new Element("fooInt").setText("23"));
        
        JDom2Reader reader = new JDom2Reader(element);
        
        XStream xstream = new XStream();
        
        Foo foo = (Foo)xstream.unmarshal(reader);
        
        assertNotNull(foo);
        assertEquals("blah", foo.getId());
        assertEquals(23, foo.fooInt);
    }
    
    @Test
    public void testCanConvert_AnnotatedClass_CanConvert() throws Exception {
        System.out.println("testCanConvert_AnnotatedClass_CanConvert");
        
        ConversionService mockConversion = mock(ConversionService.class);
        when(mockConversion.canConvert(any(Class.class), any(Class.class)))
                .thenReturn(false);
        
        ConversionService instance = new XStreamPojoConverter().extend(mockConversion);
        
        boolean canConvertToElement = instance.canConvert(Baz.class, Element.class);
        boolean canConvertFromElement = instance.canConvert(Element.class, Baz.class);
        
        assertTrue("Should be able to convert annotated class", canConvertFromElement);
        assertTrue("Should be able to convert annotated class", canConvertToElement);
    }//end testCanConvert_AnnotatedClass_CanConvert
    
    @Test
    public void testCanConvert_ConvertingToOtherType_ReturnsFalse() throws Exception {
        System.out.println("testCanConvert_ConvertingToOtherType_ReturnsFalse");
        
        
        ConversionService mockConversion = mock(ConversionService.class);
        when(mockConversion.canConvert(any(Class.class), any(Class.class)))
                .thenReturn(false);
        
        ConversionService instance = new XStreamPojoConverter().extend(mockConversion);
        
        boolean convertToFoo = instance.canConvert(Baz.class, Foo.class);
        boolean convertFromFoo = instance.canConvert(Foo.class, Baz.class);
        
        assertFalse("Should not convert to foo", convertToFoo);
        assertFalse("Should not convert from foo", convertFromFoo);
    }//end testCanConvert_ConvertingToOtherType_ReturnsFalse
    
    @Test
    public void testConvert_AnnotatedClass_Marshals() throws Exception {
        System.out.println("testConvert_AnnotatedClass_Marshals");
        
        ConversionService base = new DefaultConversionService();
        
        ConversionService instance = new XStreamPojoConverter().extend(base);
        
        Baz baz = new Baz();
        baz.setAttrInt(32);
        baz.getTestData().add("test 1");
        baz.getTestData().add("test 2");
        baz.setId("test id");
        
        //ACT
        Element converted = instance.convert(baz, Element.class);
        
        System.out.println(new XMLOutputter().outputString(converted));
        
        assertEquals("should have correct name", "baz", converted.getName());
        assertEquals("should have correct attr value", "32", converted.getAttributeValue("attrInt"));
        
        List<Element> children = converted.getChildren("testData");
        assertEquals("should have list data", 2, children.size());
        assertEquals("should have list data", "test 1", children.get(0).getText());
        assertEquals("should have list data", "test 2", children.get(1).getText());
        
        assertNull("should not have ID element", converted.getChild("id"));

    }//end testConvert_AnnotatedClass_Marshals

    @Test
    public void testConvert_AnnotatedClass_Unmarshals() throws Exception {
        System.out.println("testConvert_AnnotatedClass_Unmarshals");
        
        ConversionService base = new DefaultConversionService();
        
        ConversionService instance = new XStreamPojoConverter().extend(base);
        
        Element baz = new Element("baz");
        baz.setAttribute("attrInt", "56");
        Element listData = new Element("testData");
        listData.setText("data 1");
        baz.addContent(listData);
        listData = new Element("testData");
        listData.setText("data 2");
        baz.addContent(listData);
        
        //ACT
        Baz converted = instance.convert(baz, Baz.class);
        
        assertEquals("should have correct attr value", 56, converted.getAttrInt());
        
        assertEquals("should have list data", 2, converted.getTestData().size());
        assertEquals("should have list data", "data 1", converted.getTestData().get(0));
        assertEquals("should have list data", "data 2", converted.getTestData().get(1));
        
        assertNull("should not have ID element", converted.getId());
    }//end testConvert_AnnotatedClass_Unmarshals
}
