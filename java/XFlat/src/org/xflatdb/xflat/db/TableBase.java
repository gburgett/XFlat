/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import org.xflatdb.xflat.EngineStateException;

/**
 *
 * @author gordon
 */
public abstract class TableBase<T> {
        
    private Class<T> tableType;
    protected Class<T> getTableType(){
        return this.tableType;
    }
    
    private String tableName;
    protected String getTableName(){
        return this.tableName;
    }
    
    private IdGenerator idGenerator;
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }
    protected IdGenerator getIdGenerator(){
        return this.idGenerator;
    }
    
    private EngineProvider engineProvider;
    
    protected TableBase(Class<T> tableType, String tableName){
        this.tableType = tableType;
        this.tableName = tableName;
    }
    
    /**
     * Sets the new engine for this Table.  This wakes any threads that were
     * waiting on the new engine.
     * @param engine The new engine for the table.
     */
    void setEngineProvider(EngineProvider engine){
        if(engine == null){
            throw new IllegalArgumentException("EngineProvider cannot be null - use clearEngine instead");
        }
        
        this.engineProvider = engine;
    }
    
    /**
     * Performs an action with an engine obtained from the EngineProvider.
     * Also wraps the action in some special engine error handling.
     * 
     * Beware that the action may be executed multiple times depending on the retry
     * logic of this method.
     * @param <T> The return type of the action.
     * @param action The action to perform.
     * @return The value returned by the action.
     */
    protected <T> T doWithEngine(EngineAction<T> action){
        try{
            return action.act(this.engineProvider.provideEngine());
        }
        catch(EngineStateException ex){
            //The engine we got may have been just spun down by another thread,
            //try again with a new engine and if it happens again let it throw
            return action.act(this.engineProvider.provideEngine());
        }
    }
}
