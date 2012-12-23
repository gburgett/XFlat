/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.Converter;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class Foo {
    
    private String id;
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
 
    public static class ToElementConverter implements Converter<Foo, Element>{
        @Override
        public Element convert(Foo source) {
            Element ret = new Element("foo");
            
            Element fooInt = new Element("fooInt");
            fooInt.setText(Integer.toString(source.fooInt));
            ret.addContent(fooInt);
            
            return ret;
        }
    }
    
    public static class FromElementConverter implements Converter<Element, Foo>{
        @Override
        public Foo convert(Element source) {
            if(!"foo".equals(source.getName() ))
                throw new ConversionException("Expected element named 'Foo'");
            
            Foo ret = new Foo();
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

