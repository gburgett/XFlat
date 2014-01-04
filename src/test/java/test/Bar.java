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
package test;

import java.util.Objects;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class Bar {
    public String barString;

    
    public static class ToElementConverter implements Converter<Bar, Element>{
        @Override
        public Element convert(Bar source) {
            Element ret = new Element("bar");
            
            Element barStr = new Element("barString");
            barStr.setText(source.barString);
            ret.addContent(barStr);
            
            return ret;
        }
    }
    
    public static class FromElementConverter implements Converter<Element, Bar>{
        @Override
        public Bar convert(Element source) throws ConversionException {
            if(!"bar".equals(source.getName() ))
                throw new ConversionException("Expected element named 'Foo'");
            
            Bar ret = new Bar();
            Element barStr = source.getChild("barString");
            if(barStr != null){
                ret.barString = barStr.getText();
            }
            
            return ret;
        }
    }
}
