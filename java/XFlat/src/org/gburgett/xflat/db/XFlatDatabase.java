/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import org.gburgett.xflat.Database;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.PojoConverter;
import org.gburgett.xflat.convert.converters.JDOMConverters;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.gburgett.xflat.db.EngineBase.EngineState;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * The base class for classes that manage tables and conversion services.
 * @author gordon
 */
public class XFlatDatabase implements Database {
    
    public static Namespace xFlatNs = Namespace.getNamespace("db", "http://xflat.gburgett.org/xflat/db");
    
    //<editor-fold desc="dependencies">
    private ScheduledExecutorService executorService;
    protected ScheduledExecutorService getExecutorService(){
        return executorService;
    }
    
    
    private ConversionService conversionService;
    
    /**
     * Gets the current conversion service.  The conversion service can be
     * set or updated in order to convert any objects.
     * @return the database's conversion service.
     */
    public ConversionService getConversionService() {
        return this.conversionService;
    }
    
    //</editor-fold>
    
    private File directory;
    protected File getDirectory(){
        return directory;
    }
    
    private AtomicReference<DatabaseState> state = new AtomicReference<>();
    public DatabaseState getState(){
        return state.get();
    }
    
    //the engine cache
    private ConcurrentHashMap<String, TableMetadata> tables = new ConcurrentHashMap<>();
    
    private DatabaseConfig config = DatabaseConfig.defaultConfig;
    public void setConfig(DatabaseConfig config){
        this.config = config;
    }
    private Map<String, TableConfig> tableConfigs = new HashMap<>();
    /**
     * Configures a table with the given table configuration.
     * @param tableName The name of the table to configure.
     * @param config The configuration to apply.
     */
    public void configureTable(String tableName, TableConfig config){
        this.tableConfigs.put(tableName, config);
    }
    
    /**
     * Creates a new database in the given directory.
     * @param directory The flat-file directory in which tables should be stored.
     */
    public XFlatDatabase(File directory){
        this.directory = directory;
        
        this.conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        
        this.state = new AtomicReference<>(DatabaseState.Uninitialized);
    }
    
    public void Initialize(){
        if(!this.state.compareAndSet(DatabaseState.Uninitialized, DatabaseState.Running)){
            return;
        }
        
        try
        {
            if(!this.directory.exists())
                this.directory.mkdirs();
        
            this.validateConfig();

            this.executorService = new ScheduledThreadPoolExecutor(this.config.getThreadCount());

            this.InitializeScheduledTasks();

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){
                @Override
                public void run() {
                    XFlatDatabase.this.shutdown();
                }
            }));
        
        }catch(Exception ex){
            this.state.set(DatabaseState.Uninitialized);
            throw new XflatException("Initialization error");
        }
    }
    
    /**
     * Shuts down the database.
     * This method blocks until the database is completely shut down, as long
     * as it takes.
     */
    public void shutdown(){
        try{
            this.shutdown(0);
        }catch(TimeoutException ex){
            throw new RuntimeException("A timeout occured that should never have happened", ex);
        }
    }
    
    /**
     * Shuts down the database.
     * This method blocks only until the timeout expires - if the database
     * is not completely shutdown in that time, a TimeoutException is thrown.
     * @param timeout The number of milliseconds to wait before timing out
     * @throws TimeoutException if the database did not fully shut down before
     * the timeout expired.
     */
    public void shutdown(int timeout) throws TimeoutException{
        if(!this.state.compareAndSet(DatabaseState.Running, DatabaseState.ShuttingDown)){
            return;
        }
        
        //by default, wait as long as it takes
        Long lTimeout = Long.MAX_VALUE;
        if(timeout > 0){
            //wait only until the timeout
            lTimeout = System.currentTimeMillis() + timeout;
        }

        //spin them all down
        for(Map.Entry<String, TableMetadata> table : this.tables.entrySet()){
            EngineState state = table.getValue().engine.getState();
            try{
                if(state == EngineState.Running){
                    table.getValue().engine.spinDown(null);
                }
                else if(state != EngineState.SpinningDown){
                    table.getValue().engine.forceSpinDown();
                }
            }catch(Exception ex){
                //eat
            }
        }

        //save all metadata
        for(Map.Entry<String, TableMetadata> table : this.tables.entrySet()){
            saveTableMetadata(table.getKey(), table.getValue().saveTableMetadata());
        }
        
        //wait for the engines to finish spinning down
        do{
            Iterator<Map.Entry<String, TableMetadata>> it = this.tables.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry<String, TableMetadata> table = it.next();
                if(table.getValue().engine.getState() == EngineState.SpunDown){
                    it.remove();
                    continue;
                }
            }

            if(this.tables.isEmpty()){
                //COOL! we're done
                return;
            }

        }while(System.currentTimeMillis() < lTimeout);

        //force any remaining tables to spin down now
        boolean anyLeft = false;
        for(Map.Entry<String, TableMetadata> table : this.tables.entrySet()){
            anyLeft = true;
            try{
                table.getValue().engine.forceSpinDown();
            }catch(Exception ex){
                //eat
            }
        }
        
        if(anyLeft)
            throw new TimeoutException("Shutdown timed out");
    }
    
    private void validateConfig(){
        
        for(Map.Entry<String, TableConfig> entry : this.tableConfigs.entrySet()){
            Document existing = this.getTableMetadata(entry.getKey());
            if(existing == null || existing.getRootElement() == null){
                //we're good here.
                continue;
            }
            Element cfg = existing.getRootElement().getChild("config", XFlatDatabase.xFlatNs);
            if(cfg == null){
                //still good
                continue;
            }
            
            TableConfig inMetadata = TableConfig.FromElementConverter.convert(cfg);
            if(!entry.getValue().equals(inMetadata)){
                throw new XflatException("Configuration for table " + entry.getKey() + 
                        " does not match stored configuration");
            }
        }
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
    
    
    /**
     * Extends the database's conversion service with the given PojoConverter.
     * It does this by invoking {@link PojoConverter#extend(org.gburgett.xflat.convert.ConversionService) }
     * using the database's current conversion service, in a synchronized context.
     * @param extender The extender that should extend the database's conversion service.
     */
    public void extendConversionService(PojoConverter extender){
        synchronized(this){
            this.conversionService = extender.extend(conversionService);
        }
    }
    
    private Document getTableMetadata(String tableName){
        File file = new File(this.directory, tableName + ".config.xml");
        if(!file.exists()){
            return null;
        }
        try {
            return new DocumentFileWrapper(file).readFile();
        } catch (IOException | JDOMException ex) {
            throw new XflatException("Error reading metadata for table " + tableName, ex);
        }
    }
    
    private void saveTableMetadata(String tableName, Document metadata){
        File file = new File(this.directory, tableName + ".config.xml");
        
        try {
            new DocumentFileWrapper(file).writeFile(metadata);
        } catch(IOException ex){
            throw new XflatException("Error saving metadata for table " + tableName, ex);
        }
    }
    
    @Override
    public <T> Table<T> getTable(Class<T> type){
        return this.getTable(type, type.getSimpleName());
    }
    
    @Override
    public <T> Table<T> getTable(Class<T> type, String name){
        if(this.state.get() == DatabaseState.Uninitialized){
            throw new IllegalStateException("Database has not been initialized");
        }
        if(this.state.get() == DatabaseState.ShuttingDown){
            throw new IllegalStateException("Database is shutting down");
        }
        
        if(!this.getConversionService().canConvert(type, Element.class) ||
                !this.getConversionService().canConvert(Element.class, type)){
            //try to load the pojo converter
            loadPojoConverter();
            
            if(!this.getConversionService().canConvert(type, Element.class) ||
                !this.getConversionService().canConvert(Element.class, type)){
                throw new UnsupportedOperationException("No conversion available between " +
                        type + " and " + Element.class);
            }
        }
        //see if we have a cached engine already
        TableMetadata table = this.tables.get(name);
        if(table == null){
            TableConfig tblConfig = this.tableConfigs.get(name);
            Class<?> idType = String.class;
            IdAccessor accessor = IdAccessor.forClass(type);
            if(accessor.hasId()){
                idType = accessor.getIdType();
            }
            
            //see if there's existing metadata
            Document metadata = getTableMetadata(name);
            if(metadata == null){
                //use the configuration data
                table = TableMetadata.makeNewTableMetadata(name, this, this.config, tblConfig, idType);
            }
            else{
                table = TableMetadata.makeTableMetadataFromDocument(name, this, metadata, tblConfig, idType);
            }
            
            TableMetadata weWereSlow = this.tables.putIfAbsent(name, table);
            if(weWereSlow != null){
                table = weWereSlow;
            }
        }
        
        TableBase ret = table.getTable(type);
        return (Table<T>)ret;
    }
    
    private AtomicBoolean pojoConverter = new AtomicBoolean(false);
    private void loadPojoConverter(){
        if(!pojoConverter.compareAndSet(false, true)){
            return;
        }
        //TODO: load JAXB context dynamically via the classloader
        //and create JAXB POJO mapper, then use it to extend conversion service.
        throw new NotImplementedException();
    }
    
    public enum DatabaseState{
        Uninitialized,
        Running,
        ShuttingDown
    }
    
}