/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
        
        this.accessor = new IdAccessor(type);
        if(!this.accessor.hasId()){
            //we need to keep a reference to the ID in a weak cache
            idMap = new WeakHashMap<>();
        }
    }
    
    private void maybeKeepId(T row, String id){
        if(idMap != null){
            idMap.put(row, id);
        }
    }
    
    private String maybeGetId(T row){
        if(idMap != null){
            return idMap.get(row);
        }
        return null;
    }
    
    private String getId(Element rowData){
        return rowData.getAttributeValue("id", Database.xFlatNs);
    }
    
    private void setId(Element rowData, String sId){
        rowData.setAttribute("id", sId, Database.xFlatNs);
    }
    
    private Element convert(T data){
        Element ret = this.conversionService.convert(data, Element.class);
        if(this.accessor.hasId()){
            Object id;
            try {
                id = this.accessor.getIdValue(data);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new XflatException("Cannot convert data: ID cannot be retrieved", ex);
            }
            if(id != null){
                String sId = this.getIdGenerator().idToString(id);
                setId(ret, sId);
            }
        }
        else if(this.idMap != null){
            //did we cache the ID?
            String sId = this.idMap.get(data);
            if(sId != null){
                ret.setAttribute("id", sId, Database.xFlatNs);
            }
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
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public T findOne(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Cursor<T> find(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<T> findAll(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void replace(T newValue) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean replaceOne(XpathQuery query, T newValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean upsert(T newValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void update(Object id, XpathUpdate update) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void delete(Object id) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int deleteAll(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
      
}
