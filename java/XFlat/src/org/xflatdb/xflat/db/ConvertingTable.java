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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.apache.commons.logging.LogFactory;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.Table;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.xflatdb.xflat.XFlatConstants;

/**
 * A table implementation that converts objects to elements
 * using the database's conversion service.
 * @author gordon
 */
public class ConvertingTable<T> extends TableBase implements Table<T> {

    private Class<T> tableType;
    /**
     * Gets the class of the items in the table.
     * @return The class object
     */
    protected Class<T> getTableType(){
        return this.tableType;
    }
    
    private ConversionService conversionService;
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    private final IdAccessor accessor;
    
    private final Map<T, String> idMap;
    
    private XPathExpression<Object> alternateIdExpression;
    void setAlternateIdExpression(XPathExpression<Object> expression){        
        this.alternateIdExpression = expression;
    }
    
    ConvertingTable(Class<T> type, String name){
        super(name);
        
        this.tableType = type;
        
        this.accessor = IdAccessor.forClass(type);
        if(!this.accessor.hasId()){
            //we need to keep a reference to the ID in a weak cache
            idMap = Collections.synchronizedMap(new WeakHashMap<T, String>());
        }
        else{
            idMap = null;
        }
        
    }
        
    //<editor-fold desc="helpers">
    private String getId(Element rowData){
        return rowData.getAttributeValue("id", XFlatConstants.xFlatNs);
    }
    
    private String getId(T data){
        if(this.accessor.hasId()){
            Object id;
            try {
                 id = this.accessor.getIdValue(data);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XFlatException("Cannot access ID on data", ex);
            }
            if(id == null)
                return null;
            
            return this.getIdGenerator().idToString(id);
            
        }
        else if(this.idMap != null){
            //hopefully we cached it
            return this.idMap.get(data);
        }
        
        return null;
    }
    
    private void setId(Element rowData, String sId){
        rowData.setAttribute("id", sId, XFlatConstants.xFlatNs);
    }
        
    private Element convert(T data, String id){
        Element ret;
        try {
            ret = this.conversionService.convert(data, Element.class);
        } catch (ConversionException ex) {
            throw new XFlatException("Cannot convert data with ID " + id, ex);
        }
        
        if (id != null){
            setId(ret, id);
        }
        
        return ret;
    }
    
    private T convert(Element rowData){
        String sId = getId(rowData);
        
        T ret;
        try {
            ret = this.conversionService.convert(rowData, this.getTableType());
        } catch (ConversionException ex) {
            throw new XFlatException("Cannot convert data with ID " + sId, ex);
        }
        
        if(sId == null){
            //can't do any ID stuff, return ret.
            return ret;
        }
        
        if(!this.accessor.hasId() && this.idMap != null){
            //cache the ID
            this.idMap.put(ret, sId);
        }
        else{
            try {
                this.accessor.setIdValue(ret, this.getIdGenerator().stringToId(sId, this.accessor.getIdType()));
            } catch (IllegalAccessException | InvocationTargetException ex) {
                LogFactory.getLog(getClass()).warn("Exception setting ID value " + sId + " on type " + this.accessor.getIdType(), ex);
            }
        }
        
        return ret;
    }

    private String getOrGenerateId(T rowData){
        
        if(this.accessor.hasId()){
            try{
                Object id = this.accessor.getIdValue(rowData);
                if(id != null){
                    //already have an ID
                    return this.getIdGenerator().idToString(id);
                }

                id = this.getIdGenerator().generateNewId(this.accessor.getIdType());
        
                this.accessor.setIdValue(rowData, id);
                
                return this.getIdGenerator().idToString(id);
                
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XFlatException("Cannot generate ID", ex);
            }
        }
        else{
            //if no ID property, always use string
            if(this.idMap != null){
                //doublecheck in the ID map
                String id;
                synchronized(this.idMap){
                    id = this.idMap.get(rowData);
                    if(id == null){
                        id = (String)this.getIdGenerator().generateNewId(String.class);
                        this.idMap.put(rowData, id);
                    }
                }
                return id;
            }
            
            return (String)this.getIdGenerator().generateNewId(String.class);
        }
    }
    //</editor-fold>
    
    @Override
    public void insert(T row) throws DuplicateKeyException {
        //generate new ID
        final String id = getOrGenerateId(row);
        final Element e = convert(row, id);
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.insertRow(id, e);
                return null;
            }
        });
    }

    @Override
    public T find(Object id) {
        final String sId = this.getIdGenerator().idToString(id);
        
        Element data = this.doWithEngine(new EngineAction<Element>(){
            @Override
            public Element act(Engine engine) {
                return engine.readRow(sId);
            }
        });
        
        if(data == null){
            return null;
        }
        
        T ret = convert(data);
        return ret;
    }

    @Override
    public T findOne(final XPathQuery query) {
        Element e = findOneElement(query);
        
        if(e == null){
            return null;
        }
        
        return convert(e);
    }

    private Element findOneElement(XPathQuery query){
        try(Cursor<Element> elements = this.queryTable(query)){
            Iterator<Element> it = elements.iterator();
            if(!it.hasNext()){
                return null;
            }
            
            return it.next();
        }
        catch(Exception ex){
            throw new XFlatException("Unable to close cursor", ex);
        }
    }
    
    private Cursor<Element> queryTable(final XPathQuery query){
        query.setAlternateIdExpression(alternateIdExpression);
        
        return this.doWithEngine(new EngineAction<Cursor<Element>>(){
            @Override
            public Cursor<Element> act(Engine engine) {
                return engine.queryTable(query);
            }
        });
    }
    
    @Override
    public Cursor<T> find(final XPathQuery query) {
        query.setAlternateIdExpression(alternateIdExpression);
        
        return this.doWithEngine(new EngineAction<Cursor<T>>(){
            @Override
            public Cursor<T> act(Engine engine) {
                return new ConvertingCursor(engine.queryTable(query));
            }
        });
    }

    @Override
    public List<T> findAll(XPathQuery query) {
        List<T> ret = new ArrayList<>();
        
        try(Cursor<Element> data = this.queryTable(query)){
            for(Element e : data){
                ret.add(convert(e));
            }
        }
        
        return ret;
    }

    @Override
    public void replace(T newValue) throws KeyNotFoundException {
        final String id = getId(newValue);
        if(id == null){
            throw new KeyNotFoundException("Object has no ID");
        }
        
        final Element data = convert(newValue, id);
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.replaceRow(id, data);
                return null;
            }
        });
        
    }

    @Override
    public boolean replaceOne(XPathQuery query, T newValue) {
        
        Element existing = this.findOneElement(query);
        if(existing == null){
            return false;
        }

        Element data = convert(newValue, null);
        String replacedId = recursiveReplaceOne(query, data, existing);
        if(replacedId == null){
            return false;
        }
        
        try {    
            this.accessor.setIdValue(newValue, this.getIdGenerator().stringToId(replacedId, this.accessor.getIdType()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new XFlatException("Unable to update object ID", ex);
        }
        
        return true;
    }
    
    private String recursiveReplaceOne(XPathQuery query, final Element data, Element existing){
        if(existing == null){
            return null;
        }
        
        final String id = getId(existing);
        setId(data, id);
        try{
            this.doWithEngine(new EngineAction(){
                @Override
                public Object act(Engine engine) {
                    engine.replaceRow(id, data);
                    return null;
                }
            });
            
            return id;
        }
        catch(KeyNotFoundException ex){
            //concurrent modification, try again
            existing = this.findOneElement(query);
            return recursiveReplaceOne(query, data, existing);
        }
    }

    @Override
    public boolean upsert(T newValue) {
        final String id = getOrGenerateId(newValue);
        final Element data = convert(newValue, id);
        
        return this.doWithEngine(new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.upsertRow(id, data);
            }
        });
    }

    @Override
    public boolean update(Object id, final XPathUpdate update) throws KeyNotFoundException {
        if(id == null){
            throw new IllegalArgumentException("Id cannot be null");
        }
        final String sId = this.getIdGenerator().idToString(id);
        
        return this.doWithEngine(new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.update(sId, update);
            }
        });
    }

    @Override
    public int update(final XPathQuery query, final XPathUpdate update) {
        query.setAlternateIdExpression(alternateIdExpression);
        
        return this.doWithEngine(new EngineAction<Integer>(){
            @Override
            public Integer act(Engine engine) {
                return engine.update(query, update);
            }
        });
    }

    @Override
    public void delete(Object id) throws KeyNotFoundException {
        if(id == null){
            throw new IllegalArgumentException("id cannot be null");
        }
        final String sId = this.getIdGenerator().idToString(id);
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.deleteRow(sId);
                return null;
            }
        });
    }

    @Override
    public int deleteAll(final XPathQuery query) {
        query.setAlternateIdExpression(alternateIdExpression);
        
        return this.doWithEngine(new EngineAction<Integer>(){
            @Override
            public Integer act(Engine engine) {
                return engine.deleteAll(query);
            }
        });
    }
    
    private class ConvertingCursor implements Cursor<T>{
        Cursor<Element> rowCursor;
        
        public ConvertingCursor(Cursor<Element> rowCursor){
            this.rowCursor = rowCursor;
        }

        @Override
        public Iterator<T> iterator() {
            return new ConvertingCursorIterator(this.rowCursor.iterator());
        }

        @Override
        public void close() throws XFlatException {
            this.rowCursor.close();
        }
    }
    
    private class ConvertingCursorIterator implements Iterator<T>{
        Iterator<Element> rowIterator;
        
        public ConvertingCursorIterator(Iterator<Element> rowIterator){
            this.rowIterator = rowIterator;
        }

        @Override
        public boolean hasNext() {
            return rowIterator.hasNext();
        }

        @Override
        public T next() {
            return convert(rowIterator.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported on cursors.");
        }
    }
}
