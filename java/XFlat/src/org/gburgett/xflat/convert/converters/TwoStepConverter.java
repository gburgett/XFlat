/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert.converters;

import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.Converter;

/**
 * A converter that converts one value to another by first converting to an intermediate
 * value.
 * For example, a value might be convertible to Integer by first converting to 
 * String.  In that case a TwoStepConverter can be registered for the conversion.
 * @author gordon
 */
public class TwoStepConverter<From, Intermediate, To> implements Converter<From, To> {

    private Converter<From, Intermediate> fromConverter;
    private Converter<Intermediate, To> toConverter;
    
    public TwoStepConverter(Converter<From, Intermediate> fromConverter,
                            Converter<Intermediate, To> toConverter)
    {
        this.fromConverter = fromConverter;
        this.toConverter = toConverter;
    }
    
    @Override
    public To convert(From source) throws ConversionException {
        Intermediate i = fromConverter.convert(source);
        return toConverter.convert(i);
    }
    
}
