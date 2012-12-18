/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert.converters;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.Converter;

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
        service.addConverter(String.class, Integer.class, new StringToIntegerConverter());
        service.addConverter(String.class, Boolean.class, new StringToBooleanConverter());
        service.addConverter(String.class, Double.class, new StringToDoubleConverter());
        service.addConverter(String.class, Float.class, new StringToFloatConverter());
        service.addConverter(String.class, Long.class, new StringToLongConverter());
        service.addConverter(String.class, Date.class, new StringToDateConverter());
        
        Converter<Object, String> ots = new ObjectToStringConverter();
        service.addConverter(Integer.class, String.class, ots);
        service.addConverter(Boolean.class, String.class, ots);
        service.addConverter(Double.class, String.class, ots);
        service.addConverter(Float.class, String.class, ots);
        service.addConverter(Long.class, String.class, ots);
        service.addConverter(null, String.class, ots);
        
        service.addConverter(Date.class, String.class, new DateToStringConverter());
    }
    
    public static class StringToIntegerConverter implements Converter<String, Integer>{
        @Override
        public Integer convert(String source) {
            return Integer.valueOf(source);
        }
    }
    
    public static class StringToBooleanConverter implements Converter<String, Boolean>{
        @Override
        public Boolean convert(String source) {
            return Boolean.valueOf(source);
        }
    }
    
    public static class StringToLongConverter implements Converter<String, Long>{
        @Override
        public Long convert(String source) {
            return Long.valueOf(source);
        }
    }
    
    public static class StringToFloatConverter implements Converter<String, Float>{
        @Override
        public Float convert(String source) {
            return Float.valueOf(source);
        }
    }
    
    public static class StringToDoubleConverter implements Converter<String, Double>{
        @Override
        public Double convert(String source) {
            return Double.valueOf(source);
        }
    }
    
    public static class ObjectToStringConverter implements Converter<Object, String>{
        @Override
        public String convert(Object source) {
            if(source == null)
                return null;
            
            return source.toString();
        }
    }
    
    static final ThreadLocal<java.text.DateFormat> format =
            new ThreadLocal<java.text.DateFormat>(){
                @Override
                public java.text.DateFormat initialValue(){
                    //SimpleDateFormat is not thread-safe
                    return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
                }
            };
    public static class StringToDateConverter implements Converter<String, Date>{
        @Override
        public Date convert(String source) {
            try{
                return format.get().parse(source);
            }catch(ParseException ex){
                return null;
            }
        }
    }
    
    public static class DateToStringConverter implements Converter<Date, String> {
        @Override
        public String convert(Date source) {
            return format.get().format(source);
        }
    }
}
