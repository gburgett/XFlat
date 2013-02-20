/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.convert.converters;

import java.util.Date;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;

/**
 * Contains a number of converters to and from Date.
 * @author Gordon
 */
public class DateConverters {
    
    
    /**
     * Registers all the date converters to the given ConversionService.
     * @param service 
     */
    public static void registerTo(ConversionService service){
        service.addConverter(Date.class, Long.class, DateToLongConverter);
        service.addConverter(Long.class, Date.class, LongToDateConverter);
    }
    
    public static final Converter<Date, Long> DateToLongConverter = new Converter<Date, Long>() {
        @Override
        public Long convert(Date source) throws ConversionException {
            return source.getTime();
        }
    };
    
    public static final Converter<Long, Date> LongToDateConverter = new Converter<Long, Date>() {
        @Override
        public Date convert(Long source) throws ConversionException {
            return new Date(source);
        }
    };
}
