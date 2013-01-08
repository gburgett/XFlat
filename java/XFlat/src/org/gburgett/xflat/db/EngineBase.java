/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.convert.ConversionService;
import org.jdom2.Element;

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
    
    protected AtomicReference<EngineState> state = new AtomicReference<>(EngineState.Uninitialized);
    public EngineState getState(){
        return state.get();
    }
    
    /**
     * Initializes the engine and instructs it to begin acquiring the resources
     * necessary to function.  At this point the engine may begin to respond to
     * read-only requests, but any write requests MUST block until {@link #beginOperations() )
     * is called.
     */
    protected abstract boolean spinUp();
    
    /**
     * Instructs the engine to begin full read/write operations.  At this point this
     * engine has full control over the data files.
     */
    protected abstract boolean beginOperations();
    
    /**
     * Instructs this Engine to wrap up its operations in preparation for being
     * switched out.  The engine must be set to read-only mode (write operations
     * should throw an exception).  The engine can stay alive until its outstanding
     * cursors are closed, but must allow concurrent reads of the file.
     * @param completionEventHandler An event handler that is notified when
     * the Engine has finished spinning down.
     */
    protected abstract boolean spinDown(SpinDownEventHandler completionEventHandler);
    
    /**
     * Forces this engine to immediately release all resources, even if there are
     * still outstanding cursors.  Cursors that continue iterating will throw an
     * exception that indicates the engine has spun down.
     */
    protected abstract boolean forceSpinDown();
    
    private AtomicLong lastActivity = new AtomicLong();
    
    /**
     * Gets the date at which the last operation was performed on the engine.
     * This is used by the Database to determine whether this engine needs to
     * remain in its cache or if it can be spun down for later.
     * @return The date at which the last write or read has occurred, or the date
     * of engine creation if no operations have occurred.
     */
    public long getLastActivity(){
        return lastActivity.get();
    }
    
    /**
     * Concurrently updates the lastActivity property to the greater of the
     * existing value or the given time.
     * @param time the time to update lastActivity to, if greater than the existing
     * value.
     */
    protected void setLastActivity(long time){
        long existing;
        do{
            existing = lastActivity.get();
            if(existing >= time){
                //no need to update
                return;
            }
            //ensure we compared to the latest value before setting.
        }while(!lastActivity.compareAndSet(existing, time));
    }
    
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
    
    /**
     * Saves metadata to the given element.  Metadata is things like indexes
     * and other configuration.
     * @param metadataElement The element from which metadata should be loaded.
     */
    protected void saveMetadata(Element metadataElement){
        
    }
    /**
     * Loads metadata from the given element.  Metadata is things like indexes
     * and other configurations the engine may need to save.
     * @param metatdataElement The element to which metadata should be saved.
     */
    protected void loadMetadata(Element metatdataElement){
        
    }
 
    public enum EngineState{
        Uninitialized,
        SpinningUp,
        SpunUp,
        Running,
        SpinningDown,
        SpunDown
    }
}
