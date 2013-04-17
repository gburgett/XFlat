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

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
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

    private AtomicLong lastDate = new AtomicLong(0l);
    
    public static final ThreadLocal<java.text.DateFormat> format =
            new ThreadLocal<java.text.DateFormat>(){
                @Override
                public java.text.DateFormat initialValue(){
                    //SimpleDateFormat is not thread-safe
                    java.text.SimpleDateFormat ret = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                    ret.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return ret;
                }
            };
    
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
            return format.get().format(ret);
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
        
        if(Long.class.equals(clazz)){
            return ((Long)id).toString();
        }
        
        if(Date.class.equals(clazz)){
            Date ret = (Date)id;
            return Long.toString(ret.getTime());
        }
        
        throw new UnsupportedOperationException("Unknown ID type " + id.getClass());        
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        
        if(String.class.equals(idType)){
            return id;
        }
        
        if(id == null)
            return null;
        
        Long timestamp;
        try{
            timestamp = Long.valueOf(id);
        }
        catch(NumberFormatException ex){
            //try parsing as a date
            try {
                Date date = format.get().parse(id);
                timestamp = date.getTime();
            } catch (ParseException ex2) {
                //can't parse, return null;
                return null;
            }
        }
        
        if(idType.isAssignableFrom(Long.class)){
            return timestamp;
        }
        
        if(idType.isAssignableFrom(Date.class)){
            return new Date(timestamp);
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
