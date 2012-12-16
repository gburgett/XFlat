/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Contains a number of converters to and from strings
 * @author gordon
 */
public class StringConverters {
    
    /**
     * Registers all the string converters to the given ConversionService.
     * @param service 
     */
    public static void RegisterTo(ConversionService service){
        service.addConverter(String.class, Integer.class, new IntegerConverter());
        service.addConverter(String.class, Boolean.class, new BooleanConverter());
        service.addConverter(String.class, Double.class, new DoubleConverter());
        service.addConverter(String.class, Float.class, new FloatConverter());
        service.addConverter(String.class, Long.class, new LongConverter());
        service.addConverter(String.class, Date.class, new DateConverter());
    }
    
    public static class IntegerConverter implements Converter<String, Integer>{
        @Override
        public Integer convert(String source) {
            return Integer.valueOf(source);
        }
    }
    
    public static class BooleanConverter implements Converter<String, Boolean>{
        @Override
        public Boolean convert(String source) {
            return Boolean.valueOf(source);
        }
    }
    
    public static class LongConverter implements Converter<String, Long>{
        @Override
        public Long convert(String source) {
            return Long.valueOf(source);
        }
    }
    
    public static class FloatConverter implements Converter<String, Float>{
        @Override
        public Float convert(String source) {
            return Float.valueOf(source);
        }
    }
    
    public static class DoubleConverter implements Converter<String, Double>{
        @Override
        public Double convert(String source) {
            return Double.valueOf(source);
        }
    }
    
    public static class DateConverter implements Converter<String, Date>{
        static final DateFormat format = new java.text.SimpleDateFormat();
        
        @Override
        public Date convert(String source) {
            try{
                return format.parse(source);
            }catch(ParseException ex){
                return null;
            }
        }
    }
}
