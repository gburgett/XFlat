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

import org.jdom2.*;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.Converter;

/**
 * A number of converters that converts JDOM values to and from several types.
 * @author gordon
 */
public class JDOMConverters {
    
    /**
     * Registers all the JDOM converters to the given ConversionService.
     * @param service 
     */
    public static void registerTo(ConversionService service){
        service.addConverter(Content.class, String.class, ContentToStringConverter);
        service.addConverter(Element.class, String.class, ContentToStringConverter);
        service.addConverter(Text.class, String.class, ContentToStringConverter);
        service.addConverter(CDATA.class, String.class, ContentToStringConverter);
        service.addConverter(DocType.class, String.class, ContentToStringConverter);
        service.addConverter(EntityRef.class, String.class, ContentToStringConverter);
        service.addConverter(Attribute.class, String.class, AttributeToStringConverter);
        
        service.addConverter(String.class, Text.class, StringToTextConverter);
        service.addConverter(String.class, CDATA.class, StringToCdataConverter);
        service.addConverter(String.class, Content.class, StringToCdataConverter);
        
        registerNumbers(service);
    }
    
    //<editor-fold desc="string" >
    //converts all Content elements to strings
    public static final Converter<Content, String> ContentToStringConverter = new Converter<Content, String>(){
        @Override
        public String convert(Content source) {
            return source.getValue();
        }
    };
    
    public static final Converter<Attribute, String> AttributeToStringConverter = new Converter<Attribute, String>(){
        @Override
        public String convert(Attribute source) {
            return source.getValue();
        }
    };
    
    public static final Converter<String, Text> StringToTextConverter = new Converter<String, Text>(){
        @Override
        public Text convert(String source) {
            return new Text(source);
        }
    };
    
    public static final Converter<String, CDATA> StringToCdataConverter = new Converter<String, CDATA>(){
        @Override
        public CDATA convert(String source) {
            return new CDATA(source);
        }
    };
    
    //</editor-fold>
    
    //<editor-fold desc="numbers">
    
    private static void registerNumbers(ConversionService service){
        service.addConverter(Content.class, Integer.class, ContentToIntegerConverter);
        service.addConverter(Element.class, Integer.class, ContentToIntegerConverter);
        service.addConverter(Text.class, Integer.class, ContentToIntegerConverter);
        service.addConverter(CDATA.class, Integer.class, ContentToIntegerConverter);
        service.addConverter(Attribute.class, Integer.class, AttributeToIntegerConverter);
        
        service.addConverter(Content.class, Long.class, ContentToLongConverter);
        service.addConverter(Element.class, Long.class, ContentToLongConverter);
        service.addConverter(Text.class, Long.class, ContentToLongConverter);
        service.addConverter(CDATA.class, Long.class, ContentToLongConverter);
        service.addConverter(Attribute.class, Long.class, AttributeToLongConverter);
        
        service.addConverter(Content.class, Float.class, ContentToFloatConverter);
        service.addConverter(Element.class, Float.class, ContentToFloatConverter);
        service.addConverter(Text.class, Float.class, ContentToFloatConverter);
        service.addConverter(CDATA.class, Float.class, ContentToFloatConverter);
        service.addConverter(Attribute.class, Float.class, AttributeToFloatConverter);
        
        service.addConverter(Content.class, Double.class, ContentToDoubleConverter);
        service.addConverter(Element.class, Double.class, ContentToDoubleConverter);
        service.addConverter(Text.class, Double.class, ContentToDoubleConverter);
        service.addConverter(CDATA.class, Double.class, ContentToDoubleConverter);
        service.addConverter(Attribute.class, Double.class, AttributeToDoubleConverter);
        
        service.addConverter(Content.class, Boolean.class, ContentToBooleanConverter);
        service.addConverter(Element.class, Boolean.class, ContentToBooleanConverter);
        service.addConverter(Text.class, Boolean.class, ContentToBooleanConverter);
        service.addConverter(CDATA.class, Boolean.class, ContentToBooleanConverter);
        service.addConverter(Attribute.class, Boolean.class, AttributeToBooleanConverter);
        
        service.addConverter(Integer.class, Content.class, ObjectToTextConverter);
        service.addConverter(Integer.class, Text.class, ObjectToTextConverter);
        service.addConverter(Long.class, Content.class, ObjectToTextConverter);
        service.addConverter(Long.class, Text.class, ObjectToTextConverter);
        service.addConverter(Float.class, Content.class, ObjectToTextConverter);
        service.addConverter(Float.class, Text.class, ObjectToTextConverter);
        service.addConverter(Double.class, Content.class, ObjectToTextConverter);
        service.addConverter(Double.class, Text.class, ObjectToTextConverter);
        service.addConverter(Boolean.class, Content.class, ObjectToTextConverter);
        service.addConverter(Boolean.class, Text.class, ObjectToTextConverter);
    }
    
    public static final Converter<Content, Integer> ContentToIntegerConverter 
        = new TwoStepConverter<>(ContentToStringConverter, StringConverters.StringToIntegerConverter);
    
    public static final Converter<Content, Float> ContentToFloatConverter
        = new TwoStepConverter<>(ContentToStringConverter, StringConverters.StringToFloatConverter);
    
    public static final Converter<Content, Long> ContentToLongConverter
        = new TwoStepConverter<>(ContentToStringConverter, StringConverters.StringToLongConverter);
    
    public static final Converter<Content, Double> ContentToDoubleConverter 
        = new TwoStepConverter<>(ContentToStringConverter, StringConverters.StringToDoubleConverter);
    
    public static final Converter<Content, Boolean> ContentToBooleanConverter
        = new TwoStepConverter<>(ContentToStringConverter, StringConverters.StringToBooleanConverter);

    public static final Converter<Attribute, Integer> AttributeToIntegerConverter 
        = new TwoStepConverter<>(AttributeToStringConverter, StringConverters.StringToIntegerConverter);
    
    public static final Converter<Attribute, Float> AttributeToFloatConverter
        = new TwoStepConverter<>(AttributeToStringConverter, StringConverters.StringToFloatConverter);
    
    public static final Converter<Attribute, Long> AttributeToLongConverter
        = new TwoStepConverter<>(AttributeToStringConverter, StringConverters.StringToLongConverter);
    
    public static final Converter<Attribute, Double> AttributeToDoubleConverter 
        = new TwoStepConverter<>(AttributeToStringConverter, StringConverters.StringToDoubleConverter);
    
    public static final Converter<Attribute, Boolean> AttributeToBooleanConverter
        = new TwoStepConverter<>(AttributeToStringConverter, StringConverters.StringToBooleanConverter);
    
    public static final Converter<Object, Text> ObjectToTextConverter
        = new TwoStepConverter<>(StringConverters.ObjectToStringConverter, StringToTextConverter);
    
    
    //</editor-fold
}
