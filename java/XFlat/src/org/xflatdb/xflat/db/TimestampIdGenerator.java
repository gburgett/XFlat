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
package org.xflatdb.xflat.db;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.converters.StringConverters;
import org.jdom2.Element;

/**
 * An ID Generator that generates timestamp based IDs.
 * The timestamps are stored as ISO date strings.  To uniqueify the ID, the millisecond
 * is incremented.
 * Do not use this ID generator in insert-heavy tables that may need more than 1000
 * dates per second.
 * @author gordon
 */
public class TimestampIdGenerator extends IdGenerator {

    AtomicLong lastDate = new AtomicLong(0l);
    
    @Override
    public boolean supports(Class<?> idType) {
        return String.class.equals(idType) ||
                Long.class.equals(idType) ||
                Date.class.equals(idType);
    }

    @Override
    public Object generateNewId(Class<?> idType) {
        long now = System.currentTimeMillis();
        
        long last;
        do{
            last = lastDate.get();
            if(now <= last){
                now = last + 1;
            }
            //keep going until a successful set.
        }while(!lastDate.compareAndSet(last, now));
        
        if(Long.class.equals(idType)){
            return now;
        }
        Date ret = new Date(now);
        if(Date.class.equals(idType)){
            return ret;
        }
        if(String.class.equals(idType)){
            try {
                return StringConverters.DateToStringConverter.convert(ret);
            } catch (ConversionException ex) {
                //Should never happen
                throw new XFlatException("Unable to convert Integer to String", ex);
            }
        }
        
        throw new UnsupportedOperationException("Unsupported ID type " + idType);
    }

    @Override
    public String idToString(Object id) {
        if(id == null){
            return "0";
        }
        Class<?> clazz = id.getClass();
        if(String.class.equals(clazz)){
            return (String)id;
        }
        Date ret;
        if(Date.class.equals(clazz)){
            ret = (Date)id;
        }
        else if(Long.class.equals(clazz)){
            ret = new Date((Long)id);
        }
        else{
            throw new UnsupportedOperationException("Unknown ID type " + id.getClass());
        }
        try {
            return StringConverters.DateToStringConverter.convert(ret);
        } catch (ConversionException ex) {
            return "0";
        }
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        
        if(String.class.equals(idType)){
            return id;
        }
        
        Date date;
        if(id == null){
            date = new Date(0);
        }
        else{
            try {
                date = StringConverters.StringToDateConverter.convert(id);
            } catch (ConversionException ex) {
                date = new Date(0);
            }
        }
        
        if(Date.class.equals(idType)){
            return date;
        }
        if(Long.class.equals(idType)){
            return date.getTime();
        }
        
        throw new UnsupportedOperationException("Unknown ID type " + idType);
    }
    
    @Override
    public void saveState(Element state){
        state.setAttribute("maxId", Long.toString(this.lastDate.get()), XFlatDatabase.xFlatNs);
    }
    
    @Override
    public void loadState(Element state){
        String maxId = state.getAttributeValue("maxId", XFlatDatabase.xFlatNs);
        this.lastDate.set(Long.parseLong(maxId));
    }
}
