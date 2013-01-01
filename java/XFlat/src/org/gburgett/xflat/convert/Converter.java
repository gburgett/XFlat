/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

/**
 * A converter converts a source object of type S to a target of type T. 
 * Implementations of this interface are thread-safe and can be shared.
 * 
 * Copied from org.springframework.core.convert,
 * modified by Gordon Burgett
 */
public interface Converter<S, T> {
    
    /**
     * Convert the source of type S to target type T.
     * @param source the source object to convert, which must be an instance of S
     * @return the converted object, which must be an instance of T
     * @throws ConversionException if an error occurred during conversion.
     */
    public T convert(S source)
            throws ConversionException;
}
