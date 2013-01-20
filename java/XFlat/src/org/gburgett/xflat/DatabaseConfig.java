/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import org.gburgett.xflat.TableConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.gburgett.xflat.convert.PojoConverter;
import org.gburgett.xflat.db.IdGenerator;
import org.gburgett.xflat.db.IdGenerator;
import org.gburgett.xflat.db.IntegerIdGenerator;
import org.gburgett.xflat.db.IntegerIdGenerator;
import org.gburgett.xflat.db.TimestampIdGenerator;
import org.gburgett.xflat.db.TimestampIdGenerator;
import org.gburgett.xflat.db.UuidIdGenerator;
import org.gburgett.xflat.db.UuidIdGenerator;

/**
 *
 * @author gordon
 */
public class DatabaseConfig {
    
    private List<Class<? extends IdGenerator>> idGeneratorStrategy;
    
    private int threadCount;
    
    private String pojoConverterClass;
    
    private TableConfig defaultTableConfig;
    
    private DatabaseConfig(){
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
     * Gets the ID generator strategy used by this Database.  
     * @return An unmodifiable list of the ID generators in the strategy.
     */
    public List<Class<? extends IdGenerator>> getIdGeneratorStrategy(){
        if(idGeneratorStrategy == null){
            return Collections.EMPTY_LIST;
        }
        return this.idGeneratorStrategy;
    }
    /**
     * Sets the ID generator strategy used by this Database.  
     * ID generators are selected on a per-table basis by the {@link Database} 
     * based on an ID generation strategy.  The strategy selects the first IdGenerator
     * in the list that supports the ID property's type.
     * @param strategy The strategy to use for this database.
     * @return A new instance of the DatabaseConfig using this strategy.
     */
    public DatabaseConfig setIdGeneratorStrategy(List<Class<? extends IdGenerator>> strategy){
        if(strategy.size() <= 0){
            throw new IllegalArgumentException("Id Generator strategy must contain at least " +
                    "one ID generator");
        }
        
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.idGeneratorStrategy = Collections.unmodifiableList(new ArrayList<>(strategy));
        return ret;
    }
    
    /**
     * Gets the number of threads that this Database can use.
     * @return the size of the database's thread pool
     * @see #setThreadCount(int) 
     */
    public int getThreadCount(){
        return this.threadCount;
    }
    /**
     * Sets the number of threads that this Database can use.
     * The database uses an ExecutorService to manage scheduled and recurring tasks.
     * This sets the size of its thread pool.
     * @param threadCount The number of threads in the database's thread pool.
     * @return A new instance with the ThreadCount property set.
     */
    public DatabaseConfig setThreadCount(int threadCount){
        DatabaseConfig ret = new DatabaseConfig(this);
        ret.threadCount = threadCount;
        return ret;
    }
    /**
     * Gets the binary name of the class used by the database to convert 
     * POJOs for the database.
     * @return 
     */
    public String getPojoConverterClass(){
        return this.pojoConverterClass;
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
    
    /**
     * The default configuration used by the Database.
     */
    public static DatabaseConfig Default = new DatabaseConfig()
            .setThreadCount(4)
            .setPojoConverterClass("org.gburgett.xflat.convert.converters.JAXBPojoConverter")
            .setDefaultTableConfig(TableConfig.Default)
            .setIdGeneratorStrategy(Arrays.asList(
                UuidIdGenerator.class,
                TimestampIdGenerator.class,
                IntegerIdGenerator.class));
}
