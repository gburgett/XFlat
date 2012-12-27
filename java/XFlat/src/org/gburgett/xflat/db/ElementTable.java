/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom2.Attribute;
import org.jdom2.Element;

/**
 * This is the simple Table implementation for tables of JDOM Elements.
 * @author gordon
 */
public class ElementTable extends TableBase<Element> implements Table<Element> {

    
    ElementTable(Database db, String tableName){
        super(db, Element.class, tableName);
    }
    
    @Override
    public void insert(Element data) throws DuplicateKeyException {
        //always clone incoming data
        data = data.clone(); 
        
        String id = getId(data);
        if(id == null){
            id = generateNewId();
            setId(id, data);
        }
        
        this.getEngine().insertRow(id, data);
    }

    @Override
    public Element find(Object id) {
        
        String sId = getId(id);
        return this.getEngine().readRow(sId);
    }

    @Override
    public Element findOne(XpathQuery query) {
        try(Cursor<Element> c = this.getEngine().queryTable(query)){
            Iterator<Element> i = c.iterator();
            if(i.hasNext()){
                return i.next();
            }
            
            return null;
        }catch(Exception ex){
            throw new XflatException("Unable to close cursor", ex);
        }
    }

    @Override
    public Cursor<Element> find(XpathQuery query) {
        return this.getEngine().queryTable(query);
    }

    @Override
    public List<Element> findAll(XpathQuery query) {
        try(Cursor<Element> c = this.getEngine().queryTable(query)){
            List<Element> ret = new ArrayList<>();
            for(Element e : c){
                ret.add(e);
            }
            
            return ret;
        }catch(Exception ex){
            throw new XflatException("Unable to close cursor", ex);
        }
    }

    @Override
    public void replace(Element newValue) throws KeyNotFoundException {
        //always clone incoming data
        newValue = newValue.clone(); 
        
        String id = getId(newValue);
        if(id == null){
            throw new KeyNotFoundException("Element has no ID");
        }
        
        this.getEngine().replaceRow(id, newValue);
    }

    @Override
    public boolean replaceOne(XpathQuery query, Element newValue){
        //always clone incoming data
        newValue = newValue.clone(); 
        
        Element e = this.findOne(query);
        if(e == null){
            return false;
        }
        
        String id = getId(e);
        setId(id, newValue);
        
        try{
            this.getEngine().replaceRow(id, newValue);
            return true;
        }catch(KeyNotFoundException ex){
            //someone concurrently deleted this row, try again by identifying
            //a new row by the query.
            return replaceOne(query, newValue);
        }
    }

    @Override
    public boolean upsert(Element newValue) {
        //always clone incoming data
        newValue = newValue.clone(); 
        
        String id = getId(newValue);
        if(id == null){
            id = generateNewId();
            setId(id, newValue);
            this.getEngine().insertRow(id, newValue);
            //inserted, return true
            return true;
        }
        else{
            return this.getEngine().upsertRow(id, newValue);
        }
    }

    @Override
    public boolean update(Object id, XpathUpdate update) throws KeyNotFoundException {
        String sId = getId(id);
        return this.getEngine().update(sId, update);
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        return this.getEngine().update(query, update);
    }

    @Override
    public void delete(Object id) throws KeyNotFoundException {
        String sId = getId(id);
        this.getEngine().deleteRow(sId);
    }

    @Override
    public int deleteAll(XpathQuery query) {
        return this.getEngine().deleteAll(query);
    }
    
    
    private String getId(Element element){
        Attribute a = element.getAttribute("id", Database.xFlatNs);
        if(a != null){
            return a.getValue();
        }
        
        return null;
    }
    
    private void setId(String id, Element e){
        e.setAttribute("id", id, Database.xFlatNs);
    }
    
    private String generateNewId(){
        //TODO: move this into ID generator class
        return UUID.randomUUID().toString();
    }

    private String getId(Object id) throws IllegalArgumentException {
        if(!(id instanceof String)){
            throw new IllegalArgumentException("Tables of element always have string IDs");
        }
        String sId = (String)id;
        return sId;
    }
    
}
