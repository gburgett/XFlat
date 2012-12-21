/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.converters.JDomConverters;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathDiagnostic;
import org.jdom2.xpath.XPathFactory;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import test.Foo;

/**
 *
 * @author gordon
 */
public class XpathUpdateTests {
    
    private ConversionService conversionService;
    
    private XPathFactory xpath;
    
    private XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
    
    @Before
    public void setup(){
        this.conversionService = new DefaultConversionService();
        StringConverters.RegisterTo(conversionService);
        JDomConverters.RegisterTo(conversionService);
        
        this.xpath = XPathFactory.instance();
    }
    
    @Test
    public void testUpdate_FirstChild_ModifiesText() throws Exception {
        System.out.println("testUpdate_FirstChild_ModifiesText");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(data);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("data"), "other value");
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("data"));
        assertEquals("Should have changed the value", "other value", row.getChild("data").getValue());
    }//end testUpdate_FirstChild_ModifiesText
    
    @Test
    public void testUpdate_SetChildAttribute_ModifiesAttrValue() throws Exception {
        System.out.println("testUpdate_SetChildAttribute_ModifiesAttrValue");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        data.setAttribute("val", "value");
        row.addContent(data);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("data/@val"), "other value");
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("data"));
        assertEquals("Should have changed the value", "other value", row.getChild("data").getAttributeValue("val"));
    }//end testUpdate_SetChildAttribute_ModifiesAttrValue
    
    @Test
    public void testUpdate_SetElementValueToNull_DeletesContent() throws Exception {
        System.out.println("testUpdate_SetElementValueToNull_DeletesContent");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(data);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("data"), null);
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("data"));
        assertEquals("Should have removed the content", 0, row.getChild("data").getContentSize());
    }//end testUpdate_SetElementValueToNull_DeletesContent
    
    @Test
    public void testUpdate_SetAttributeValueToNull_SetsToEmptyString() throws Exception {
        System.out.println("testUpdate_SetAttributeValueToNull_SetsToEmptyString");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        data.setAttribute("val", "value");
        row.addContent(data);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("data/@val"), null);
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("data"));
        assertEquals("Should have changed the value", "", row.getChild("data").getAttributeValue("val"));
    }//end testUpdate_SetAttributeValueToNull_SetsToEmptyString
    
    @Test
    public void testUpdate_SetElementContentToFoo_SetsToConvertedFoo() throws Exception {
        System.out.println("testUpdate_SetElementContentToFoo_SetsToConvertedFoo");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(data);
        
        Foo setTo = new Foo();
        setTo.fooInt = 13;
        
        conversionService.addConverter(Foo.class, Content.class, new Foo.ToElementConverter());
        conversionService.addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("data"), setTo);
        update.setConversionService(conversionService);
        
        //ACT
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        
        assertEquals("Should have added the converted content", "13",
                row.getChild("data").getChild("foo").getChild("fooInt").getValue());
    }//end testUpdate_SetElementContentToFoo_SetsToConvertedFoo
    
    @Test
    public void testUpdate_UnsetElement_RemovesElement() throws Exception {
        System.out.println("testUpdate_UnsetElement_RemovesElement");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(new Element("deep"));
        row.getChild("deep").addContent(data);
        
        XpathUpdate update = XpathUpdate.unset(xpath.compile("deep/data"));
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("deep"));
        assertNull("Should have removed the element", row.getChild("deep").getChild("data"));
    }//end testUpdate_UnsetElement_RemovesElement
    
    @Test
    public void testUpdate_UnsetAttribute_RemovesAttribute() throws Exception {
        System.out.println("testUpdate_UnsetAttribute_RemovesAttribute");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        data.setAttribute("val", "value");
        row.addContent(data);
        
        XpathUpdate update = XpathUpdate.unset(xpath.compile("data/@val"));
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        assertTrue("Should have applied the update", updateCount > 0);
        assertNotNull(row.getChild("data"));
        assertNull("Should have deleted the attribute", row.getChild("data").getAttribute("val"));
    }//end testUpdate_UnsetAttribute_RemovesAttribute
    
    @Test
    public void testUpdate_SetMultiMatch_UpdatesAll() throws Exception {
        System.out.println("testUpdate_SetMultiMatch_UpdatesAll");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(new Element("deep"));
        row.getChild("deep").addContent(data);
        row.addContent(new Element("other"));
        row.getChild("other").addContent(data.clone());
        
        XpathUpdate update = XpathUpdate.set(xpath.compile(".//data"), 17);
        update.setConversionService(conversionService);
        
        //ACT
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        //ASSERT
        
        assertEquals("Should have updated both", 2, updateCount);
        assertEquals("Should have updated first value", "17", row.getChild("deep").getChild("data").getValue());
        assertEquals("Should have updated second value", "17", row.getChild("other").getChild("data").getValue());
    }//end testUpdate_SetMultiMatch_UpdatesAll
    
    @Test
    public void testUpdate_MultipleUpdates_UpdatesAll() throws Exception {
        System.out.println("testUpdate_MultipleUpdates_UpdatesAll");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(new Element("deep"));
        row.getChild("deep").addContent(data);
        row.addContent(new Element("other"));
        row.getChild("other").addContent(data.clone());
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("deep/data"), 17)
                                    .andSet(xpath.compile("other/data"), 34.2);
        update.setConversionService(conversionService);
        
        //ACT
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        //ASSERT
        
        assertEquals("Should have updated both", 2, updateCount);
        assertEquals("Should have updated first value", "17", row.getChild("deep").getChild("data").getValue());
        assertEquals("Should have updated second value", "34.2", row.getChild("other").getChild("data").getValue());
    }//end testUpdate_MultipleUpdates_UpdatesAll
    
    @Test
    public void testUpdate_SetAndUnset_UpdatesBoth() throws Exception {
        System.out.println("testUpdate_SetAndUnset_UpdatesBoth");
        
        Element row = new Element("row");
        Element data = new Element("data");
        
        data.setText("value");
        row.addContent(new Element("deep"));
        row.getChild("deep").addContent(data);
        row.addContent(new Element("other"));
        row.getChild("other").addContent(data.clone());
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("deep/data"), 17)
                                .andUnset(xpath.compile("other/data"));
        update.setConversionService(conversionService);
        
        //ACT
        int updateCount = update.apply(row);
        System.out.println(outputter.outputString(row));
        
        //ASSERT
        
        assertEquals("Should have updated both", 2, updateCount);
        assertEquals("Should have updated first value", "17", row.getChild("deep").getChild("data").getValue());
        assertNull("Should have deleted second value", row.getChild("other").getChild("data"));
    }//end testUpdate_SetAndUnset_UpdatesBoth
}
