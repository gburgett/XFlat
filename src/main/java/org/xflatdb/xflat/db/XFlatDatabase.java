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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.xflatdb.xflat.Database;
import org.xflatdb.xflat.DatabaseBuilder;
import org.xflatdb.xflat.DatabaseConfig;
import org.xflatdb.xflat.KeyValueTable;
import org.xflatdb.xflat.Table;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatConstants;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.DefaultConversionService;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.convert.converters.DateConverters;
import org.xflatdb.xflat.convert.converters.JDOMConverters;
import org.xflatdb.xflat.convert.converters.StringConverters;
import org.xflatdb.xflat.engine.DefaultEngineFactory;
import org.xflatdb.xflat.transaction.ThreadContextTransactionManager;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.xflatdb.xflat.util.Action1;
import org.xflatdb.xflat.util.DocumentFileWrapper;

/**
 * This database implementation manages a local directory of tables.  It is the 
 * main database implementation inside XFlat.
 * <p/>
 * Within the directory, each table is represented by an XML file.  If the table
 * is sharded, it is represented as a subdirectory, in which each shard is represented
 * as an XML file.
 * <p/>
 * This implementation supports the following requirements:<br/>
 * <pre>
 * "transactional" : true | [ A, C, I, D ]
 * "local" : true
 * "threadsafe" : true
 * </pre>
 * @author gordon
 */
public class XFlatDatabase implements Database {
    
    
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
    
    private EngineFactory engineFactory = new DefaultEngineFactory();
    /**
     * Sets the {@link EngineFactory} used to create {@link Engine} instances
     * for the tables.
     * @param factory 
     */
    public void setEngineFactory(EngineFactory factory){
        this.engineFactory = factory;
    }
    /**
     * @see #setEngineFactory(org.xflatdb.xflat.db.EngineFactory) 
     */
    public EngineFactory getEngineFactory(){
        return this.engineFactory;
    }
    
    private TableMetadataFactory metadataFactory;
    TableMetadataFactory getMetadataFactory(){
        return metadataFactory;
    }
    
    void setMetadataFactory(TableMetadataFactory factory){
        this.metadataFactory = factory;
    }
    
    private EngineTransactionManager transactionManager;
    /**
     * Gets the transactionManager.
     */
    @Override
    public TransactionManager getTransactionManager(){
        return this.transactionManager;
    }
    
    protected EngineTransactionManager getEngineTransactionManager(){
        return this.transactionManager;
    }
    
    public void setTransactionManager(EngineTransactionManager transactionManager){
        this.transactionManager = transactionManager;
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
    
    private final Thread shutdownHook = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        XFlatDatabase.this.shutdown(1000);
                    } catch (TimeoutException ex) {
                        log.warn("Timed out while shutting down database " + directory);
                    }
                }
            });
    
    //the engine cache
    private ConcurrentHashMap<String, TableMetadata> tables = new ConcurrentHashMap<>();
    
    private DatabaseConfig config = new DatabaseConfig();
    public void setConfig(DatabaseConfig config){
        if(this.state.get() != DatabaseState.Uninitialized){
            throw new XFlatException("Cannot configure database after initialization");
        }
        this.config = config;
    }
    public DatabaseConfig getConfig(){
        return config;
    }
    
    private Map<String, TableConfig> tableConfigs = new HashMap<>();
    /**
     * Configures a table with the given table configuration.
     * @param tableName The name of the table to configure.
     * @param config The configuration to apply.
     */
    void configureTable(String tableName, TableConfig config){
        if(this.state.get() != DatabaseState.Uninitialized){
            throw new XFlatException("Cannot configure table after initialization");
        }
        
        this.tableConfigs.put(tableName, config);
    }
    
    private Log log = LogFactory.getLog(getClass());
    
    private AtomicBoolean pojoConverterLoaded = new AtomicBoolean(false);
    private volatile PojoConverter pojoConverter;
    /**
     * Gets the PojoConverter that has been used to extend the database's conversion service.
     * This overrides any PojoConverter that was defined in the Database Configuration.
     */
    public PojoConverter getPojoConverter(){
        return pojoConverter;
    }
    
    /**
     * Extends the database's conversion service with the given PojoConverter.
     * This overrides any PojoConverter that was defined in the Database Configuration.
     * @param converter The converter that should extend the database's conversion service.
     */
    public void setPojoConverter(PojoConverter converter){
        synchronized(this){
            this.conversionService = converter.extend(conversionService);
            this.pojoConverter = converter;
        }
    }
    
    //<editor-fold desc="construction">
    
    /**
     * Creates a new database in the given directory.
     * @param directory The flat-file directory in which tables should be stored.
     */
    XFlatDatabase(File directory){
        this(directory, null);
    }
    
    /**
     * Creates a new database in the given directory.
     * @param directory The flat-file directory in which tables should be stored.
     * @param executorService The executor service to use for all database-related
     * tasks.  If null, the database will create one in Initialize.
     */
    protected XFlatDatabase(File directory, ScheduledExecutorService executorService){
        this.directory = directory;
        
        this.conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        DateConverters.registerTo(conversionService);
        
        this.executorService = executorService;
        
        this.metadataFactory = new TableMetadataFactory(this, new File(directory, "xflat_metadata"));
        
        this.state = new AtomicReference<>(DatabaseState.Uninitialized);
    }
    
    /**
     * Builds a new XFlatDatabase around the given file.  The returned builder
     * can be used to configure the database.
     * @param file The directory to be managed by the XFlatDatabase.
     * @return A DatabaseBuilder which constructs instances of XFlatDatabase.
     */
    public static DatabaseBuilder<XFlatDatabase> Build(File file){
        return new DatabaseBuilder<>(file.toURI(), new XFlatDatabaseProvider());
    }
    
    //</editor-fold>
    
    /**
     * Initializes the database.  Once initialized the database can provide tables
     * and operate on underlying data.
     * <p/>
     * The database will register a shutdown hook with the runtime to clean up any
     * resources and abandon all running tasks.  This shutdown hook will be removed
     * when {@link #shutdown() } is called.
     */
    void initialize(){
        if(!this.state.compareAndSet(DatabaseState.Uninitialized, DatabaseState.Initializing)){
            return;
        }
        
        try
        {
            String hello = String.format("Starting up XFlat database, version %s", 
                    org.xflatdb.xflat.Version.VERSION);
            if(org.xflatdb.xflat.Version.BUILD_REVISION > 0){
                hello += String.format("\r\n        development revision %d (%s)", 
                        org.xflatdb.xflat.Version.BUILD_REVISION,
                        org.xflatdb.xflat.Version.BUILD_COMMIT.substring(0, 7));
            }
            log.info(hello);
            
            if(!this.directory.exists())
                this.directory.mkdirs();
        
            this.validateConfig();            
            
            if(this.executorService == null)
                this.executorService = new ScheduledThreadPoolExecutor(this.config.getThreadCount());

            if(this.transactionManager == null){
                this.transactionManager = new ThreadContextTransactionManager(new DocumentFileWrapper(new File(directory, "xflat_transaction")));
            }
            
            this.InitializeScheduledTasks();

            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        
            
            //recover transactional state if necessary
            
            this.transactionManager.recover(this);
            
            //done initializing
            this.state.set(DatabaseState.Running);
        }catch(Exception ex){
            this.state.set(DatabaseState.Uninitialized);
            throw new XFlatException("Initialization error", ex);
        }
    }
    
    /**
     * Shuts down the database.
     * This method blocks until the database is completely shut down, as long
     * as it takes.
     */
    public void shutdown(){
        try{
            this.doShutdown(0);
        }catch(TimeoutException ex){
            throw new RuntimeException("A timeout occured that should never have happened", ex);
        }
        finally{
            //close all resources
            this.getEngineTransactionManager().close();
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
        try{
            this.doShutdown(timeout);
        }
        finally{
            //close all resources
            this.getEngineTransactionManager().close();
        }
    }
    
    private void doShutdown(int timeout) throws TimeoutException{
        if(!this.state.compareAndSet(DatabaseState.Running, DatabaseState.ShuttingDown)){
            return;
        }
        
        if(log.isTraceEnabled())
            log.trace(String.format("Shutting down, timeout %dms", timeout));
        
        //by default, wait as long as it takes
        Long lTimeout = Long.MAX_VALUE;
        if(timeout > 0){
            //wait only until the timeout
            lTimeout = System.currentTimeMillis() + timeout;
        }

        //spin them all down
        Set<EngineBase> engines = new HashSet<>();
        for(Map.Entry<String, TableMetadata> table : this.tables.entrySet()){
            try{
                EngineBase e = table.getValue().spinDown(true, false);
                if(e != null){
                    if(e.getState() == EngineState.Running){
                        //don't care, force spin down
                        e.spinDown(null);
                    }
                    engines.add(e);
                }
            }catch(Exception ex){
                //eat
            }
        }

        //save all metadata
        for(Map.Entry<String, TableMetadata> table : this.tables.entrySet()){
            try {
                this.metadataFactory.saveTableMetadata(table.getValue());
            } catch (IOException ex) {
                this.log.warn("Unable to save metadata for table " + table.getKey(), ex);
            }
        }
        
        this.tables.clear();
        
        //wait for the engines to finish spinning down
        do{
            Iterator<EngineBase> it = engines.iterator();
            while(it.hasNext()){
                EngineBase e = it.next();
                EngineState state = e.getState();
                if(state == EngineState.Uninitialized ||
                        state == EngineState.SpunDown){
                    it.remove();
                    continue;
                }
            }

            if(engines.isEmpty()){
                //COOL! we're done
                return;
            }

        }while(System.currentTimeMillis() < lTimeout);

        //force any remaining tables to spin down now
        boolean anyLeft = false;
        for(EngineBase engine : engines){
            anyLeft = true;
            try{
                if(engine != null)
                    engine.forceSpinDown();
            }catch(Exception ex){
                //eat
            }
        }
        
        if(anyLeft)
            throw new TimeoutException("Shutdown timed out");
        
        try{
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }catch(Exception ex){
            //that's ok
        }
    }
    
    private void validateConfig(){
        
        for(Map.Entry<String, TableConfig> entry : this.tableConfigs.entrySet()){
            Document existing = this.metadataFactory.getMetadataDoc(entry.getKey());
            if(existing == null || existing.getRootElement() == null){
                //we're good here.
                continue;
            }
            Element cfg = existing.getRootElement().getChild("config", XFlatConstants.xFlatNs);
            if(cfg == null){
                //still good
                continue;
            }

            try {
                
                TableConfig inMetadata = TableConfig.FromElementConverter.convert(cfg);
                if(!entry.getValue().equals(inMetadata)){
                    throw new XFlatException("Configuration for table " + entry.getKey() +
                            " does not match stored configuration");
                }
            } catch (ConversionException ex) {
                //table metadata is corrupt, ignore but warn
                log.warn("The metadata for table " + entry.getKey() + " is corrupt", ex);
                
                continue;
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
        
        //check on inactivity shutdown
        for(TableMetadata m : this.tables.values()){
            if(m.canSpinDown()){
                //spin down if no uncommitted data
                m.spinDown(false, false);
            }
            //don't ever remove TableMetadata.  It's too dangerous with the way we do locking and isn't worth it.
        }
    }
    
    

        

    @Override
    public <T> Table<T> getTable(Class<T> type){
        return this.getTable(type, type.getSimpleName());
    }
    
    @Override
    public <T> Table<T> getTable(Class<T> type, String name){
        
        TableMetadata table = getMetadata(type, name);
        
        TableBase ret = table.getTable(type);
        return (Table<T>)ret;
    }
    
    @Override
    public KeyValueTable getKeyValueTable(String name){
        TableMetadata table = getMetadata(null, name);
        
        ConvertingKeyValueTable ret = new ConvertingKeyValueTable(name);
        ret.setConversionService(conversionService);
        ret.setEngineProvider(table);
        ret.setLoadPojoMapperAction(new Action1<ConvertingKeyValueTable>(){
            @Override
            public void apply(ConvertingKeyValueTable val){
                try {
                    loadPojoConverter();
                    val.setConversionService(conversionService);
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                    throw new UnsupportedOperationException("No conversion available between your type and Element", ex);
                }
            }
        });
        
        return ret;        
    }
    
    /**
     * Gets the internal EngineBase that has been spun up to manage the given table.
     * This internal engine is the low-level manager of the database on disk.
     * <p/>
     * Please use {@link #getTable(java.lang.Class) } instead.  It is preferable
     * to interact with the data via the {@link Table} interface.
     * @param name The name of the table for which an engine is desired.
     * @return A running EngineBase which manages the table.
     */
    public EngineBase getEngine(String name){
        return getMetadata(null, name).provideEngine();
    }
    
    private TableMetadata getMetadata(Class<?> type, String name){
        if(this.state.get() == DatabaseState.Uninitialized){
            throw new IllegalStateException("Database has not been initialized");
        }
        if(this.state.get() == DatabaseState.ShuttingDown){
            throw new IllegalStateException("Database is shutting down");
        }
        
        if(name == null || name.startsWith("xflat_")){
            throw new IllegalArgumentException("Table name cannot be null or start with 'xflat_': " + name);
        }
        if(type != null && !Element.class.equals(type)){
            if(!this.getConversionService().canConvert(type, Element.class) ||
                    !this.getConversionService().canConvert(Element.class, type)){

                try {
                    //try to load the pojo converter
                    loadPojoConverter();

                } catch (Exception ex) {
                    throw new UnsupportedOperationException("No conversion available between " +
                            type + " and " + Element.class, ex);
                }

                //check again
                if(!this.getConversionService().canConvert(type, Element.class) ||
                    !this.getConversionService().canConvert(Element.class, type)){
                    throw new UnsupportedOperationException("No conversion available between " +
                            type + " and " + Element.class);
                }
            }
        }
        
        //see if we have a cached engine already
        TableMetadata table = this.tables.get(name);
        if(table == null){
            TableConfig tblConfig = this.tableConfigs.get(name);
            Class<?> idType = String.class;
            if(type != null && !Element.class.equals(type)){
                IdAccessor accessor = IdAccessor.forClass(type);
                if(accessor != null && accessor.hasId()){
                    idType = accessor.getIdType();
                }
            }
            
            table = this.metadataFactory.makeTableMetadata(name, new File(getDirectory(), name + ".xml"), tblConfig, idType);
            
            TableMetadata weWereSlow = this.tables.putIfAbsent(name, table);
            if(weWereSlow != null){
                //this thread was slower than another thread, use the other thread's table metadata
                table = weWereSlow;
            }
            if(log.isTraceEnabled())
                log.trace(String.format("Metadata loaded for table %s", table.getName()));
        }
        
        return table;
    }
    
    
    private void loadPojoConverter() throws ClassNotFoundException, InstantiationException, IllegalAccessException{
        if(!pojoConverterLoaded.compareAndSet(false, true)){
            return;
        }
        if(this.pojoConverter != null){
            //the user set a pojo converter via the Set method.
            return;
        }
        
        Class<?> converter;
        
        converter = this.getClass().getClassLoader().loadClass(this.config.getPojoConverterClass());
        
        if(converter == null){
            log.warn(String.format("Unable to locate Pojo Converter %s", this.config.getPojoConverterClass()));
            return;
        }
        
        if(log.isTraceEnabled())
            log.trace(String.format("Activating Pojo Converter %s", converter.getName()));
        
        PojoConverter instance = (PojoConverter)converter.newInstance();
        
        this.setPojoConverter(instance);
    }
    
    /**
     * Represents the various states of the Database.
     */
    public enum DatabaseState{
        /**
         * The state of a database before the {@link XFlatDatabase#initialize() } method is
         * called.
         */
        Uninitialized,
        /**
         * The state when the database is initializing, including potentially
         * recovering from an unexpected shutdown.
         */
        Initializing,
        /**
         * The state of a database that is running and capable of responding
         * to requests.
         */
        Running,
        /**
         * The state of a database that is either in the process of or has already
         * shut down.  Requests on this database will throw.
         */
        ShuttingDown,
        
        
    }
    
}
