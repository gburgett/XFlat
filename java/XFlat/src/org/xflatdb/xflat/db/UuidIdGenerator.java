/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import java.util.UUID;

/**
 * Implements an ID generator which generates IDs using java's {@link UUID} class
 * @author gordon
 */
public class UuidIdGenerator extends IdGenerator {

    @Override
    public boolean supports(Class<?> idType) {
        return String.class.equals(idType) || 
                idType.isAssignableFrom(UUID.class);
    }

    @Override
    public Object generateNewId(Class<?> idType) {
        UUID id = UUID.randomUUID();
        
        if(idType.isAssignableFrom(UUID.class)){
            return id;
        }
        
        if(String.class.equals(idType)){
            return id.toString();
        }
        
        throw new UnsupportedOperationException("ID type " + idType + 
                " is not supported by this ID generator.");
    }

    @Override
    public String idToString(Object id) {
        if(id == null){
            return null;
        }
        
        //always either UUID or string
        return id.toString();
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        if(id == null)
            return null;
        
        if(String.class.equals(idType))
            return id;
        
        //else its a UUID
        return UUID.fromString(id);
    }
}
