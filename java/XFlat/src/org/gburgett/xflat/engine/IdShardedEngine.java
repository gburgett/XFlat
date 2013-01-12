/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.Range;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineState;
import org.gburgett.xflat.db.ShardedEngineBase;
import org.gburgett.xflat.db.TableMetadata;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom2.Element;

/**
 *
 * @author Gordon
 */
public class IdShardedEngine<T> extends ShardedEngineBase<T> {


    public IdShardedEngine(File file, String tableName, ShardsetConfig<T> config){
        super(file, tableName, config);
        
        if((config.getShardPropertySelector().getExpression() == null ? XpathQuery.Id.getExpression() != null : !config.getShardPropertySelector().getExpression().equals(XpathQuery.Id.getExpression())) ||
                config.getShardPropertySelector().getNamespace("db") != XFlatDatabase.xFlatNs){
            throw new XflatException("IdShardedEngine must be sharded by the expression '@db:id' where db is the XFlat Namespace");
        }
    }

    @Override
    public void insertRow(String id, Element data) throws DuplicateKeyException {
        
        Engine e = this.getEngine(getRange(id));
        e.insertRow(id, data);
    }

    @Override
    public Element readRow(String id) {
        Engine e = this.getEngine(getRange(id));
        return e.readRow(id);
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void replaceRow(String id, Element data) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean update(String id, XpathUpdate update) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean upsertRow(String id, Element data) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteRow(String id) throws KeyNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int deleteAll(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
