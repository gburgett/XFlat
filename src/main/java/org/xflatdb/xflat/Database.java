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
package org.xflatdb.xflat;

import org.xflatdb.xflat.transaction.Transaction;
import org.xflatdb.xflat.transaction.TransactionManager;

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
     * Gets the named table as a KeyValueTable, which can be used to store data
     * as key-value pairs.
     * @param name The name of the table.
     * @return A table for manipulating rows of key-value data.
     */
    public KeyValueTable getKeyValueTable(String name);
    
    /**
     * Gets the database's {@link TransactionManager}.  The TransactionManager
     * allows opening transactions in the database.
     * @return The database's TransactionManager.
     */
    public TransactionManager getTransactionManager();
}
