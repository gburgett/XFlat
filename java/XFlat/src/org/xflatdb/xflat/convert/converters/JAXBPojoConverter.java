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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.jaxb.JDOMStreamReader;
import org.jdom2.jaxb.JDOMStreamWriter;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionNotSupportedException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.db.IdAccessor;

/**
 * A PojoConverter that extends a ConversionService to convert all unknown
 * objects to and from Element using JAXB.
 * Any conversion which 
 * @author gordon
 */
public class JAXBPojoConverter implements PojoConverter {
    
    /**
     * Extends the given conversion service with a JAXB conversion service.
     * A JAXB Marshaller and Unmarshaller will be created for classes that cannot
     * be converted using the given conversion service.
     * @param service The service to extend
     * @return A new service that extends the functionality of the given service.
     */
    @Override
    public ConversionService extend(ConversionService service) {
        if(service instanceof JAXBConversionService){
            return service;
        }
        
        return new JAXBConversionService(service);
    }

    private Map<Class<?>, XPathExpression<Object>> idSelectorCache = new ConcurrentHashMap<>();
    
    /**
     * Guesses an alternate ID selector for the class based on how JAXB would
     * map the ID property.  If the class has no ID property, a null expression
     * is returned.
     * @param clazz The class for which an alternate ID selector is required.
     * @return The alternate ID selector, or null if the class hass no ID property.
     */
    public XPathExpression<Object> idSelector(Class<?> clazz) {
        
        if(!idSelectorCache.containsKey(clazz)){
            XPathExpression<Object> ret = makeIdSelector(clazz);
            idSelectorCache.put(clazz, ret);
            return ret;
        }
        return idSelectorCache.get(clazz);
    }
    
    private XPathExpression<Object> makeIdSelector(Class<?> clazz){
        IdAccessor accessor = IdAccessor.forClass(clazz);
        
        if(!accessor.hasId()){
            return null;
        }
        
        Namespace ns = null;
        StringBuilder ret = new StringBuilder(clazz.getSimpleName());
        
        XmlAttribute attribute = (XmlAttribute) accessor.getIdPropertyAnnotation(XmlAttribute.class);
        if(attribute != null){
            ret.append("/@");
            if(attribute.namespace() != null){
                 ns = Namespace.getNamespace("id", attribute.namespace());
                 ret.append(ns.getPrefix()).append(":");
            }
            if(attribute.name() != null){
                ret.append(attribute.name());
            }
            else{
                ret.append(accessor.getIdPropertyName());
            }
        }
        else{
            ret.append("/");
            XmlElement element = (XmlElement) accessor.getIdPropertyAnnotation(XmlElement.class);
            if(element != null){
                if(element.namespace() != null){
                    ns = Namespace.getNamespace("id", attribute.namespace());
                    ret.append(ns.getPrefix()).append(":");
                }
                if(element.name() != null){
                    ret.append(element.name());
                }
                else{
                    ret.append(accessor.getIdPropertyName());
                }
            }
            else{
                ret.append(accessor.getIdPropertyName());
            }
        }
        
        if(ns == null){
            return XPathFactory.instance().compile(ret.toString());
        }
        return XPathFactory.instance().compile(ret.toString(), Filters.fpassthrough(), null, ns);
    }
    
    
    private static class JAXBConversionService implements ConversionService{

        ConversionService base;
        
        Set<Class<?>> cannotMap = new HashSet<>();
        
        public JAXBConversionService(ConversionService base){
            this.base = base;
        }
        
        @Override
        public boolean canConvert(Class<?> source, Class<?> target) {
            if(base.canConvert(source, target)){
                return true;
            }
            
            //are we converting to/from Element?
            if(!Element.class.equals(source) && !Element.class.equals(target)){
                return false;
            }
            
            try{
                if(Element.class.equals(source)){
                    return this.makeJaxbConverters(target);
                }
                else{
                    return this.makeJaxbConverters(source);
                }
            }
            catch(ConversionException ex){
                LogFactory.getLog(getClass())
                    .trace("ConversionException encountered when attempting to map class with JAXB", ex);
                return false;
            }
        }

        @Override
        public <T> T convert(Object source, Class<T> target) throws ConversionException {
            
            try{
                return base.convert(source, target);
            }
            catch(ConversionNotSupportedException ex){
                //the base class does not support the conversion - try to make JAXB converters
                
                Class<?> pojoClass;
                if(Element.class.equals(target)){
                    if(source == null){
                        throw ex;
                    }
                    
                    pojoClass = source.getClass();
                }
                else{
                    pojoClass = target;
                }
                
                if(!this.makeJaxbConverters(pojoClass)){
                    throw new ConversionNotSupportedException("Unable to make JAXB converters for target " + pojoClass);
                }
                
                //try again now that we successfully made JAXB converters
                return base.convert(source, target);
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
        
        private boolean makeJaxbConverters(Class<?> target)
                throws ConversionNotSupportedException
        {
            //are the classes non-primitive Objects?
            if(target.isPrimitive() ||
                    target.isEnum() ||
                    target.isAnnotation() ||
                    target.isInterface()){
                return false;
            }
            
            //is it a class we previously tried and failed to map?
            if(cannotMap.contains(target)){
                return false;
            }
            
            try {
                JAXBContext context = JAXBContext.newInstance(target);
                
                addJAXBConverters(context, target, base);
                return true;
            } catch (JAXBException ex) {
                //failure to map - add to the set so we don't retry
                this.cannotMap.add(target);
                throw new ConversionNotSupportedException("Cannot create JAXB binding for " + target, ex);
            }
        }
        
    }
    
    /**
     * Adds converters to and from JDOM {@link Element} for the given class.
     * This gives you more control over the {@link JAXBContext} than just allowing the 
     * JAXBPojoConverter to create a default JAXBContext for your pojos.
     * @param <T> The generic type of the pojo which should be convertible.
     * @param context The JAXBContext created to map the pojo to XML.
     * @param baseClass The pojo class which should be convertible.
     * @param registerTo The conversion service to which the converters should be added.
     * @throws JAXBException If an error occurs when creating the marshaller or unmarshaller.
     */
    public static <T> void addJAXBConverters(JAXBContext context, Class<T> baseClass, ConversionService registerTo) throws JAXBException{
        Converter<T, Element> marshaller = new JAXBMarshallingConverter(baseClass, context.createMarshaller());
        Converter<Element, T> unmarshaller = new JAXBUnmarshallingConverter<>(baseClass, context.createUnmarshaller());
        
        registerTo.addConverter(baseClass, Element.class, marshaller);
        registerTo.addConverter(Element.class, baseClass, unmarshaller);
    }
    
    private static class JAXBMarshallingConverter<T> implements Converter<T, Element>{
        XMLInputFactory factory = XMLInputFactory.newFactory();
        Marshaller marshaller;
        Class<T> clazz;
        
        public JAXBMarshallingConverter(Class<T> clazz, Marshaller marshaller){
            this.clazz = clazz;
            this.marshaller = marshaller;
        }

        @Override
        public Element convert(T source) throws ConversionException {
            try{
                Document doc;
                JDOMStreamWriter out = new JDOMStreamWriter();
                try{
                    this.marshaller.marshal(source, out);
                    
                    doc = out.getDocument();
                }
                finally{
                    out.close();
                }
                
                return doc.detachRootElement();
                
            }catch(JAXBException | XMLStreamException ex){
                throw new ConversionException("Unable to convert", ex);
            }
        }
    }
    
    private static class JAXBUnmarshallingConverter<T> implements Converter<Element, T>{

        org.jdom2.output.XMLOutputter outputter = new XMLOutputter();
        Unmarshaller unmarshaller;
        Class<T> clazz;
        
        public JAXBUnmarshallingConverter(Class<T> clazz, Unmarshaller unmarshaller){
            this.clazz = clazz;
            this.unmarshaller = unmarshaller;
        }
        
        @Override
        public T convert(Element source) throws ConversionException {
           
            Document doc = new Document();
            doc.setRootElement(source.detach());
            
            JDOMStreamReader in = new JDOMStreamReader(doc);
            try{
                T ret = (T)unmarshaller.unmarshal(in);
                return ret;
            }
            catch(JAXBException | ClassCastException ex){
                throw new ConversionException("Unable to convert element to " + clazz, ex);
            }
            finally{
                try{
                    in.close();
                }catch(XMLStreamException ex){
                    //ignore
                }
            }
        }
        
    }
}
