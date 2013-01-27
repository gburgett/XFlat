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
package org.xflatdb.xflat.convert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author gordon
 */
public class DefaultConversionService implements ConversionService {
    
    private Map<Class<?>, ConverterEntry<?>> converters = new HashMap<>();
    
    private ReadWriteLock converterLock = new ReentrantReadWriteLock();

    public DefaultConversionService(){
    }
    
    @Override
    public boolean canConvert(Class<?> source, Class<?> target) {
        converterLock.readLock().lock();
        try{
            ConverterEntry<?> entry = converters.get(target);
            if(entry == null)
                return false;

            return entry.hasConverter(source);
        }finally{
            converterLock.readLock().unlock();
        }
    }

    @Override
    public <T> T convert(Object source, Class<T> target) throws ConversionException {        
        Converter<?, ?> converter;
        
        converterLock.readLock().lock();
        try{
            ConverterEntry<T> entry = (ConverterEntry<T>)converters.get(target);
            if(entry == null){
                if(source == null){
                    //no special converter for null, just convert to null.
                    return null;
                }
                //else
                throw new ConversionNotSupportedException("No converters for target " + target);
            }

            if(source == null){
                converter = entry.getNullConverter();
                if(converter == null){
                    //no special converter for null, assume null
                    return null;
                }
            }
            else{
                converter = entry.getConverter(source.getClass());
                if(converter == null){
                    throw new ConversionNotSupportedException("No converter for source type " + source.getClass());
                }
            }
        }finally{
            converterLock.readLock().unlock();
        }
        
        return ((Converter<Object, T>)converter).convert(source);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
        ConverterEntry<T> entry;
        
        converterLock.readLock().lock();
        try{
            
            entry = (ConverterEntry<T>)this.converters.get(targetType);
            
        }finally{
            converterLock.readLock().unlock();
        }
        
        if(entry == null){
            converterLock.writeLock().lock();
            try{
                //doublecheck after locking
                entry = (ConverterEntry<T>)this.converters.get(targetType);
                if(entry == null){
                    entry = new ConverterEntry(targetType);
                    this.converters.put(targetType, entry);
                }
            }finally{
                converterLock.writeLock().unlock();
            }
        }
            
        //concurrency managed by the entry
        entry.putConverter(sourceType, converter);
    }

    @Override
    public void removeConverter(Class<?> sourceType, Class<?> targetType) {
        ConverterEntry<?> entry;
        converterLock.readLock().lock();
        try{
            entry = this.converters.get(targetType);
        }finally{
            converterLock.readLock().unlock();
        }
        
        if(entry == null)
            return;

        //concurrency managed by the entry
        boolean isEmpty = entry.removeConverter(sourceType);
        if(isEmpty){
            converterLock.writeLock().lock();
            try{
                //doublecheck inside lock
                if(entry.isEmpty()){
                    this.converters.remove(targetType);
                }
            }finally{
                converterLock.writeLock().unlock();
            }
        }
    }
    
    private static class ConverterEntry<T>
    {   
        private Class<T> targetClass;
        public Class<T> getTargetClass(){
            return targetClass;
        }
        
        private Map<Class<?>, Converter<?, ?>> converters = new HashMap<>();
        
        private Converter<?, ?> nullConverter;
        
        public ConverterEntry(Class<T> targetClass){
            this.targetClass = targetClass;
        }
        
        /**
         * Gets a converter for the source class to the target class
         * defined by this entry.
         * @param <S> The generic type of the source class.
         * @param clazz The source class
         * @return A converter for the source class to this entry's target class
         */
        public synchronized <S> Converter<?, ?> getConverter(Class<S> clazz){
            Converter<?, ?> ret = converters.get(clazz);
            
            return ret;
        }
        
        /**
         * Gets the converter for a null object.
         * @return 
         */
        public synchronized Converter<?, ?> getNullConverter(){
            return nullConverter;
        }
        
        /**
         * Returns true iff this entry has a converter for the source class
         * @param clazz
         * @return 
         */
        public synchronized boolean hasConverter(Class<?> clazz){
            return converters.containsKey(clazz);
        }
        
        /**
         * Sets a converter
         * @param <S>
         * @param clazz
         * @param converter 
         */
        public synchronized <S> void putConverter(Class<S> clazz, Converter<? super S, ? extends T> converter){
            if(clazz == null){
                nullConverter = converter;
                return;
            }
            
            this.converters.put(clazz, converter);
        }
        
        /**
         * Removes the converter associated with the given class
         * @param clazz 
         */
        public synchronized boolean removeConverter(Class<?> clazz){
            if(clazz == null){
                nullConverter = null;
                return this.converters.isEmpty();
            }
                
            this.converters.remove(clazz);
            return this.converters.isEmpty() && nullConverter == null;
        }
        
        public synchronized boolean isEmpty(){
            return this.converters.isEmpty() && nullConverter == null;
        }
    }
}
