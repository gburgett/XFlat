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
package org.xflatdb.xflat.db;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.xflatdb.xflat.Id;
import org.xflatdb.xflat.XFlatException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

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
    
    private XPathExpression<Object> alternateIdExpression = null;
    
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
            Object idPropOrField = getIdPropertyOrField(pojoType);
            if(idPropOrField != null){
                if(idPropOrField instanceof PropertyDescriptor){
                    idProp = (PropertyDescriptor)idPropOrField;
                }
                else{
                    idField = (Field)idPropOrField;
                    idField.setAccessible(true);
                }
            }
        }
        catch(IntrospectionException ex){
            throw new XFlatException("Cannot determine ID property of class " + pojoType.getName(), ex);
        }
        
        ret = new IdAccessor<>(pojoType, idProp, idField);
        if(ret.hasId()){
            //see if there's an alternate ID expression.
            ret.alternateIdExpression = getAlternateId(ret.getIdPropertyAnnotation(Id.class));
        }
        
        verify(ret, pojoType);
        
        cachedAccessors.putIfAbsent(pojoType, ret);
        
        return ret;
    }
    
    private static XPathExpression<Object> getAlternateId(Id idPropertyAnnotation){
        if(idPropertyAnnotation == null)
            return null;
        
        String expression = idPropertyAnnotation.value();
        if(expression == null || "".equals(expression)){
            return null;
        }
        
        List<Namespace> namespaces = null;
        if(idPropertyAnnotation.namespaces() != null && idPropertyAnnotation.namespaces().length > 0){
            for(String ns : idPropertyAnnotation.namespaces()){
                if(!ns.startsWith("xmlns:")){
                    continue;
                }
                
                int eqIndex = ns.indexOf("=");
                if(eqIndex < 0 || eqIndex >= ns.length() - 1){
                    continue;
                }
                
                String prefix = ns.substring(6, eqIndex);
                String url = ns.substring(eqIndex + 1);
                
                if(url.startsWith("\"") || url.startsWith("'"))
                    url = url.substring(1);
                
                if(url.endsWith("\"") || url.endsWith("'"))
                    url = url.substring(0, url.length() - 1);
                
                if("".equals(prefix) || "".equals(url))
                    continue;
                
                if(namespaces == null)
                    namespaces = new ArrayList<>();
                
                namespaces.add(Namespace.getNamespace(prefix, url));
            }
        }
        
        //compile it
        return XPathFactory.instance().compile(expression, Filters.fpassthrough(), null, namespaces == null ? Collections.EMPTY_LIST : namespaces);
    }
    
    private static Object getIdPropertyOrField(Class<?> pojoType) throws IntrospectionException{
        List<PropertyDescriptor> descriptors = new ArrayList<>();
                
        for(PropertyDescriptor p : Introspector.getBeanInfo(pojoType).getPropertyDescriptors()){
            if(p.getReadMethod() == null || Object.class.equals(p.getReadMethod().getDeclaringClass()))
                continue;
            
            descriptors.add(p);
        }
        
        for(PropertyDescriptor p : descriptors){
            if(p.getReadMethod().getAnnotation(Id.class) != null){
                return p;
            }
        }
        
        List<Field> fields = new ArrayList<>();
        
        while(!Object.class.equals(pojoType)){
            for(Field f : pojoType.getDeclaredFields()){
                if(Object.class.equals(f.getDeclaringClass()))
                    continue;

                if((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC ||
                    (f.getModifiers() & Modifier.FINAL) == Modifier.FINAL)
                    continue;

                fields.add(f);
            }
            pojoType = pojoType.getSuperclass();
        }
        
        //try fields marked with ID attribute
        for(Field f : fields){
            if(f.getAnnotation(Id.class) != null){
                return f;
            }
        }
        
        //try properties named ID
        for(PropertyDescriptor p : descriptors){
            if("id".equalsIgnoreCase(p.getName())){
                return p;
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
                throw new XFlatException("Unable to verify pojo " + pojoType, ex);
            }
            
            if(!Object.class.equals(eqMethod.getDeclaringClass()) ||
                !Object.class.equals(hashCodeMethod.getDeclaringClass()))
            {
                //this is because our weak reference map that keeps track of IDs
                //for classes that don't have an Id property uses reference equality
                throw new XFlatException("Persistent objects that override " +
                        "equals or hashCode must also declare an id field or property");
            }
                    
        }
    }
    
    /**
     * Indicates whether the POJO introspected by this accessor has an ID property.
     * @return true if an ID property or field was detected.
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
            if(this.idProperty.getWriteMethod() != null)
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
    
    public XPathExpression<Object> getAlternateIdExpression(){
        return alternateIdExpression;
    }
}
