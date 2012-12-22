/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.PojoMapper;
import org.gburgett.xflat.engine.Engine;
import org.jdom2.Content;
import org.jdom2.Namespace;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * The base class for classes that manage tables and conversion services.
 * @author gordon
 */
public abstract class Database {
    
    public static Namespace xFlatNs = Namespace.getNamespace("db", "http://xflat.gburgett.org/xflat/db");
    
    protected ScheduledExecutorService executorService;
    
    private File directory;
    protected File getDirectory(){
        return directory;
    }
    
    //the engine cache
    private Map<File, Engine> engines = new HashMap<>();
    
    private ConversionService conversionService;
    /**
     * Gets the current conversion service.  The conversion service can be
     * set or updated in order to convert any objects.
     * @return the database's conversion service.
     */
    public ConversionService getConversionService() {
        return this.conversionService;
    }
    /** @see #getConversionService()  */
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
    
    /**
     * Creates a new database in the given directory.
     * @param directory The flat-file directory in which tables should be stored.
     */
    protected Database(File directory){
        this.directory = directory;
        
        this.executorService = new ScheduledThreadPoolExecutor(2);
        this.conversionService = new DefaultConversionService();
        
        this.InitializeScheduledTasks();
    }
    
    private void InitializeScheduledTasks(){
        this.executorService.scheduleWithFixedDelay(new Runnable(){
            @Override
            public void run() {
                update();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Called periodically by the executor service to perform maintenance
     * on the DB.
     */
    private void update(){
        //Check the current file sizes of each currently cached engine.
        //If the file sizes necessitate a change of engine, clear the engine
        //on each cached TableBase and spin it down, then set the new engine
        //and spin it up.  The whole process must take < 100 ms or else
        //the timeout in TableBase must be turned up.
    }
    
    
    public <T> Table<T> getTable(String name, Class<T> type){
        if(!this.getConversionService().canConvert(type, Content.class) ||
                !this.getConversionService().canConvert(Content.class, type)){
            //register the pojo mapping for this table type
            createPojoMapper(type).registerPojoMapping(type, this.conversionService);
        }
        
        throw new NotImplementedException();
                
        //TODO: keep cache of TableBase objects using Weak References:
        //http://docs.oracle.com/javase/6/docs/api/java/lang/ref/WeakReference.html
        
        //The name is mapped to a java.io.File, which is the key of the engine cache.
    }
    
    private PojoMapper createPojoMapper(Class<?> type){
        //TODO: load JAXB context dynamically via the classloader
        //and create JAXB POJO mapper, then cache it.
        throw new NotImplementedException();
    }
}
