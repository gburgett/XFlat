/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import org.gburgett.xflat.transaction.Transaction;
import org.gburgett.xflat.transaction.TransactionManager;

/**
 * An interface for a Database managing one or more Tables.<br/>
 * The Database allows getting and using Tables, and also provides access to
 * the {@link TransactionManager} which can open {@link Transaction Transactions}.
 * @author gordon
 */
public interface Database {
    
    /**
     * Gets a table that converts the data to the persistent class.
     * <p/>
     * The table will be named with the class' {@link Class#getSimpleName() simple name}.
     * @param <T> The generic class of the persistentType
     * @param persistentClass The persistent class, which the Database
     * can convert to and from {@link org.jdom2.Element}.
     * @return A table for manipulating rows of the persistent class.
     */
    public <T> Table<T> getTable(Class<T> persistentClass);
    
    /**
     * Gets the named table that converts the data to the given persistentClass.
     * <p/>
     * Multiple different classes of data can be stored in the same named Table provided
     * certain conditions are met.  
     * @param <T> The generic class of the persistentType
     * @param persistentClass The persistent class, which the Database
     * can convert to and from {@link org.jdom2.Element}.
     * @param name The name of the table.
     * @return A table for manipulating rows of the persistent class.
     */
    public <T> Table<T> getTable(Class<T> persistentClass, String name);
    
    /**
     * Gets the database's {@link TransactionManager}.  The TransactionManager
     * allows opening transactions in the database.
     * @return The database's TransactionManager.
     */
    public TransactionManager getTransactionManager();
}
