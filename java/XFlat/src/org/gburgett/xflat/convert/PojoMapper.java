/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

import org.jdom2.Content;

/**
 * The interface for a PojoMapper.  The implementation is loaded dynamically
 * if needed by the database.  By default it's the JAXB POJO Mapper, but this
 * can be changed in the configuration.
 * @author gordon
 */
public interface PojoMapper {
    
    /**
     * Creates {@link Converter}s to convert to and from a JDOM {@link Content} element,
     * and registers them on the conversion service.
     * @param pojoType The POJO to map.
     */
    public void registerPojoMapping(Class<?> pojoType, ConversionService conversionService);
}
