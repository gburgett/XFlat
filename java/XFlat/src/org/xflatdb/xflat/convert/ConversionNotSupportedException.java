/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.convert;

/**
 * A subclass of {@link ConversionException} which indicates that the 
 * {@link ConversionService#convert(java.lang.Object, java.lang.Class) } method
 * was called when the conversion was not supported.
 * Supported conversions are those where {@link ConversionService#canConvert(java.lang.Class, java.lang.Class) }
 * returns true.
 * @author gordon
 */
public class ConversionNotSupportedException extends ConversionException {

    /**
     * Creates a new instance of
     * <code>ConverterNotFoundException</code> without detail message.
     */
    public ConversionNotSupportedException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructs an instance of
     * <code>ConverterNotFoundException</code> with the specified detail
     * message.
     *
     * @param msg the detail message.
     */
    public ConversionNotSupportedException(String msg) {
        super(msg);
    }
}
