/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom2.Element;

/**
 * A table implementation that converts objects to elements
 * using the database's conversion service.
 * @author gordon
 */
public class ConvertingTable<T> extends TableBase<T> implements Table<T> {

    private ConversionService conversionService;
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    private IdAccessor accessor;
    
    private Map<T, String> idMap;
    
    ConvertingTable(XFlatDatabase db, Class<T> type, String name){
        super(db, type, name);
        
        this.accessor = IdAccessor.forClass(type);
        if(!this.accessor.hasId()){
            //we need to keep a reference to the ID in a weak cache
            idMap = Collections.synchronizedMap(new WeakHashMap<T, String>());
        }
    }
        
    //<editor-fold desc="helpers">
    private String getId(Element rowData){
        return rowData.getAttributeValue("id", XFlatDatabase.xFlatNs);
    }
    
    private String getId(T data){
        if(this.accessor.hasId()){
            Object id;
            try {
                 id = this.accessor.getIdValue(data);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XflatException("Cannot access ID on data", ex);
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
        rowData.setAttribute("id", sId, XFlatDatabase.xFlatNs);
    }
    
    private Element convert(T data){
        return convert(data, getId(data));
    }
    
    private Element convert(T data, String id){
        Element ret;
        try {
            ret = this.conversionService.convert(data, Element.class);
        } catch (ConversionException ex) {
            throw new XflatException("Cannot convert data with ID " + id, ex);
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
            throw new XflatException("Cannot convert data with ID " + sId, ex);
        }
        
        if(sId == null){
            //can't do any ID stuff, return ret.
            return ret;
        }
        
        if(this.accessor.hasId()){
            Object id = this.getIdGenerator().stringToId(sId, this.accessor.getIdType());
            try {
                this.accessor.setIdValue(ret, id);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XflatException("Cannot set ID", ex);
            }
        }
        else if(this.idMap != null){
            //cache the ID
            this.idMap.put(ret, sId);
        }
        
        return ret;
    }

    private String generateId(T rowData){
        
        if(this.accessor.hasId()){
            Object id = this.getIdGenerator().generateNewId(this.accessor.getIdType());
        
            try{
                this.accessor.setIdValue(rowData, id);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XflatException("Cannot set newly-generated ID", ex);
            }
            return this.getIdGenerator().idToString(id);
        }
        else{
            //if no ID property, always use string
            String id = (String)this.getIdGenerator().generateNewId(String.class);
            if(this.idMap != null){
                this.idMap.put(rowData, id);
            }
            return id;
        }
    }
    //</editor-fold>
    
    @Override
    public void insert(T row) throws DuplicateKeyException {
        final Element e = convert(row);
        String id = getId(e);
        if(id == null){
            //generate new ID
             id = generateId(row);
             setId(e, id);
        }
        
        final String sId = id;
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.insertRow(sId, e);
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
    public T findOne(final XpathQuery query) {
        Element e = findOneElement(query);
        
        if(e == null){
            return null;
        }
        
        return convert(e);
    }

    private Element findOneElement(XpathQuery query){
        try(Cursor<Element> elements = this.queryTable(query)){
            Iterator<Element> it = elements.iterator();
            if(!it.hasNext()){
                return null;
            }
            
            return it.next();
        }
        catch(Exception ex){
            throw new XflatException("Unable to close cursor", ex);
        }
    }
    
    private Cursor<Element> queryTable(final XpathQuery query){
        return this.doWithEngine(new EngineAction<Cursor<Element>>(){
            @Override
            public Cursor<Element> act(Engine engine) {
                return engine.queryTable(query);
            }
        });
    }
    
    @Override
    public Cursor<T> find(final XpathQuery query) {
        return this.doWithEngine(new EngineAction<Cursor<T>>(){
            @Override
            public Cursor<T> act(Engine engine) {
                return new ConvertingCursor(engine.queryTable(query));
            }
        });
    }

    @Override
    public List<T> findAll(XpathQuery query) {
        List<T> ret = new ArrayList<>();
        
        try(Cursor<Element> data = this.queryTable(query)){
            for(Element e : data){
                ret.add(convert(e));
            }
        }
        catch(Exception ex){
            throw new XflatException("Unable to close cursor", ex);
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
    public boolean replaceOne(XpathQuery query, T newValue) {
        
        Element existing = this.findOneElement(query);
        if(existing == null){
            return false;
        }

        Element data = convert(newValue);
        String replacedId = recursiveReplaceOne(query, data, existing);
        if(replacedId == null){
            return false;
        }
        
        try {    
            this.accessor.setIdValue(newValue, this.getIdGenerator().stringToId(replacedId, this.accessor.getIdType()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new XflatException("Unable to update object ID", ex);
        }
        
        return true;
    }
    
    private String recursiveReplaceOne(XpathQuery query, final Element data, Element existing){
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
        final Element data = convert(newValue);
        final String id = getId(data);
        
        if(id == null){
            //insert
            final String nId = generateId(newValue);
            setId(data, nId);
            
            this.doWithEngine(new EngineAction(){
                @Override
                public Object act(Engine engine) {
                    engine.insertRow(nId, data);
                    return null;
                }
            });
            
            return true;    //inserted
        }
        else{
            return this.doWithEngine(new EngineAction<Boolean>(){
                @Override
                public Boolean act(Engine engine) {
                    return engine.upsertRow(id, data);
                }
            });
        }
    }

    @Override
    public boolean update(Object id, final XpathUpdate update) throws KeyNotFoundException {
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
    public int update(final XpathQuery query, final XpathUpdate update) {
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
    public int deleteAll(final XpathQuery query) {
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
        public void close() throws Exception {
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
