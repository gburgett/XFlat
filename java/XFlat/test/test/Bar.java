/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
