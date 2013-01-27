/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.Table;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.jdom2.Attribute;
import org.jdom2.Element;

/**
 * This is the simple Table implementation for tables of JDOM Elements.
 * @author gordon
 */
public class ElementTable extends TableBase<Element> implements Table<Element> {

    
    ElementTable(String tableName){
        super(Element.class, tableName);
    }
    
    @Override
    public void insert(Element data) throws DuplicateKeyException {
        String id = getId(data);
        if(id == null){
            id = generateNewId();
            setId(id, data);
        }
        
        //always clone incoming data
        final Element cData = data.clone(); 
        final String sId = id;
        
        this.doWithEngine(new EngineAction(){
                @Override
                public Object act(Engine engine) {
                    engine.insertRow(sId, cData);
                    return null;
                }
            });
        
    }

    @Override
    public Element find(Object id) {
        
        final String sId = getId(id);
        return this.doWithEngine(new EngineAction<Element>(){
            @Override
            public Element act(Engine engine) {
                return engine.readRow(sId);
            }
        });
    }
    
    private Cursor<Element> queryTable(final XPathQuery query){
        return this.doWithEngine(new EngineAction<Cursor<Element>>(){
            @Override
            public Cursor<Element> act(Engine engine) {
                return engine.queryTable(query);
            }
        });
    }

    @Override
    public Element findOne(XPathQuery query) {
        try(Cursor<Element> c = this.queryTable(query)){
            Iterator<Element> i = c.iterator();
            if(i.hasNext()){
                return i.next();
            }
            
            return null;
        }catch(Exception ex){
            throw new XFlatException("Unable to close cursor", ex);
        }
    }

    @Override
    public Cursor<Element> find(XPathQuery query) {
        return this.queryTable(query);
    }

    @Override
    public List<Element> findAll(XPathQuery query) {
        try(Cursor<Element> c = this.queryTable(query)){
            List<Element> ret = new ArrayList<>();
            for(Element e : c){
                ret.add(e);
            }
            
            return ret;
        }catch(Exception ex){
            throw new XFlatException("Unable to close cursor", ex);
        }
    }

    @Override
    public void replace(Element newValue) throws KeyNotFoundException {
        final String id = getId(newValue);
        if(id == null){
            throw new KeyNotFoundException("Element has no ID");
        }
        
        //always clone incoming data
        final Element data = newValue.clone(); 
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.replaceRow(id, data);
                return null;
            }
        });
    }

    @Override
    public boolean replaceOne(XPathQuery query, Element origValue){
        Element e = this.findOne(query);
        if(e == null){
            return false;
        }
        
        final String id = getId(e);
        setId(id, origValue);
        
        //always clone incoming data
        final Element newValue = origValue.clone(); 

        try{            
            this.doWithEngine(new EngineAction(){
                @Override
                public Object act(Engine engine) {
                    engine.replaceRow(id, newValue);
                    return null;
                }
            });
            return true;
        }catch(KeyNotFoundException ex){
            //someone concurrently deleted this row, try again by identifying
            //a new row by the query.
            return replaceOne(query, origValue);
        }
    }

    @Override
    public boolean upsert(Element newValue) {
        
        //always clone incoming data
        final Element data = newValue.clone(); 

        final String id = getId(newValue);
        if(id == null){
            final String nId = generateNewId();
            setId(nId, newValue);
            
            
            this.doWithEngine(new EngineAction(){
                @Override
                public Object act(Engine engine) {
                    engine.insertRow(nId, data);
                    return null;
                }
            });
            //inserted, return true
            return true;
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
    public boolean update(Object id, final XPathUpdate update) throws KeyNotFoundException {
        final String sId = getId(id);
        return this.doWithEngine(new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.update(sId, update);
            }
        });
    }

    @Override
    public int update(final XPathQuery query, final XPathUpdate update) {
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
        
        final String sId = getId(id);
        
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
        return this.doWithEngine(new EngineAction<Integer>(){
            @Override
            public Integer act(Engine engine) {
                return engine.deleteAll(query);
            }
        });
    }
    
    
    private String getId(Element element){
        Attribute a = element.getAttribute("id", XFlatDatabase.xFlatNs);
        if(a != null){
            return a.getValue();
        }
        
        return null;
    }
    
    private void setId(String id, Element e){
        e.setAttribute("id", id, XFlatDatabase.xFlatNs);
    }
    
    private String generateNewId(){
        return (String)this.getIdGenerator().generateNewId(String.class);
    }

    private String getId(Object id) throws IllegalArgumentException {
        if(!(id instanceof String)){
            throw new IllegalArgumentException("Tables of element always have string IDs");
        }
        String sId = (String)id;
        return sId;
    }
    
}
