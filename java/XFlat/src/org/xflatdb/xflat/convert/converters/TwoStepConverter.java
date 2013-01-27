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
package org.xflatdb.xflat.convert.converters;

import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;

/**
 * A converter that converts one value to another by first converting to an intermediate
 * value.
 * For example, a value might be convertible to Integer by first converting to 
 * String.  In that case a TwoStepConverter can be registered for the conversion.
 * @author gordon
 */
public class TwoStepConverter<From, Intermediate, To> implements Converter<From, To> {

    private Converter<From, Intermediate> fromConverter;
    private Converter<Intermediate, To> toConverter;
    
    public TwoStepConverter(Converter<From, Intermediate> fromConverter,
                            Converter<Intermediate, To> toConverter)
    {
        this.fromConverter = fromConverter;
        this.toConverter = toConverter;
    }
    
    @Override
    public To convert(From source) throws ConversionException {
        Intermediate i = fromConverter.convert(source);
        return toConverter.convert(i);
    }
    
}
