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
