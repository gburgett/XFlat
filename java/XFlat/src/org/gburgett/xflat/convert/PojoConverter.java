/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

import org.jdom2.Content;

/**
 * The interface for a PojoConverter.  The implementation is loaded dynamically
 * if needed by the database.  By default it uses JAXB, but this
 * can be changed in the configuration.
 * @author gordon
 */
public interface PojoConverter {
    
    /**
     * Extends the given conversion service to provide it the ability to convert
     * POJOs.  This can be done by adding converters or by returning a new
     * conversion service chained to the given one.
     * @param service The current conversion service used by the database.
     * @return The new conversion service that the database should use.
     */
    public ConversionService extend(ConversionService service);
}
