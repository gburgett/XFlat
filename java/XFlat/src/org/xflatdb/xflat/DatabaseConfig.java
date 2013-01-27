/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.db.IdGenerator;
import org.xflatdb.xflat.db.IntegerIdGenerator;
import org.xflatdb.xflat.db.TimestampIdGenerator;
import org.xflatdb.xflat.db.UuidIdGenerator;
import org.xflatdb.xflat.db.XFlatDatabase;

/**
 * The Configuration for a new XFlat Database.  
 * <br/>
 * This Configuration must be
 * passed to the {@link XFlatDatabase#setConfig(org.gburgett.xflat.DatabaseConfig) }
 * method before initialization, or the default values will be used.
 * <p/>
 * This class is immutable, all set methods return new objects.
 * @author gordon
 */
public class DatabaseConfig {
    
    private List<Class<? extends IdGenerator>> idGeneratorStrategy;
    /**
     * Gets the ID generator strategy used by this Database. 
     * <p/>
     * ID generators are selected on a per-table basis by the {@link Database} 
     * based on an ID generation strategy.  The strategy selects the first IdGenerator
     * in the list that supports the ID property's type.
     * @return An unmodifiable list of the ID generators in the strategy.
     */
    public List<Class<? extends IdGenerator>> getIdGeneratorStrategy(){
        if(idGeneratorStrategy == null){
            return Collections.EMPTY_LIST;
        }
        return this.idGeneratorStrategy;
    }
    
    private int threadCount;
    
    /**
     * Gets the number of threads that this Database will spool up in its executor service.
     * <p/>
     * This is unused if an executor service is provided to the Database upon construction.
     * @return the size of the database's thread pool
     * @see #setThreadCount(int) 
     */
    public int getThreadCount(){
        return this.threadCount;
    }
    
    private String pojoConverterClass;
    /**
     * Gets the binary name of the class used by the database to convert 
     * POJOs for the database.
     * <p/>
     * The database will load this class using its {@link ClassLoader} in order
     * to convert pojos.
     * @return 
     */
    public String getPojoConverterClass(){
        return this.pojoConverterClass;
    }
    
    private TableConfig defaultTableConfig;
    
    /**
     * Creates a new DatabaseConfig with the default values.
     */
    public DatabaseConfig(){
        this.threadCount = 4;
        this.pojoConverterClass = "org.xflatdb.xflat.convert.converters.JAXBPojoConverter";
        this.defaultTableConfig = new TableConfig();
        this.idGeneratorStrategy = Arrays.asList(
                UuidIdGenerator.class,
                TimestampIdGenerator.class,
                IntegerIdGenerator.class);
    }
    
    private DatabaseConfig(DatabaseConfig other){
        this.threadCount = other.threadCount;
        this.pojoConverterClass = other.pojoConverterClass;
        //straight assignment is OK cause they are immutable.
        this.defaultTableConfig = other.defaultTableConfig;
        //straight assignment is OK cause they are unmodifiable lists of immutable objects.
        this.idGeneratorStrategy = other.idGeneratorStrategy;
    }
    

    /**
     * Sets the ID generator strategy used by this Database.
     * <p/>
     * ID generators are selected on a per-table basis by the {@link Database} 
     * based on an ID generation strategy.  The strategy selects the first IdGenerator
     * in the list that supports the ID property's type.
     * @param strategy The strategy to use for this database.
     * @return A new instance of the DatabaseConfig using this strategy.
     */
    public DatabaseConfig withIdGeneratorStrategy(List<Class<? extends IdGenerator>> strategy){
        if(strategy.size() <= 0){
            throw new IllegalArgumentException("Id Generator strategy must contain at least " +
                    "one ID generator");
        }
        
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.idGeneratorStrategy = Collections.unmodifiableList(new ArrayList<>(strategy));
        return ret;
    }
    

    /**
     * Sets the number of threads that this Database will spool up in its executor service.
     * <p/>
     * The database uses an ExecutorService to manage scheduled and recurring tasks.
     * This sets the size of its thread pool.<br/>
     * This is unused if an executor service is provided to the Database upon construction.
     * @param threadCount The number of threads in the database's thread pool.
     * @return A new instance with the ThreadCount property set.
     */
    public DatabaseConfig withThreadCount(int threadCount){
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.threadCount = threadCount;
        return ret;
    }

    /**
     * Sets the binary name of the class used by the database to convert 
     * POJOs for the database.  The class MUST be an implementation of
     * {@link PojoConverter}.  This class is only loaded when needed, in order
     * to avoid requiring JARs that are unnecessary.
     * 
     * The default value is a JAXB-based implementation.  If the PojoConverter
     * is never used, the JAXB context will never be loaded, and the JAXB jars
     * will not be necessary on the classpath.
     * @param className
     * @return A new instance with the pojoConverterClass property set.
     */
    public DatabaseConfig setPojoConverterClass(String className){
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.pojoConverterClass = className;
        return ret;
    }
    
    public TableConfig getDefaultTableConfig(){
        return this.defaultTableConfig;
    }
    /**
     * Sets the {@link TableConfig} used for new tables that have not been
     * manually configured.
     * If {@link Database#getTable(java.lang.String, java.lang.Class) } is called
     * for a table that has not been manually configured using {@link Database#configureTable(java.lang.String, org.gburgett.xflat.db.TableConfig) },
     * this configuration is used.
     * @param tableConfig The default table config to use.
     * @return A new instance with the defaultTableConfig property set.
     */
    public DatabaseConfig setDefaultTableConfig(TableConfig tableConfig){
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.defaultTableConfig = tableConfig;
        return ret;
    }
}
