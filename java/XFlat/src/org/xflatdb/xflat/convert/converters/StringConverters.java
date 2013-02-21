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

import java.text.ParseException;
import java.util.Date;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;

/**
 * Contains a number of converters to and from strings
 * @author gordon
 */
public class StringConverters {
    
    /**
     * Registers all the string converters to the given ConversionService.
     * @param service 
     */
    public static void registerTo(ConversionService service){
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
        public Integer convert(String source) throws ConversionException {
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
        public Long convert(String source) throws ConversionException {
            //is it all numbers? Longs can also represent dates.
            for(int i = 0; i < source.length(); i++){
                if(!Character.isDigit(source.codePointAt(i))){
                    
                    //it's a date format string or nothing
                    try{                
                        return format.get().parse(source).getTime();
                    }catch(ParseException ex){
                        //could also be represented as a long
                        throw new ConversionException("error parsing date", ex);
                    }
                }
            }
                        
            try{
                return Long.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing Long", ex);
            }
        }
    };
    
    public static final Converter<String, Float> StringToFloatConverter = new Converter<String, Float>(){
        @Override
        public Float convert(String source) throws ConversionException {
            try{
                return Float.valueOf(source);
            }catch(NumberFormatException ex){
                throw new ConversionException("Error parsing float", ex);
            }
        }
    };
    
    public static final Converter<String, Double> StringToDoubleConverter = new Converter<String, Double>(){
        @Override
        public Double convert(String source) throws ConversionException {
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
                    return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                }
            };
    public static final Converter<String, Date> StringToDateConverter = new Converter<String, Date>(){
        @Override
        public Date convert(String source) throws ConversionException {
            //first, is it all digits?  Dates can be represented as longs.
            for(int i = 0; i < source.length(); i++){
                if(!Character.isDigit(source.codePointAt(i))){
                    
                    //it's a date format string or nothing
                    
                    try{                
                        return format.get().parse(source);
                    }catch(ParseException ex){
                        //could also be represented as a long
                        throw new ConversionException("error parsing date", ex);
                    }
                }
            }
            
            //it's all digits - it's a long or nothing
            try{
                return new Date(Long.parseLong(source));
            }
            catch(NumberFormatException ex2){
                throw new ConversionException("error parsing date", ex2);
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
