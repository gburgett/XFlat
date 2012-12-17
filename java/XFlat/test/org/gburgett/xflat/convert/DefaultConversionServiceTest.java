/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

import java.util.Date;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static org.junit.Assert.*;


/**
 *
 * @author gordon
 */
public class DefaultConversionServiceTest {
 
    @Test
    public void testCanConvert_NoConverter_ReturnsFalse() throws Exception {
        System.out.println("testCanConvert_NoConverter_ReturnsFalse");
        
        DefaultConversionService instance = new DefaultConversionService();
        
        boolean result = instance.canConvert(Boolean.class, Date.class);
        
        assertFalse("shouldnt be able to convert", result);
        
    }//end testCanConvert_NoConverter_ReturnsFalse
    
    @Test
    public void testCanConvert_HasConverter_ReturnsTrue() throws Exception {
        System.out.println("testCanConvert_HasConverter_ReturnsTrue");
        
        DefaultConversionService instance = new DefaultConversionService();
        
        boolean result = instance.canConvert(String.class, Integer.class);
        
        assertTrue("Should be able to convert string to int", result);
    }//end testCanConvert_HasConverter_ReturnsTrue
    
    @Test
    public void testConvert_NoConverter_ThrowsConversionException() throws Exception {
        System.out.println("testConvert_NoConverter_ThrowsConversionException");
        
        DefaultConversionService instance = new DefaultConversionService();
        
        boolean didThrow = false;
        try {
            //ACT
            instance.convert(Boolean.FALSE, Date.class);
        } catch (ConversionException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown ConversionException", didThrow);
    }//end testConvert_NoConverter_ThrowsConversionException
    
    @Test
    public void testConvert_HasConverter_ConvertsCorrectly() throws Exception {
        System.out.println("testConvert_HasConverter_ConvertsCorrectly");
        
        DefaultConversionService instance = new DefaultConversionService();
        
        Integer converted = instance.convert("7", Integer.class);
        
        assertNotNull(converted);
        assertEquals("Should have converted properly", new Integer(7), converted);
    }//end testConvert_HasConverter_ConvertsCorrectly
    
    @Test
    public void testAddConverter_canConvertIsTrue() throws Exception {
        System.out.println("testAddConverter_canConvertIsTrue");
        
        Converter mockConverter = mock(Converter.class);
        
        DefaultConversionService instance = new DefaultConversionService();
        
        instance.addConverter(Boolean.class, Date.class, mockConverter);
        
        assertTrue("Can convert", instance.canConvert(Boolean.class, Date.class));
        
    }//end testAddConverter_canConvertIsTrue
    
    @Test
    public void testAddConverter_convertInvokesConverter() throws Exception {
        System.out.println("testAddConverter_convertInvokesConverter");
        
        Converter mockConverter = mock(Converter.class);
        when(mockConverter.convert(isA(Boolean.class)))
                .thenReturn(new Date(17));
        
        DefaultConversionService instance = new DefaultConversionService();
        
        instance.addConverter(Boolean.class, Date.class, mockConverter);
        
        Date converted = instance.convert(Boolean.TRUE, Date.class);
        
        assertNotNull(converted);
        assertEquals("Should have invoked converter", new Date(17), converted);
    }//end testAddConverter_convertInvokesConverter
}
