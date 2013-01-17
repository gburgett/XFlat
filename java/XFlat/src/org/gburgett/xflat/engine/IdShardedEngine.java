/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.db.Engine;
import org.gburgett.xflat.db.EngineAction;
import org.gburgett.xflat.db.ShardedEngineBase;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom2.Element;

/**
 * An engine that shards the table based on ID.  This engine manages several sub-engines,
 * which each manage one shard.
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
    public void insertRow(final String id, final Element data) throws DuplicateKeyException {
        
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.insertRow(id, data);
                return null;
            }
        });
    }

    @Override
    public Element readRow(final String id) {
        return doWithEngine(getInterval(id), new EngineAction<Element>(){
            @Override
            public Element act(Engine engine) {
                return engine.readRow(id);
            }
        });
    }

    @Override
    public Cursor<Element> queryTable(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void replaceRow(final String id, final Element data) throws KeyNotFoundException {
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.replaceRow(id, data);
                return null;
            }
        });
    }

    @Override
    public boolean update(final String id, final XpathUpdate update) throws KeyNotFoundException {
        return doWithEngine(getInterval(id), new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.update(id, update);
            }
        });
    }

    @Override
    public int update(XpathQuery query, XpathUpdate update) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean upsertRow(final String id, final Element data) {
        return doWithEngine(getInterval(id), new EngineAction<Boolean>(){
            @Override
            public Boolean act(Engine engine) {
                return engine.upsertRow(id, data);
            }
        });
    }

    @Override
    public void deleteRow(final String id) throws KeyNotFoundException {
        doWithEngine(getInterval(id), new EngineAction(){
            @Override
            public Object act(Engine engine) {
                engine.deleteRow(id);
                return null;
            }
        });
    }

    @Override
    public int deleteAll(XpathQuery query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    
}
