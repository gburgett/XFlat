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

import org.xflatdb.xflat.Id;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.Converter;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 *
 * @author gordon
 */
public class Foo {
    
    private String id;
    @Id(value="foo/@t:id", namespaces={"xmlns:t='http://www.example.com/ns'"})
    public String getId(){
        return id;
    }
    
    public void setId(String id){
        this.id = id;
    }
    
    public int fooInt;

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + this.fooInt;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Foo other = (Foo) obj;
        if (this.fooInt != other.fooInt) {
            return false;
        }
        return true;
    }
 
    public static final Namespace FooIdNs = Namespace.getNamespace("t", "http://www.example.com/ns");
    
    public static class ToElementConverter implements Converter<Foo, Element>{
        @Override
        public Element convert(Foo source) {
            Element ret = new Element("foo");
            
            if(source.id != null)
                ret.setAttribute("id", source.id, FooIdNs);
            
            Element fooInt = new Element("fooInt");
            fooInt.setText(Integer.toString(source.fooInt));
            ret.addContent(fooInt);
            
            return ret;
        }
    }
    
    public static class FromElementConverter implements Converter<Element, Foo>{
        @Override
        public Foo convert(Element source) throws ConversionException {
            if(!"foo".equals(source.getName() ))
                throw new ConversionException("Expected element named 'Foo'");
            
            Foo ret = new Foo();
            ret.id = source.getAttributeValue("id", FooIdNs);
            
            Element fooInt = source.getChild("fooInt");
            if(fooInt != null){
                try{
                    ret.fooInt = Integer.parseInt(fooInt.getText());
                }catch(NumberFormatException ex){
                    throw new ConversionException("Error parsing fooInt", ex);
                }
            }
            
            return ret;
        }
    }
}

