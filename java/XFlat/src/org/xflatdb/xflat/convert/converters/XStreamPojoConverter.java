/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.convert.converters;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.JDom2Reader;
import com.thoughtworks.xstream.io.xml.JDom2Writer;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jdom2.Element;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionNotSupportedException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.convert.PojoConverter;

/**
 * A PojoConverter that uses XStream for marshalling and unmarshalling.
 * @author Gordon
 */
public class XStreamPojoConverter implements PojoConverter {

    
    private final XStream xstream;
    /**
     * Gets the XStream instance that performs the POJO mapping.
     * @return 
     */
    public XStream getXStream(){
        return xstream;
    }
    
    private final boolean isThreadSafe;
    
    private final ReadWriteLock lock;
    
    /**
     * Creates a new XStream instance with the default options.
     * This instance is not thread safe on serializing new objects,
     * since it relies on annotation processing.
     */
    public XStreamPojoConverter(){
        this.xstream = new XStream();
        this.isThreadSafe = false;
        lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Creates a pojo converter that uses the given configured XStream instance.
     * <p/>
     * From the XStream documentation:
     * <pre>
     * The XStream instance is thread-safe. That is, once the XStream instance has been created and configured,
     * it may be shared across multiple threads allowing objects to be serialized/deserialized concurrently. 
     * Note, that this only applies if annotations are not auto-detected on -the-fly.
     * </pre>
     * Therefore, if you are using annotations to control serialization, ensure
     * that you set "isThreadSafe" to false.
     * @param xstream 
     */
    public XStreamPojoConverter(XStream xstream, boolean isThreadSafe){
        this.xstream = xstream;
        this.isThreadSafe = isThreadSafe;
        if(!isThreadSafe){
            lock = new ReentrantReadWriteLock();
        }
        else{
            lock = null;
        }
    }
    
    @Override
    public ConversionService extend(ConversionService service) {
        return new XStreamConversionService(service); 
    }
    
    private class XStreamConversionService implements ConversionService {

        ConversionService base;        
        
        public XStreamConversionService(ConversionService base){
            this.base = base;            
        }
        
        @Override
        public boolean canConvert(Class<?> source, Class<?> target) {
            if(!base.canConvert(source, target)){
                
                if(Element.class.equals(target))
                    return true;
                else if(Element.class.equals(source)){
                    return true;
                }
                else{
                    //can't convert
                    return false;
                }
            }
            //else the base can convert
            
            return true;
        }

        @Override
        public <T> T convert(Object source, Class<T> target) throws ConversionException {
             try{
                return base.convert(source, target);
            }
            catch(ConversionNotSupportedException ex){
                if(source == null){
                    throw ex;
                }
                
                //the base class does not support the conversion - try to make converters
                if(Element.class.equals(target))
                    return makeConvertersAndRetry(source.getClass(), source, target);
                else if(Element.class.equals(source.getClass())){
                    return makeConvertersAndRetry(target, source, target);
                }
                else{
                    //can't convert
                    throw ex;
                }
            }
        }

        @Override
        public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
            base.addConverter(sourceType, targetType, converter);
        }

        @Override
        public void removeConverter(Class<?> sourceType, Class<?> targetType) {
            base.removeConverter(sourceType, targetType);
        }
    
        private <T, U> T makeConvertersAndRetry(Class<U> clazz, Object source, Class<T> target) throws ConversionException {
            if(!isThreadSafe){
                //need to synchronize
                lock.writeLock().lock();
                try{
                    //doublecheck if another thread got here first
                    if(!base.canConvert(clazz, Element.class)){
                        //any other threads will stall on the readlock
                        base.addConverter(clazz, Element.class, threadSafeMarshallingConverter);
                        XStreamThreadSafeUnmarshallingConverter<U> unmarshaller = new XStreamThreadSafeUnmarshallingConverter<>(clazz);
                        base.addConverter(Element.class, clazz, unmarshaller);

                        //process the annotations
                        xstream.processAnnotations(clazz);
                    }
                }
                finally{
                    lock.writeLock().unlock();
                }
                
                //invoke the converters via the base
                return base.convert(source, target);                
            }
            else{
                //no need to synchronize, the XStream class is thread safe.
                //Since we've said thread-safe, we also have no need to process annotations.
                
                base.addConverter(clazz, Element.class, marshallingConverter);
                XStreamUnmarshallingConverter<U> unmarshaller = new XStreamUnmarshallingConverter<>(clazz);
                base.addConverter(Element.class, clazz, unmarshaller);
                
                if(Element.class.equals(target))
                    return (T)marshallingConverter.convert(source);
                else {
                    return (T)unmarshaller.convert((Element)source);
                }
            }
        }
        
        
    }
    
    //marshalling singletons
    private final Converter<Object, Element> marshallingConverter = new XStreamMarshallingConverter();
    
    private final Converter<Object, Element> threadSafeMarshallingConverter = new XStreamThreadSafeMarshallingConverter();    
    
    /**
     * A converter that performs marshalling to XML
     */
    private class XStreamMarshallingConverter implements Converter<Object, Element>{

        @Override
        public Element convert(Object source) throws ConversionException {            
            
            JDom2Writer writer = new JDom2Writer();
            try{
                xstream.marshal(source, writer);
            }
            catch(Exception ex){
                throw new ConversionException("Unable to marshal " + source.getClass() + " to xml", ex);
            }
            
            return writer.getTopLevelNode();
        }        
    }
        
    /**
     * Wraps the marshalling converter in a global readlock for the XStream instance,
     * since it is not thread safe when reading annotations.
     */
    private class XStreamThreadSafeMarshallingConverter implements Converter<Object, Element>{

        @Override
        public Element convert(Object source) throws ConversionException {            
            
            lock.readLock().lock();
            try{
                return marshallingConverter.convert(source);
            }
            finally{
                lock.readLock().unlock();
            }
        }        
    }
    
    /**
     * A converter that performs unmarshalling from XML
     * @param <T> 
     */
    private class XStreamUnmarshallingConverter<T> implements Converter<Element, T>{

        Class<T> clazz;
        
        public XStreamUnmarshallingConverter(Class<T> clazz){
            this.clazz = clazz;
        }
        
        @Override
        public T convert(Element source) throws ConversionException {
            
            JDom2Reader reader = new JDom2Reader(source);
            try{
                T ret = (T)xstream.unmarshal(reader);
                return ret;                
                
            }
            catch(Exception ex){
                throw new ConversionException("Unable to unmarshal " + clazz + " from xml", ex);
            }
        }
        
    }
    
    /**
     * Wraps the unmarshalling converter in a global readlock for the XStream instance,
     * since it is not thread safe when reading annotations.
     * @param <T> 
     */
    private class XStreamThreadSafeUnmarshallingConverter<T> implements Converter<Element, T>{

        Class<T> clazz;
        
        XStreamUnmarshallingConverter<T> innerConverter;
        
        public XStreamThreadSafeUnmarshallingConverter(Class<T> clazz){
            this.clazz = clazz;
            this.innerConverter = new XStreamUnmarshallingConverter<>(clazz);
        }
        
        @Override
        public T convert(Element source) throws ConversionException {
            
            lock.readLock().lock();
            try{
                return innerConverter.convert(source);
            }
            finally{
                lock.readLock().unlock();
            }
        }
        
    }
    
    
    
    
    
}
