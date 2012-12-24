/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.engine.Engine;

/**
 *
 * @author gordon
 */
public abstract class TableBase<T> {
    
    private Database database;
    protected Database getDatabase(){
        return this.database;
    }
    
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
    
    private AtomicReference<Engine> engine;
    private final Object syncRoot = new Object();
    
    
    protected TableBase(Database db, Class<T> tableType, String tableName){
        this.database = db;
        this.engine = new AtomicReference<>();
        this.tableType = tableType;
    }
    
    /**
     * Clears out the current engine in preparation for swapping engines.
     * While the engine is cleared, operations on the table will cause the thread
     * to wait until the new engine is set.  The wait times out after 100 ms.
     */
    void clearEngine(){
        engine.set(null);
    }
    
    /**
     * Sets the new engine for this Table.  This wakes any threads that were
     * waiting on the new engine.
     * @param engine The new engine for the table.
     */
    void setEngine(Engine engine){
        if(engine == null){
            throw new IllegalArgumentException("Engine cannot be null - use clearEngine instead");
        }
        
        synchronized(syncRoot){
            this.engine.set(engine);
        
            //we successfully updated to a new engine
            syncRoot.notifyAll();
        }
    }
    
    /**
     * Gets the current engine.  If the engine is in the process of being swapped,
     * the thread is blocked until the new engine is set.  The blocking times out
     * after 100ms.
     * @return The current engine.
     */
    protected Engine getEngine(){
        Engine ret = engine.get();
        
        if(engine == null){
            try{
                //we're in the process of swapping engines, wait for completion
                long start = System.currentTimeMillis();
                synchronized(syncRoot){
                    //while loop because of potential spurious wakeups
                    while((ret = engine.get()) == null) {
                        syncRoot.wait(100);
                        //did we time out?
                        if(System.currentTimeMillis() - start > 100){
                            throw new XflatException("Timeout while swapping database engines");
                        }
                    }
                }
            }
            catch(InterruptedException ex){
                throw new XflatException("Interrupted while waiting for engine swap to complete.", ex);
            }
        }
        
        return ret;
    }
    
    
}
