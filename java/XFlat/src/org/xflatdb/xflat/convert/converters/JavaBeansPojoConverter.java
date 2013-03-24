/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.convert.converters;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionNotSupportedException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;
import org.xflatdb.xflat.convert.PojoConverter;

/**
 * This PojoConverter maps Java beans to XML using {@link java.beans.XMLEncoder}.
 * @author Gordon
 */
public class JavaBeansPojoConverter implements PojoConverter {

    @Override
    public ConversionService extend(ConversionService service) {
        return new JavaBeansConversionService(service);
    }
    
    private static class JavaBeansConversionService implements ConversionService {

        ConversionService base;
        
        public JavaBeansConversionService(ConversionService base){
            this.base = base;
        }
        
        @Override
        public boolean canConvert(Class<?> source, Class<?> target) {
            if(!base.canConvert(source, target)){
                
                if(Element.class.equals(target))
                    makeConverters(source);
                else if(Element.class.equals(source)){
                    makeConverters(target);
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
                    makeConverters(source.getClass());
                else if(Element.class.equals(source.getClass())){
                    makeConverters(target);
                }
                else{
                    //can't convert
                    throw ex;
                }
                
                //try again now that we successfully made converters
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
    
        private <T> void makeConverters(Class<T> clazz){
            base.addConverter(clazz, Element.class, XMLEncoderConverter);
            base.addConverter(Element.class, clazz, getDecoder(clazz));
        }
    }
    
    
    
    private static Converter<Object, Element> XMLEncoderConverter = new Converter<Object, Element>() {
                
        @Override
        public Element convert(Object source) throws ConversionException {
            
            byte[] bytes;
            try(ByteArrayOutputStream os = new ByteArrayOutputStream()){

                try(XMLEncoder encoder = new XMLEncoder(os)){
                    encoder.writeObject(source);
                }

                bytes = os.toByteArray();
            }
            catch(IOException ex){
                throw new ConversionException("Error converting object of class " + source.getClass(), ex);
            }
            
            try(ByteArrayInputStream is = new ByteArrayInputStream(bytes)){
                
                Document doc = new SAXBuilder().build(is);
                
                return doc.getRootElement().getChild("object").detach();
            }
            catch(JDOMException | IOException ex){
                throw new ConversionException("Error converting object of class " + source.getClass(), ex);
            }
        }
    };
    
    private static <U> Converter<Element, U> getDecoder(Class<U> clazz){
        return new Converter<Element, U>(){

            final String version = System.getProperty("java.version");

            @Override
            public U convert(Element source) throws ConversionException {
                byte[] bytes;
                try(ByteArrayOutputStream os = new ByteArrayOutputStream()){

                    Document doc = new Document();
                    doc.setRootElement(new Element("java")
                            .setAttribute("version", version)
                            .setAttribute("class", "java.beans.XMLDecoder"));

                    doc.getRootElement().addContent(source.detach());

                    XMLOutputter outputter = new XMLOutputter();
                    outputter.output(doc, os);

                    bytes = os.toByteArray();
                }
                catch(IOException ex){
                    throw new ConversionException("Error reading object of class " + source.getClass(), ex);
                }

                try(ByteArrayInputStream is = new ByteArrayInputStream(bytes)){

                    try(XMLDecoder decoder = new XMLDecoder(is)){
                        return (U)decoder.readObject();
                    }
                }
                catch(IOException ex){
                    throw new ConversionException("Error reading object of class " + source.getClass(), ex);
                }
            }
        };
    }
    
    
}
