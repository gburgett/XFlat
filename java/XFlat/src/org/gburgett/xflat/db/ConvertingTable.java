/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.List;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;

/**
 * A table implementation that converts objects to elements
 * using the database's conversion service.
 * @author gordon
 */
public class ConvertingTable<T> extends TableBase<T> implements Table<T> {

    ConvertingTable(Database db, Class<T> type, String name){
        super(db, type, name);
    }

    @Override
    public void insert(T row) throws DuplicateKeyException {
        throw new UnsupportedOperationException("Not supported yet.");
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
