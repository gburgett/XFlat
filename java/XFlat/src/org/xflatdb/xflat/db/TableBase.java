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

import org.xflatdb.xflat.EngineStateException;

/**
 * The base class for implementations of {@link org.xflatdb.xflat.Table}.
 * This provides dependencies to table implementations that are injected by the database.
 * @author gordon
 */
public abstract class TableBase {
        
    private String tableName;
    /**
     * Gets the table's name.
     * @return The table name.
     */
    protected String getTableName(){
        return this.tableName;
    }
    
    private IdGenerator idGenerator;
    /**
     * Injects the ID generator used by this table to generate IDs for objects
     * that do not already have them.
     * @param idGenerator The ID generator to use.
     */
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }
    /**
     * Gets the ID generator used by this table to generate IDs for objects
     * that do not already have them.
     * @return The ID generator used by the table.
     */
    protected IdGenerator getIdGenerator(){
        return this.idGenerator;
    }
    
    private EngineProvider engineProvider;
    /**
     * Creates a new Table for the given table type and name.
     * @param tableType The type of the objects in this table.
     * @param tableName The name of the table.
     */
    protected TableBase(String tableName){
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
     * <p/>
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
