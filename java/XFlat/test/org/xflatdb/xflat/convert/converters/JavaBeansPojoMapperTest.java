/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.convert.converters;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.hamcrest.Matchers;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import test.Foo;
import static org.junit.Assert.*;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.DefaultConversionService;

/**
 * 
 * @author Gordon
 */
public class JavaBeansPojoMapperTest {
    
    public JavaBeansPojoMapperTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testXmlEncoder_CanEncodeObject_EncodesAsXml() throws Exception {
        System.out.println("testXmlEncoder_CanEncodeObject_EncodesAsXml");
        
        
        Foo foo = new Foo();
        foo.setId("junk");
        foo.fooInt = 17;
        
        byte[] bytes;
        try(ByteArrayOutputStream os = new ByteArrayOutputStream()){
            
            XMLEncoder encoder = new XMLEncoder(os);
            encoder.writeObject(foo);
            
            
            encoder.close();
            
            bytes = os.toByteArray();
        }
        
        System.out.write(bytes);
        
        SAXBuilder builder = new SAXBuilder();
        XMLOutputter outputter = new XMLOutputter();
        
        Document doc;
        try(ByteArrayInputStream is = new ByteArrayInputStream(bytes)){
            doc = builder.build(is);
        }
        
        assertNotNull(doc);
        
        outputter.output(doc, System.out);       
        
    }
    
    @Test
    public void testXmlEncoderDecoder_RoundTrip() throws Exception {
        System.out.println("testXmlEncoderDecoder_RoundTrip");
        
        Foo foo = new Foo();
        foo.setId("junk");
        foo.fooInt = 17;
        
        byte[] bytes;
        try(ByteArrayOutputStream os = new ByteArrayOutputStream()){
            
            XMLEncoder encoder = new XMLEncoder(os);
            encoder.writeObject(foo);
            
            
            encoder.close();
            
            bytes = os.toByteArray();
        }
        
        System.out.write(bytes);
        
        Foo foo2;
        try(ByteArrayInputStream is = new ByteArrayInputStream(bytes)){
            
            XMLDecoder decoder = new XMLDecoder(is);
            
            foo2 = (Foo)decoder.readObject();
            
        }
        
        assertNotNull(foo2);
        
        assertThat(foo, Matchers.equalTo(foo2));
    }
    
    @Test
    public void testExtendConversionService_ConversionServiceCanNowConvertToJDOM() throws Exception {
        System.out.println("testExtendConversionService_ConversionServiceCanNowConvertToJDOM");
        
        
        ConversionService service = new DefaultConversionService();
        
        JavaBeansPojoMapper instance = new JavaBeansPojoMapper();
        //act
        service = instance.extend(service);
        
        
        assertTrue(service.canConvert(Foo.class, Element.class));
        assertTrue(service.canConvert(Element.class, Foo.class));
        
        Foo foo = new Foo();
        foo.fooInt = 12;
        foo.setId("junk some more");
        
        Element element = service.convert(foo, Element.class);
        new XMLOutputter().output(element, System.out);
        
        Foo foo2 = service.convert(element, Foo.class);
        
        assertThat(foo2, Matchers.equalTo(foo));
    }
}
