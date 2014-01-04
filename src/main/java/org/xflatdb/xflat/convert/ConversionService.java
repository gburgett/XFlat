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
package org.xflatdb.xflat.convert;

/**
 * Provides conversions from one POJO to another.
 * 
 * Modeled after Spring's conversion service, but I saw no need to import
 * spring framework's core to perform the simple conversions.
 * 
 * Copied from org.springframework.core.convert,
 * modified by Gordon Burgett
 * @author gordon
 */
public interface ConversionService {
   
    /**
     * Returns true if objects of sourceType can be converted to targetType. 
     * If this method returns true, it means convert(Object, Class) is capable of
     * converting an instance of sourceType to targetType.
     * @param source the source type to convert from (may be null if source is null)
     * @param target the target type to convert to (required)
     * @return true if the conversion service has a conversion from the source to the target.
     */
    public boolean canConvert(Class<?> source, Class<?> target);
    
    /**
     * Convert the source to targetType.
     * @param <T>
     * @param source the source object to convert (may be null)
     * @param target the target type to convert to (required)
     * @return the converted object, an instance of targetType
     * @throws ConversionException if there is an error during conversion
     * @throws ConversionNotSupportedException if the conversion is not
     * supported according to {@link #canConvert(java.lang.Class, java.lang.Class) }.
     */
    public <T> T convert(Object source, Class<T> target)
            throws ConversionException;

    /**
     * Adds a converter to the conversion service.
     * @param <S>
     * @param <T>
     * @param sourceType The type of objects that the converter converts from.
     * @param targetType The type of objects that the converter converts to.
     * @param converter The converter that performs the conversion.
     */
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, 
            Converter<? super S, ? extends T> converter);
    
    /**
     * Removes the registered converter for the source to target type.
     * @param sourceType The source type to convert from
     * @param targetType The target type to convert to
     */
    public void removeConverter(Class<?> sourceType, Class<?> targetType);
}
