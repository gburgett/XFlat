/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert.converters;

import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.Converter;
import org.jdom.*;

/**
 *
 * @author gordon
 */
public class JDomConverters {
    
    /**
     * Registers all the string converters to the given ConversionService.
     * @param service 
     */
    public static void RegisterTo(ConversionService service){
        Converter<Content, String> cToS = new ContentToStringConverter();
        service.addConverter(Element.class, String.class, cToS);
        service.addConverter(Text.class, String.class, cToS);
        service.addConverter(CDATA.class, String.class, cToS);
        service.addConverter(DocType.class, String.class, cToS);
        service.addConverter(EntityRef.class, String.class, cToS);
        service.addConverter(Attribute.class, String.class, new AttributeToStringConverter());
        
        service.addConverter(String.class, Text.class, new StringToTextConverter());
        service.addConverter(String.class, CDATA.class, new StringToCdataConverter());
    }
    
    
    //converts all Content elements to strings
    public static class ContentToStringConverter implements Converter<Content, String>{
        @Override
        public String convert(Content source) {
            return source.getValue();
        }
    }
    
    public static class AttributeToStringConverter implements Converter<Attribute, String>{
        @Override
        public String convert(Attribute source) {
            return source.getValue();
        }
    }
    
    public static class StringToTextConverter implements Converter<String, Text>{
        @Override
        public Text convert(String source) {
            return new Text(source);
        }
    }
    
    public static class StringToCdataConverter implements Converter<String, CDATA>{
        @Override
        public CDATA convert(String source) {
            return new CDATA(source);
        }
    }
}
