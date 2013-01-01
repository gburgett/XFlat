/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.ConversionService;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Parent;
import org.jdom2.Text;
import org.jdom2.xpath.XPathExpression;

/**
 * Specifies an update operation which sets the value of a matched
 * existing DOM element.
 * The new value must be convertible to {@link Content} or String.
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
    public static <T> XpathUpdate set(XPathExpression<T> path, Object value){
        XpathUpdate ret = new XpathUpdate();
        
        Update<T> u = new Update<>(path, value, UpdateType.SET);
        ret.updates.add(u);
        
        return ret;
    }
    
    public static <T> XpathUpdate unset(XPathExpression<T> path){
        XpathUpdate ret = new XpathUpdate();
        ret.updates.add(new Update<>(path, null, UpdateType.UNSET));
        return ret;
    }
    
    /**
     * Adds an additional update operation to this XPath update for the values
     * selected by the given XPath expression.
     * @param path
     * @return 
     */
    public <T> XpathUpdate andSet(XPathExpression<T> path, Object value){
        Update<T> u = new Update<>(path, value, UpdateType.SET);
        this.updates.add(u);
        
        return this;
    }
    
    public <T> XpathUpdate andUnset(XPathExpression<T> path){
        this.updates.add(new Update<>(path, null, UpdateType.UNSET));
        return this;
    }
    
    public static class Update <T>{
        private XPathExpression<T> path;
        public XPathExpression<T> getPath(){
            return path;
        }
        
        private Object value;
        public Object getValue(){
            return value;
        }
        
        private UpdateType updateType;
        public UpdateType getUpdateType() {
            return this.updateType;
        }
        
        private Update(XPathExpression<T> path, Object value, UpdateType type){
            this.path = path;
            this.value = value;
            this.updateType = type;
        }
    }
    

    /**
     * Applies the update operations to the given DOM Element representing
     * the data in a selected row.
     * @param rowData The DOM Element representing the data in a selected row.
     * @return true if any updates were applied.
     * @throws JDOMException 
     */
    public int apply(Element rowData)
            throws JDOMException
    {
        int updateCount = 0;
        
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
                
                Parent parent;
                Element parentElement;
                
                if(update.getUpdateType() == UpdateType.UNSET){
                    if(node instanceof Attribute){
                        parentElement = ((Attribute)node).getParent();
                        if(parentElement != null){
                            parentElement.removeAttribute((Attribute)node);
                            updateCount++;
                        }
                    }
                    else if(node instanceof Content){
                        parent = ((Content)node).getParent();
                        //remove this node from its parent element
                        if(parent != null){
                            parent.removeContent((Content)node);
                            updateCount++;
                        }
                    }
                    
                    continue;
                }
                
                //it's a set
                
                if(node instanceof Attribute){
                    //for attributes we set the value to empty string
                    //this way it can still be selected by xpath for future updates
                    if(update.value == null){
                        ((Attribute)node).setValue("");
                        updateCount++;
                    }
                    else {
                        if(asString == null){
                            asString = getStringValue(update.value);
                        }
                        
                        //if we fail conversion then do nothing.
                        if(asString != null){
                            ((Attribute)node).setValue(asString);
                            updateCount++;
                        }
                    }
                    
                    continue;
                }
                else if(!(node instanceof Content)){
                    //can't do anything
                    continue;
                }
                
                Content contentNode = (Content)node;
                
                //need to convert
                if(update.value != null && asContent == null){
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
                
                if(node instanceof Element){
                    //for elements we also set the value, but the value could be Content
                    if(update.value == null){
                        ((Element)node).removeContent();
                        updateCount++;
                    }
                    else if(asContent != null){
                        if(asContent.getParent() != null){
                            //we used the content before, need to clone it
                            asContent = asContent.clone();
                        }
                        
                        ((Element)node).setContent(asContent);
                        updateCount++;
                    }
                    continue;
                }
                
                //at this point the node is Text, CDATA or something else.
                //The strategy now is to replace the value in its parent.
                parentElement = contentNode.getParentElement();
                if(parentElement == null){
                    //can't do anything
                    continue;
                }
                
                if(update.value == null || asContent != null){
                    //replace this content in the parent element
                    int index = parentElement.indexOf(contentNode);
                    parentElement.removeContent(index);
                    if(update.value != null){
                        //if it was null then act like an unset, otherwise
                        //its a replace
                        
                        if(asContent.getParent() != null){
                            //we used the content before, need to clone it
                            asContent = asContent.clone();
                        }
                        
                        parentElement.addContent(index, asContent);
                    }
                    updateCount++;
                }
            }
        }
        
        return updateCount;
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
        try {
            return this.conversionService.convert(value, String.class);
        } catch (ConversionException ex) {
            Log log = LogFactory.getLog(getClass());
            log.warn("Unable to convert update value to string", ex);
            return null;
        }
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
        try {
            return this.conversionService.convert(value, Content.class);
        } catch (ConversionException ex) {
            Log log = LogFactory.getLog(getClass());
            log.warn("Unable to convert update value to content", ex);
            return null;
        }
    }

    public enum UpdateType{
        SET,
        UNSET
    }
}
