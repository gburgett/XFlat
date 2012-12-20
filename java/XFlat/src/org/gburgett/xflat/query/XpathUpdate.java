/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.List;
import org.gburgett.xflat.convert.ConversionService;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Parent;
import org.jdom2.Text;
import org.jdom2.xpath.XPathExpression;

/**
 *
 * @author gordon
 */
public class XpathUpdate {
    
    private List<Update> updates = new ArrayList<>();
    public List<Update> getUpdates(){
        return updates;
    }
    
    
    private ConversionService conversionService;
    /**
     * Sets the conversion service used by this Update operation when it is
     * applied.
     * @param conversionService 
     */
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    private XpathUpdate(){
        
    }
    
    /**
     * Creates an update that sets the values selected by the XPath expression.
     * @param path The path selecting an element (or elements) to set.
     * @return A builder object that can be chained to provide a value for the update.
     */
    public static Builder set(XPathExpression<Object> path){
        XpathUpdate ret = new XpathUpdate();
        
        return ret.new Builder(path);
    }
    
    /**
     * Adds an additional update operation to this XPath update for the values
     * selected by the given XPath expression.
     * @param path
     * @return 
     */
    public Builder and(XPathExpression<Object> path){
        return new Builder(path);
    }
    
    public static class Update{
        private XPathExpression<Object> path;
        public XPathExpression<Object> getPath(){
            return path;
        }
        
        private Object value;
        public Object getValue(){
            return value;
        }
        
        private Update(XPathExpression<Object> path, Object value){
            this.path = path;
            this.value = value;
        }
    }
    
    public class Builder {
    
        private XPathExpression<Object> path;
        
        public XpathUpdate to(Object value){
            Update update = new Update(path, value);
            XpathUpdate.this.updates.add(update);
            
            return XpathUpdate.this;
        }
        
        private Builder(XPathExpression<Object> path){
            this.path = path;
        }
    }


    /**
     * Applies the update operations to the given DOM Element representing
     * the data in a selected row.
     * @param rowData The DOM Element representing the data in a selected row.
     * @return true if any updates were applied.
     * @throws JDOMException 
     */
    public boolean apply(Element rowData)
            throws JDOMException
    {
        boolean anyUpdates = false;
        for(Update update : this.updates){
            //the update's value will be one or the other, don't know which
            Content asContent = null;
            String asString = null;
            
            if(update.value instanceof String){
                asString = (String) update.value;
            }
            
            if(update.value instanceof Content){
                asContent = (Content) update.value;
            }
            
            for(Object node : update.path.evaluate(rowData)){
                if(node == null)
                    continue;
                
                if(!(node instanceof Content)){
                    continue;
                }
                
                Content contentNode = (Content)node;
                Parent parent;
                
                if(update.value == null){
                    //remove this node from its parent element
                    parent = contentNode.getParent();
                    if(parent != null){
                        parent.removeContent(contentNode);
                        anyUpdates = true;
                        continue;
                    }
                }
                
                if(node instanceof Attribute){
                    //for attributes we just set the string value
                    if(asString == null){
                        asString = getStringValue(update.value);
                    }
                    
                    //if we fail conversion then do nothing.
                    if(asString != null)
                        ((Attribute)node).setValue(asString);
                    
                    anyUpdates = true;
                    continue;
                }
                
                Element parentElement = contentNode.getParentElement();
                if(parentElement == null){
                    //can't do anything
                    continue;
                }
                
                if(asContent == null){
                    asContent = getContentValue(update.value);
                    if(asContent == null){
                        //failed conversion, try text
                        asString = getStringValue(update.value);
                        if(asString != null){
                            //success!
                            asContent = new Text(asString);
                        }
                    }
                }
                
                if(asContent != null){
                    //replace this content in the parent element
                    int index = parentElement.indexOf(contentNode);
                    parentElement.removeContent(index);
                    parentElement.addContent(index, asContent);
                    anyUpdates = true;
                }
            }
        }
        
        return anyUpdates;
    }
    
    private String getStringValue(Object value){
        if(value == null)
            return null;
        
        if(value instanceof String)
            return (String)value;
        
        if(this.conversionService == null || 
                !this.conversionService.canConvert(value.getClass(), String.class)){
            return null;
        }
        
        return this.conversionService.convert(value, String.class);
    }

    private Content getContentValue(Object value){
        if(value == null)
            return null;
        
        if(value instanceof Content)
            return (Content)value;
        
        if(this.conversionService == null || 
                !this.conversionService.canConvert(value.getClass(), Content.class)){
            return null;
        }
        
        return this.conversionService.convert(value, Content.class);
    }
}
