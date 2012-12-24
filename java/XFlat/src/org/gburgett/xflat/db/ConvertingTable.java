/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
    
    ConvertingTable(Database db, Class<T> type, String name){
        super(db, type, name);
        
        this.accessor = IdAccessor.forClass(type);
        if(!this.accessor.hasId()){
            //we need to keep a reference to the ID in a weak cache
            idMap = new WeakHashMap<>();
        }
    }
        
    //<editor-fold desc="helpers">
    private String getId(Element rowData){
        return rowData.getAttributeValue("id", Database.xFlatNs);
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
        rowData.setAttribute("id", sId, Database.xFlatNs);
    }
    
    private Element convert(T data){
        return convert(data, getId(data));
    }
    
    private Element convert(T data, String id){
        Element ret = this.conversionService.convert(data, Element.class);
        
        if (id != null){
            setId(ret, id);
        }
        
        return ret;
    }
    
    private T convert(Element rowData){
        T ret = this.conversionService.convert(rowData, this.getTableType());
        
        String sId = getId(rowData);
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
        Element e = convert(row);
        String id = getId(e);
        if(id == null){
            //generate new ID
             id = generateId(row);
             setId(e, id);
        }
        
        this.getEngine().insertRow(id, e);
    }

    @Override
    public T find(Object id) {
        String sId = this.getIdGenerator().idToString(id);
        Element data = this.getEngine().readRow(sId);
        if(data == null){
            return null;
        }
        
        T ret = convert(data);
        return ret;
    }

    @Override
    public T findOne(XpathQuery query) {
        Element e = findOneElement(query);
        if(e == null){
            return null;
        }
        
        return convert(e);
    }

    private Element findOneElement(XpathQuery query){
        try(Cursor<Element> elements = this.getEngine().queryTable(query)){
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
    
    @Override
    public Cursor<T> find(XpathQuery query) {
        return new ConvertingCursor(this.getEngine().queryTable(query));
    }

    @Override
    public List<T> findAll(XpathQuery query) {
        List<T> ret = new ArrayList<>();
        
        try(Cursor<Element> data = this.getEngine().queryTable(query)){
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
        String id = getId(newValue);
        if(id == null){
            throw new KeyNotFoundException("Object has no ID");
        }
        
        Element data = convert(newValue, id);
        this.getEngine().replaceRow(id, data);
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
    
    private String recursiveReplaceOne(XpathQuery query, Element data, Element existing){
        if(existing == null){
            return null;
        }
        
        String id = getId(existing);
        setId(data, id);
        try{
            this.getEngine().replaceRow(id, data);
            
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
        Element data = convert(newValue);
        String id = getId(data);
        if(id == null){
            //insert
            id = generateId(newValue);
            setId(data, id);
            this.getEngine().insertRow(id, data);
            
            return true;    //inserted
        }
        else{
            return this.getEngine().upsertRow(id, data);
        }
    }

    @Override
    public boolean update(Object id, XpathUpdate update) throws KeyNotFoundException {
        if(id == null){
            throw new IllegalArgumentException("Id cannot be null");
        }
        String sId = this.getIdGenerator().idToString(id);
        
        return this.getEngine().update(sId, update);
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        return this.getEngine().update(query, update);
    }

    @Override
    public void delete(Object id) throws KeyNotFoundException {
        if(id == null){
            throw new IllegalArgumentException("id cannot be null");
        }
        String sId = this.getIdGenerator().idToString(id);
        
        this.getEngine().deleteRow(sId);
    }

    @Override
    public int deleteAll(XpathQuery query) {
        return this.getEngine().deleteAll(query);
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
