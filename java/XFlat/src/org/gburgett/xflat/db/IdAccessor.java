/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.gburgett.xflat.Id;
import org.gburgett.xflat.XflatException;

/**
 * A helper class that accesses the IDs of an object.
 * @author gordon
 */
public class IdAccessor<T> {
    private PropertyDescriptor idProperty;
    
    private Class<T> pojoType;
    public Class<T> getPojoType(){
        return pojoType;
    }
    
    public IdAccessor(Class<T> pojoType){
        this.pojoType = pojoType;
        
        try{
            doIntrospection(pojoType);
        }
        catch(IntrospectionException ex){
            throw new XflatException("Cannot determine ID property of class " + pojoType.getName(), ex);
        }
    }
    
    private void doIntrospection(Class<T> pojoType) throws IntrospectionException{
        List<PropertyDescriptor> descriptors = new ArrayList<>();
                
        for(PropertyDescriptor p : Introspector.getBeanInfo(pojoType).getPropertyDescriptors()){
            if(p.getReadMethod() == null || Object.class.equals(p.getReadMethod().getDeclaringClass()))
                continue;
            
            if(p.getWriteMethod() == null || Object.class.equals(p.getWriteMethod().getDeclaringClass()))
                continue;
            
            descriptors.add(p);
        }
        
        for(PropertyDescriptor p : descriptors){
            if(p.getReadMethod().getAnnotation(Id.class) != null){
                this.idProperty = p;
            }
        }
        
        //try properties named ID
        for(PropertyDescriptor p : descriptors){
            if("id".equalsIgnoreCase(p.getName())){
                this.idProperty = p;
            }
        }
    }
    
    /**
     * Indicates whether the POJO introspected by this accessor has an ID property.
     * @return 
     */
    public boolean hasId(){
        return this.idProperty != null;
    }
    
    public Class<?> getIdType(){
        if(this.idProperty == null)
            return null;
        
        return this.idProperty.getPropertyType();
    }
    
    public Object getIdValue(T pojo) 
                throws IllegalAccessException, InvocationTargetException 
    {
        if(this.idProperty == null){
            throw new UnsupportedOperationException("Cannot get ID value when object has no ID");
        }
        
        return this.idProperty.getReadMethod().invoke(pojo);
    }
    
    public void setIdValue(T pojo, Object id) 
                throws IllegalAccessException, InvocationTargetException 
    {
        if(this.idProperty == null){
            throw new UnsupportedOperationException("Cannot get ID value when object has no ID");
        }
        
        this.idProperty.getWriteMethod().invoke(pojo, id);
    }
}
