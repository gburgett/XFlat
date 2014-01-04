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
