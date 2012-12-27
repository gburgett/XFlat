/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.concurrent.atomic.AtomicInteger;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class IntegerIdGenerator extends IdGenerator {

    private AtomicInteger lastId = new AtomicInteger(0);
    
    @Override
    public boolean supports(Class<?> idType) {
        return Integer.class.equals(idType) ||
                Float.class.equals(idType) ||
                Double.class.equals(idType) ||
                Long.class.equals(idType) ||
                String.class.equals(idType);
    }

    @Override
    public Object generateNewId(Class<?> idType) {
        
        int id = lastId.incrementAndGet();
        
        if(Integer.class.equals(idType)){
            return new Integer(id);
        }
        if(Float.class.equals(idType)){
            return new Float(id);
        }
        if(Double.class.equals(idType)){
            return new Double(id);
        }
        if(Long.class.equals(idType)){
            return new Long(id);
        }
        if(String.class.equals(idType)){
            return Integer.toString(id);
        }
        
        throw new UnsupportedOperationException("Unsupported ID type " + idType);
    }

    @Override
    public String idToString(Object id) {
        if(id == null){
            return "0";
        }
        
        Class<?> idType = id.getClass();
        if(String.class.equals(idType)){
            return (String)id;
        }
        if(Integer.class.equals(idType)){
            return ((Integer)id).toString();
        }
        if(Float.class.equals(idType)){
            return Integer.toString(((Float)id).intValue());
        }
        if(Double.class.equals(idType)){
            return Integer.toString(((Double)id).intValue());
        }
        if(Long.class.equals(idType)){
            return Integer.toString(((Long)id).intValue());
        }
        
        throw new UnsupportedOperationException("Unsupported ID type " + idType);
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        
        if(String.class.equals(idType)){
            return id;
        }
        
        Integer i;
        if(id == null){
            i = new Integer(0);
        }
        else{
            i = Integer.parseInt(id);
        }
        
        if(Integer.class.equals(idType)){
            return Integer.parseInt(id);
        }
        if(Float.class.equals(idType)){
            return i.floatValue();
        }
        if(Double.class.equals(idType)){
            return i.doubleValue();
        }
        if(Long.class.equals(idType)){
            return i.longValue();
        }
        
        throw new UnsupportedOperationException("Unsupported ID type " + idType);
    }
    
    
    @Override
    public void saveState(Element state){
        state.setAttribute("maxId", Integer.toString(this.lastId.get()), Database.xFlatNs);
    }
    
    @Override
    public void loadState(Element state){
        String maxId = state.getAttributeValue("maxId", Database.xFlatNs);
        this.lastId.set(Integer.parseInt(maxId));
    }
    
}
