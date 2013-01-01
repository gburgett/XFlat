/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert.converters;

import org.gburgett.xflat.convert.converters.JAXBPojoConverter;
import java.util.List;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import test.Baz;
import test.Foo;

/**
 *
 * @author gordon
 */
public class JAXBPojoConverterTest {
    
    
    @Test
    public void testCanConvert_NonJaxbAnnotatedClass_CannotConvert() throws Exception {
        System.out.println("testCanConvert_NonJaxbAnnotatedClass_CannotConvert");
        
        ConversionService mockConversion = mock(ConversionService.class);
        when(mockConversion.canConvert(any(Class.class), any(Class.class)))
                .thenReturn(false);
        
        ConversionService instance = new JAXBPojoConverter().extend(mockConversion);
        
        boolean canConvertToElement = instance.canConvert(Foo.class, Element.class);
        boolean canConvertFromElement = instance.canConvert(Element.class, Foo.class);
        
        assertFalse("Should be able to convert non-annotated class", canConvertFromElement);
        assertFalse("Should be able to convert non-annotated class", canConvertToElement);
        
    }//end testCanConvert_NonJaxbAnnotatedClass_CannotConvert
    
    @Test
    public void testCanConvert_AnnotatedClass_CanConvert() throws Exception {
        System.out.println("testCanConvert_AnnotatedClass_CanConvert");
        
        ConversionService mockConversion = mock(ConversionService.class);
        when(mockConversion.canConvert(any(Class.class), any(Class.class)))
                .thenReturn(false);
        
        ConversionService instance = new JAXBPojoConverter().extend(mockConversion);
        
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
        
        ConversionService instance = new JAXBPojoConverter().extend(mockConversion);
        
        boolean convertToFoo = instance.canConvert(Baz.class, Foo.class);
        boolean convertFromFoo = instance.canConvert(Foo.class, Baz.class);
        
        assertFalse("Should not convert to foo", convertToFoo);
        assertFalse("Should not convert from foo", convertFromFoo);
    }//end testCanConvert_ConvertingToOtherType_ReturnsFalse
    
    @Test
    public void testConvert_NonAnnotatedClass_ConversionSucceeds() throws Exception {
        System.out.println("testConvert_NonAnnotatedClass_ConversionSucceeds");
        
        ConversionService base = new DefaultConversionService();
        
        ConversionService instance = new JAXBPojoConverter().extend(base);
        
        Foo foo = new Foo();
        foo.fooInt = 18;
        foo.setId("stuff");
        
        boolean didThrow = false;
        try {
            //ACT
            Element converted = instance.convert(foo, Element.class);
        } catch (ConversionException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown ConversionException", didThrow);
        
    }//end testConvert_NonAnnotatedClass_ConversionSucceeds
    
    @Test
    public void testConvert_AnnotatedClass_Marshals() throws Exception {
        System.out.println("testConvert_AnnotatedClass_Marshals");
        
        ConversionService base = new DefaultConversionService();
        
        ConversionService instance = new JAXBPojoConverter().extend(base);
        
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
        
        ConversionService instance = new JAXBPojoConverter().extend(base);
        
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
