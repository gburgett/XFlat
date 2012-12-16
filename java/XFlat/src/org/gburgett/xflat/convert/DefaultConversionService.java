/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author gordon
 */
public class DefaultConversionService implements ConversionService {
    
    private Map<Class<?>, ConverterEntry<?>> converters = new HashMap<>();
    
    private ReadWriteLock converterLock = new ReentrantReadWriteLock();

    
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
    public <T> T convert(Object source, Class<T> target) {
        
        Converter<?, T> converter;
        
        converterLock.readLock().lock();
        try{
            ConverterEntry<T> entry = (ConverterEntry<T>)converters.get(target);
            if(entry == null){
                if(source == null){
                    //no special converter for null, just convert to null.
                    return null;
                }
                //else
                throw new ConversionException("No converters for target " + target);
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
                    throw new ConversionException("No converter for source type " + source.getClass());
                }
            }
        }finally{
            converterLock.readLock().unlock();
        }
        
        return ((Converter<Object, T>)converter).convert(source);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<S, T> converter) {
        converterLock.readLock().lock();
        try{
            ConverterEntry<T> entry = (ConverterEntry<T>)this.converters.get(targetType);
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
            
            entry.putConverter(sourceType, converter);
        }finally{
            converterLock.readLock().unlock();
        }
    }

    @Override
    public void removeConverter(Class<?> sourceType, Class<?> targetType) {
        converterLock.readLock().lock();
        try{
            ConverterEntry<?> entry = this.converters.get(targetType);
            if(entry == null)
                return;

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
        }finally{
            converterLock.readLock().unlock();
        }
    }
    
    
    
    private static class ConverterEntry<T>
    {   
        private Class<T> targetClass;
        public Class<T> getTargetClass(){
            return targetClass;
        }
        
        private ConcurrentHashMap<Class<?>, Converter<?, T>> converters = new ConcurrentHashMap<>();
        
        
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
        public <S> Converter<S, T> getConverter(Class<S> clazz){
            Converter<?, T> ret = converters.get(clazz);
            
            return (Converter<S, T>)ret;
        }
        
        /**
         * Gets the converter for a null object.
         * @return 
         */
        public Converter<?, T> getNullConverter(){
            return converters.get(null);
        }
        
        /**
         * Returns true iff this entry has a converter for the source class
         * @param clazz
         * @return 
         */
        public boolean hasConverter(Class<?> clazz){
            return converters.containsKey(clazz);
        }
        
        /**
         * Sets a converter
         * @param <S>
         * @param clazz
         * @param converter 
         */
        public <S> void putConverter(Class<S> clazz, Converter<S, T> converter){
            this.converters.put(clazz, converter);
        }
        
        /**
         * Removes the converter associated with the given class
         * @param clazz 
         */
        public boolean removeConverter(Class<?> clazz){
            this.converters.remove(clazz);
            return this.converters.isEmpty();
        }
        
        public boolean isEmpty(){
            return this.converters.isEmpty();
        }
    }
}
