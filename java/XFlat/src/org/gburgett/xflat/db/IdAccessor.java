/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.gburgett.xflat.Id;
import org.gburgett.xflat.XflatException;
import org.jdom2.Attribute;

/**
 * A helper class that accesses the IDs of an object.
 * @author gordon
 */
public class IdAccessor<T> {
    private final PropertyDescriptor idProperty;
    
    private final Field idField;
    
    private final Class<T> pojoType;
    public Class<T> getPojoType(){
        return pojoType;
    }
    
    private IdAccessor(Class<T> pojoType, PropertyDescriptor idProperty, Field idField){
        this.pojoType = pojoType;
        this.idProperty = idProperty;
        this.idField = idField;
    }
    
    private static ConcurrentHashMap<Class<?>, IdAccessor<?>> cachedAccessors =
            new ConcurrentHashMap<>();
    /**
     * Gets the IdAccessor for the given pojo type.  Id Accessors are cached
     * statically so that the reflection is only performed once.
     * @param <U> The type of the class for which to get the accessor.
     * @param pojoType The type of the class for which to get the accessor.
     * @return The accessor, which may have already been created and cached.
     */
    public static <U> IdAccessor<U> forClass(Class<U> pojoType){
        if(pojoType.isPrimitive() ||
                String.class.equals(pojoType)){
            return null;
        }
        
        IdAccessor<U> ret = (IdAccessor<U>)cachedAccessors.get(pojoType);
        if(ret != null){
            return ret;
        }
        
        PropertyDescriptor idProp = null;
        Field idField = null;
        try{
            idProp = getIdProperty(pojoType);
            if(idProp == null){
                idField = getIdField(pojoType);
            }
        }
        catch(IntrospectionException ex){
            throw new XflatException("Cannot determine ID property of class " + pojoType.getName(), ex);
        }
        
        ret = new IdAccessor<>(pojoType, idProp, idField);
        
        verify(ret, pojoType);
        
        cachedAccessors.putIfAbsent(pojoType, ret);
        
        return ret;
    }
    
    private static PropertyDescriptor getIdProperty(Class<?> pojoType) throws IntrospectionException{
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
                return p;
            }
        }
        
        //try properties named ID
        for(PropertyDescriptor p : descriptors){
            if("id".equalsIgnoreCase(p.getName())){
                return p;
            }
        }
        
        return null;
    }
    
    private static Field getIdField(Class<?> pojoType){
        List<Field> fields = new ArrayList<>();
        
        for(Field f : pojoType.getFields()){
            if(Object.class.equals(f.getDeclaringClass()))
                continue;
            
            if((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC ||
                (f.getModifiers() & Modifier.FINAL) == Modifier.FINAL)
                continue;
            
            fields.add(f);
        }
        
        //try fields marked with ID attribute
        for(Field f : fields){
            if(f.getAnnotation(Id.class) != null){
                return f;
            }
        }
        
        //try fields named ID
        for(Field f : fields){
            if("id".equalsIgnoreCase(f.getName())){
                return f;
            }
        }
        
        return null;
    }
    
    private static void verify(IdAccessor accessor, Class<?> pojoType){
        //if we don't have an ID, verify that the pojo uses reference equality.
        if(!accessor.hasId()){
            Method eqMethod;
            Method hashCodeMethod;
            try {
                 eqMethod = pojoType.getMethod("equals", Object.class);
                 hashCodeMethod = pojoType.getMethod("hashCode");
            } catch (NoSuchMethodException | SecurityException ex) {
                //should never happen
                throw new XflatException("Unable to verify pojo " + pojoType, ex);
            }
            
            if(!Object.class.equals(eqMethod.getDeclaringClass()) ||
                !Object.class.equals(hashCodeMethod.getDeclaringClass()))
            {
                //this is because our weak reference map that keeps track of IDs
                //for classes that don't have an Id property uses reference equality
                throw new XflatException("Persistent objects that override " +
                        "equals or hashCode must also declare an id field or property");
            }
                    
        }
    }
    
    /**
     * Indicates whether the POJO introspected by this accessor has an ID property.
     * @return 
     */
    public boolean hasId(){
        return this.idProperty != null ||
                this.idField != null;
    }
    
    public Class<?> getIdType(){
        if(this.idProperty != null)
        {
            return this.idProperty.getPropertyType();
        }
        
        if(this.idField != null){
            return this.idField.getType();
        }
        
        return null;
    }
    
    /**
     * Gets the value of the ID by accessing the ID property or field
     * on the object.
     * 
     * If the ID is a JavaBean property, the property's getter is invoked via reflection.
     * If the ID is a field, the field is retrieved via reflection.
     * @param pojo The object whose ID to get.
     * @return The value of the object's ID property or field.
     * @throws IllegalAccessException 
     * @throws InvocationTargetException 
     * @see Method#invoke(java.lang.Object, java.lang.Object[])
     * @see Field#get(java.lang.Object) 
     */
    public Object getIdValue(T pojo) 
                throws IllegalAccessException, InvocationTargetException 
    {
        if(this.idProperty != null){
            return this.idProperty.getReadMethod().invoke(pojo);
        }
        
        if(this.idField != null){
            return this.idField.get(pojo);
        }
        
        throw new UnsupportedOperationException("Cannot get ID value when object has no ID");
    }
    
    /**
     * Sets the object's ID property or field to the given value.
     * 
     * If the ID is a JavaBean property, the property's setter is invoked via reflection.
     * If the ID is a field, the field is set via reflection.
     * @param pojo The object whose ID should be set
     * @param id The new value of the ID
     * @throws IllegalAccessException
     * @throws InvocationTargetException 
     * @see Method#invoke(java.lang.Object, java.lang.Object[]) 
     * @see Field#set(java.lang.Object, java.lang.Object) 
     */
    public void setIdValue(T pojo, Object id) 
                throws IllegalAccessException, InvocationTargetException 
    {
        if(this.idProperty != null){
            this.idProperty.getWriteMethod().invoke(pojo, id);
            return;
        }
        
        if(this.idField != null){
            this.idField.set(pojo, id);
            return;
        }
            
        throw new UnsupportedOperationException("Cannot get ID value when object has no ID");
    }

    public String getIdPropertyName(){
        if(this.idProperty != null){
            return this.idProperty.getName();
        }
        
        if(this.idField != null){
            return this.idField.getName();
        }
        
        throw new UnsupportedOperationException("Cannot get field name when object has no ID");
    }
    
    public <U extends Annotation> U getIdPropertyAnnotation(Class<U> annotationClass){
        if(this.idProperty != null){
            return this.idProperty.getReadMethod().getAnnotation(annotationClass);
        }
        
        if(this.idField != null){
            return this.idField.getAnnotation(annotationClass);
        }
        
        throw new UnsupportedOperationException("Cannot get annotation when object has no ID");
    }
}
