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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jdom2.Element;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.KeyValueTable;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionNotSupportedException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.xflatdb.xflat.transaction.TransactionOptions;
import org.xflatdb.xflat.transaction.TransactionScope;
import org.xflatdb.xflat.util.Action1;

/**
 * A KeyValueTable implementation using the XFlat conversion service
 * to convert values to XML.
 * @author Gordon
 */
public class ConvertingKeyValueTable extends TableBase implements KeyValueTable {
    
    private ConversionService conversionService;
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    private TransactionManager transactionService;
    /**
     * Injects the transactionService service.
     */
    public void setTransactionService(TransactionManager transactionService){
        this.transactionService = transactionService;
    }
    
    private Action1 loadPojoMapperAction = new Action1<ConvertingKeyValueTable>(){
        @Override
        public void apply(ConvertingKeyValueTable val) {            
        }
    };
    
    void setLoadPojoMapperAction(Action1 action) {
        this.loadPojoMapperAction = action;
    }
    
    public ConvertingKeyValueTable(String tableName){
        super(tableName);
    }
    

    private <T> Element convert(T data, String key){
        Element ret;
        try {
            try{
                ret = this.conversionService.convert(data, Element.class);
            } catch(ConversionNotSupportedException ex){
                //load pojo mapper and try one more time...
                this.loadPojoMapperAction.apply(this);

                ret = this.conversionService.convert(data, Element.class);
            }

        } catch (ConversionException ex) {
            throw new XFlatException("Cannot convert data with key " + key, ex);
        }        
        
        return ret;
    }
    
    private <T> T convert(Element rowData, String key, Class<T> clazz){
                
        T ret;
        try {
            try{
                ret = this.conversionService.convert(rowData, clazz);
            } catch(ConversionNotSupportedException ex){
                //load pojo mapper and try one more time...
                this.loadPojoMapperAction.apply(this);

                ret = this.conversionService.convert(rowData, clazz);
            }
        } catch (ConversionException ex) {
            throw new XFlatException("Cannot convert data with key " + key, ex);
        }        
        
        return ret;
    }
    
    @Override
    public <T> void add(final String key, T row) throws DuplicateKeyException {
        final Element e = convert(row, key);
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.insertRow(key, e);
                return null;
            }
        });
    }

    @Override
    public <T> void set(final String key, T row){
        final Element data = convert(row, key);
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.upsertRow(key, data);
                return null;
            }
        });
    }
    
    @Override
    public <T> T put(final String key, T row) {
        Class<? extends T> clazz = (Class<? extends T>)row.getClass();
        final Element data = convert(row, key);
        
        Element inRow;
        
        //since this is a two step process, must execute in transaction.
        //This transaction can be non-durable, since non-transactional table operations
        //do not guarantee durability.
        try(TransactionScope tx = this.transactionService.openTransaction(TransactionOptions.DEFAULT.withDurability(false))){
            
            inRow = this.doWithEngine(new EngineAction<Element>(){
                @Override
                public Element act(Engine engine) {
                    Element inRow = engine.readRow(key);       
                    
                    if(inRow == null)
                        engine.insertRow(key, data);
                    else
                        engine.replaceRow(key, data);
                    
                    return inRow;
                }
            });
            
            tx.commit(); 
        }
        
        if(inRow == null)
            return null;

        return convert(inRow, key, clazz);
    }

    @Override
    public <T> T get(final String key, Class<T> clazz) {
        Element data = this.doWithEngine(new EngineAction<Element>(){
            @Override
            public Element act(Engine engine) {
                return engine.readRow(key);
            }
        });
        
        if(data == null){
            return null;
        }
        
        T ret = convert(data, key, clazz);
        return ret;
    }

    @Override
    public <T> T findOne(XPathQuery query, Class<T> clazz) {
        Element e = findOneElement(query);
        
        if(e == null){
            return null;
        }
        
        return convert(e, "unknown", clazz);
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
        return this.doWithEngine(new EngineAction<Cursor<Element>>(){
            @Override
            public Cursor<Element> act(Engine engine) {
                return engine.queryTable(query);
            }
        });
    }
    
    @Override
    public <T> Cursor<T> find(final XPathQuery query, final Class<T> clazz) {
                
        return this.doWithEngine(new EngineAction<Cursor<T>>(){
            @Override
            public Cursor<T> act(Engine engine) {
                return new ConvertingCursor(engine.queryTable(query), clazz);
            }
        });
    }

    @Override
    public <T> List<T> findAll(XPathQuery query, Class<T> clazz) {
        List<T> ret = new ArrayList<>();
        
        try(Cursor<Element> data = this.queryTable(query)){
            for(Element e : data){
                ret.add(convert(e, "unknown", clazz));
            }
        }
        
        return ret;
    }

    @Override
    public <T> void replace(final String key, T newValue) throws KeyNotFoundException {
        final Element data = convert(newValue, key);
        
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.replaceRow(key, data);
                return null;
            }
        });
    }

    @Override
    public boolean update(final String key, final XPathUpdate update) throws KeyNotFoundException {
        return this.doWithEngine(new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.update(key, update);
            }
        });
    }

    @Override
    public void delete(final String key) throws KeyNotFoundException {
        this.doWithEngine(new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.deleteRow(key);
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

    
    
    private class ConvertingCursor<T> implements Cursor<T>{
        Cursor<Element> rowCursor;
        
        Class<T> clazz;
        
        public ConvertingCursor(Cursor<Element> rowCursor, Class<T> clazz){
            this.rowCursor = rowCursor;
            this.clazz = clazz;
        }

        @Override
        public Iterator<T> iterator() {
            return new ConvertingCursorIterator(this.rowCursor.iterator(), clazz);
        }

        @Override
        public void close() throws XFlatException {
            this.rowCursor.close();
        }
    }
    
    private class ConvertingCursorIterator<T> implements Iterator<T>{
        Iterator<Element> rowIterator;
        
        Class<T> clazz;
        
        public ConvertingCursorIterator(Iterator<Element> rowIterator, Class<T> clazz){
            this.rowIterator = rowIterator;
            this.clazz = clazz;
        }

        @Override
        public boolean hasNext() {
            return rowIterator.hasNext();
        }

        @Override
        public T next() {
            return convert(rowIterator.next(), "unknown", clazz);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported on cursors.");
        }
    }
    
}
