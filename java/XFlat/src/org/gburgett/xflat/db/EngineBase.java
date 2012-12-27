/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.convert.ConversionService;

/**
 * The base class for Engine objects.  The Database uses the functionality
 * described here to manage engines, so all engine implementations must extend
 * this base class.
 * @author gordon
 */
public abstract class EngineBase implements Engine {
    
    final private String tableName;
    @Override
    public String getTableName(){
        return tableName;
    }
    
    protected Log log = LogFactory.getLog(getClass());
    
    protected EngineBase(String tableName){
        this.tableName = tableName;
    }
    
    //<editor-fold desc="transition functions">
    
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
    
    private ScheduledExecutorService executorService;
    protected ScheduledExecutorService getExecutorService(){
        return executorService;
    }
    
    protected void setExecutorService(ScheduledExecutorService service){
        this.executorService = service;
    }
    
    private ConversionService conversionService;
    protected ConversionService getConversionService() {
        return this.conversionService;
    }
    
    protected void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
}
