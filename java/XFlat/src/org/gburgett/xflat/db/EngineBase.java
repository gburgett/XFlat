/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The base class for Engine objects.  The Database uses the functionality
 * described here to manage engines, so all engine implementations must extend
 * this base class.
 * @author gordon
 */
public abstract class EngineBase implements Engine {
    
    final protected Database database;
    
    final private String tableName;
    @Override
    public String getTableName(){
        return tableName;
    }
    
    protected Log log = LogFactory.getLog(getClass());
    
    protected EngineBase(Database db, String tableName){
        this.database = db;
        this.tableName = tableName;
    }
    
    //<editor-fold desc="transition functions">
    /**
     * Instructs the engine to take over for the previous engine.  In the process
     * this engine will spin up and begin operations in coordination with the 
     * previous engine.
     * @param lastEngine The engine that was previously managing the dataset.
     */
    protected final void takeOver(final EngineBase lastEngine)
    {
        final AtomicBoolean hasSpunUp = new AtomicBoolean(false);
        final AtomicBoolean hasSpunDown = new AtomicBoolean(false);
        final AtomicBoolean hasBegunOperations = new AtomicBoolean(false);
        final Object syncRoot = new Object();
        
        if(log.isDebugEnabled())
            log.debug("Taking over for engine " + lastEngine.toString());
        
        lastEngine.spinDown(new SpinDownEventHandler(){
            @Override
            public void spinDownComplete(SpinDownEvent event) {
                synchronized(syncRoot){
                    if(hasSpunDown.get())
                        return;
                    
                    hasSpunDown.set(true);

                    if(log.isTraceEnabled())
                        log.trace(lastEngine.toString() + " finished spinning down");

                    if(hasSpunUp.get()){
                        if(log.isTraceEnabled())
                            log.trace("beginning operations");
                    
                        if(hasBegunOperations.get()){
                            return;
                        }
                        
                        EngineBase.this.beginOperations();
                        hasBegunOperations.set(true);
                    }
                }
            }
        });
        
        this.spinUp();
        hasSpunUp.set(true);
        
        if(hasSpunDown.get()){
            if(log.isTraceEnabled())
                log.trace("beginning operations");
            this.beginOperations();
            return;
        }
        //else
        
        //create a monitoring task that will repeatedly see if any write
        //operations have been performed on this new engine.  If so, we give
        //approx 100 ms for the old engine to complete, during which time
        //the write operation is blocked but after that we force shutdown
        //the old engine.
        final AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
        task.set(this.database.getExecutorService().scheduleWithFixedDelay(
        new Runnable(){
            @Override
            public void run() {
                if(hasSpunDown.get()){
                    ScheduledFuture<?> t = task.get();
                    if(t != null)
                        t.cancel(false);
                    return;
                }
                Date lastModified = getLastModified();
                if(lastModified == null ||
                    lastModified.after(new Date(System.currentTimeMillis() - 100)))
                {
                    return;
                }

                //the old engine timed out while spinning down
                ScheduledFuture<?> t = task.get();
                if(t != null)
                    t.cancel(false);

                synchronized(syncRoot){
                    if(!hasSpunDown.get()){
                        try{
                            log.debug("forcing spin down of engine " + lastEngine.toString());
                            lastEngine.forceSpinDown();
                            hasSpunDown.set(true);
                        }catch(Exception ex){
                            log.warn("failure spinning down engine " + lastEngine.toString(), ex);
                        }

                        if(hasBegunOperations.get()){
                            return;
                        }

                        EngineBase.this.beginOperations();
                        hasBegunOperations.set(true);
                    }
                }
            }
        }, 100, 100, TimeUnit.MILLISECONDS));
        
    }
    
    /**
     * Initializes the engine and instructs it to begin acquiring the resources
     * necessary to function.  At this point the engine may begin to respond to
     * read-only requests, but any write requests MUST block until {@link #beginOperations() )
     * is called.
     */
    protected abstract void spinUp();
    
    /**
     * Instructs the engine to begin full read/write operations.  At this point this
     * engine has full control over the data files.
     */
    protected abstract void beginOperations();
    
    /**
     * Instructs this Engine to wrap up its operations in preparation for being
     * switched out.  The engine must be set to read-only mode (write operations
     * should throw an exception).  The engine can stay alive until its outstanding
     * cursors are closed, but must allow concurrent reads of the file.
     * @param completionEventHandler An event handler that is notified when
     * the Engine has finished spinning down.
     */
    protected abstract void spinDown(SpinDownEventHandler completionEventHandler);
    
    /**
     * Forces this engine to immediately release all resources, even if there are
     * still outstanding cursors.  Cursors that continue iterating will throw an
     * exception that indicates the engine has spun down.
     */
    protected abstract void forceSpinDown();
    
    /**
     * Gets the date at which the data in the engine was last modified.
     * This is used by the Database to determine whether this engine needs to
     * remain in its cache or if it can be spun down for later.
     * @return The date at which the last "write" command occurred, or null if no
     * writes have yet occurred.
     */
    protected abstract Date getLastModified();
    
    public static interface SpinDownEventHandler{
        /**
         * Called when the engine is completely finished spinning down,
         * and is ready to release all filesystem references.
         * @param event 
         */
        public void spinDownComplete(SpinDownEvent event);
    }
    
    public static class SpinDownEvent extends java.util.EventObject{
        @Override
        public Engine getSource(){
            return (Engine)super.getSource();
        }
        
        public SpinDownEvent(Engine source){
            super(source);
        }
    }

    //</editor-fold>
    
    protected ScheduledExecutorService getExecutorService(){
        return database.getExecutorService();
    }
    
}
