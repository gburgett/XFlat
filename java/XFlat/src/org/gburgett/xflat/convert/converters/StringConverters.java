/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert.converters;

import java.text.ParseException;
import java.util.Date;
import org.gburgett.xflat.convert.ConversionException;
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
        service.addConverter(String.class, Integer.class, StringToIntegerConverter);
        service.addConverter(String.class, Boolean.class, StringToBooleanConverter);
        service.addConverter(String.class, Double.class, StringToDoubleConverter);
        service.addConverter(String.class, Float.class, StringToFloatConverter);
        service.addConverter(String.class, Long.class, StringToLongConverter);
        service.addConverter(String.class, Date.class, StringToDateConverter);
        
        service.addConverter(Integer.class, String.class, ObjectToStringConverter);
        service.addConverter(Boolean.class, String.class, ObjectToStringConverter);
        service.addConverter(Double.class, String.class, ObjectToStringConverter);
        service.addConverter(Float.class, String.class, ObjectToStringConverter);
        service.addConverter(Long.class, String.class, ObjectToStringConverter);
        service.addConverter(null, String.class, ObjectToStringConverter);
        
        service.addConverter(Date.class, String.class, DateToStringConverter);
    }
    
    public static final Converter<String, Integer> StringToIntegerConverter = new Converter<String, Integer>(){
        @Override
        public Integer convert(String source) {
            try{
                return Integer.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing Integer", ex);
            }
        }
    };
    
    public static final Converter<String, Boolean> StringToBooleanConverter = new Converter<String, Boolean>(){
        @Override
        public Boolean convert(String source) {
            return Boolean.valueOf(source);
        }
    };
    
    public static final Converter<String, Long> StringToLongConverter = new Converter<String, Long>() {
        @Override
        public Long convert(String source) {
            try{
                return Long.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing Long", ex);
            }
        }
    };
    
    public static final Converter<String, Float> StringToFloatConverter = new Converter<String, Float>(){
        @Override
        public Float convert(String source) {
            try{
                return Float.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing float", ex);
            }
        }
    };
    
    public static final Converter<String, Double> StringToDoubleConverter = new Converter<String, Double>(){
        @Override
        public Double convert(String source) {
            try{
                return Double.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing double", ex);
            }
        }
    };
    
    public static final Converter<Object, String> ObjectToStringConverter = new Converter<Object, String>(){
        @Override
        public String convert(Object source) {
            if(source == null)
                return null;
            
            return source.toString();
        }
    };
    
    static final ThreadLocal<java.text.DateFormat> format =
            new ThreadLocal<java.text.DateFormat>(){
                @Override
                public java.text.DateFormat initialValue(){
                    //SimpleDateFormat is not thread-safe
                    return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
                }
            };
    public static final Converter<String, Date> StringToDateConverter = new Converter<String, Date>(){
        @Override
        public Date convert(String source) {
            try{
                return format.get().parse(source);
            }catch(ParseException ex){
                throw new ConversionException("error parsing date", ex);
            }
        }
    };
    
    public static final Converter<Date, String> DateToStringConverter = new Converter<Date, String>() {
        @Override
        public String convert(Date source) {
            return format.get().format(source);
        }
    };
}
