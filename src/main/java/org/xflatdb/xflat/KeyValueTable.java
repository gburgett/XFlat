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

import java.util.List;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;

/**
 * This interface represents a "KeyValue" view to an XFlat table.  The KeyValue
 * view allows storing arbitrary convertible objects by key in the database.
 * @author Gordon
 */
public interface KeyValueTable {
    
    //CREATE
    /**
     * Inserts a key value pair as a row in the table.
     * @param key The key to which the value is associated.
     * @param row The value to insert as XML.  Cannot be null.
     * @throws DuplicateKeyException if a row with the given key already exists.
     */
    public <T> void add(String key, T row)
            throws DuplicateKeyException;
    
    /**
     * Puts a value with the given key in the database.  If the value already
     * exists, it is overwritten.
     * @param <T>
     * @param key The key to which the value is associated.
     * @param row The new value for the row.  Cannot be null.
     */
    public <T> void set(String key, T row);
    
    /**
     * Puts a value with the given key in the database.  If the value already
     * exists, it is overwritten and the old value is returned.
     * @param <T>
     * @param key The key to which the value is associated.
     * @param row The new value for the row.  Cannot be null.
     * @return The old value for the row, or null if it did not previously exist.
     */
    public <T> T put(String key, T row);
    
    //READ
    /**
     * Finds one value by key
     * @param key The key to which the value is associated.
     * @param clazz The type as which the value should be deserialized.
     * @return The row value, or null if the row does not exist.
     */
    public <T> T get(String key, Class<T> clazz);
    
    /**
     * Finds the first value matching the Xpath query.
     * @param query The query to match.
     * @param clazz The type as which the value should be deserialized. 
     * @return the value of the matched row, or null if no row was matched.
     */
    public <T> T findOne(XPathQuery query, Class<T> clazz);
    
    /**
     * Gets a cursor over all the values matching the Xpath query.
     * @param query The query to match.
     * @param clazz The type as which the value should be deserialized.
     * @return A cursor over each matching row.
     */
    public <T> Cursor<T> find(XPathQuery query, Class<T> clazz);
    
    /**
     * Gets a list of all the values matching the Xpath query.
     * This is the same as {@link #find(org.xflatdb.xflat.query.XPathQuery, Class) }
     * but without the hassle of a cursor.
     * @param query The query to match.
     * @param clazz The type as which the value should be deserialized.
     * @return A list of all the matching values.
     */
    public <T> List<T> findAll(XPathQuery query, Class<T> clazz);
    
    //UPDATE
    /**
     * Replaces a value with the new value by ID.  This is the same as "Save"
     * in some other document databases.
     * @param key The key to which the value is associated.
     * @param newValue The new value to replace the old value.
     */
    public <T> void replace(String key, T newValue)
            throws KeyNotFoundException;
 
    /**
     * Applies an update to the data in a given row.
     * @param key The key to which the value is associated.
     * @param update The update to apply.
     * @return true if the update actually applied, false if the row was found
     * but the update did not select an existing document element.
     * @throws KeyNotFoundException if the row does not exist.
     */
    public boolean update(String key, XPathUpdate update)
            throws KeyNotFoundException;
    
    //DELETE
    /**
     * Deletes the row associated to the given key.
     * @param key The key to which the value is associated.
     */
    public void delete(String key)
            throws KeyNotFoundException;
    
    /**
     * Deletes all rows matching the given query.
     * @param query The query selecting elements to delete.
     * @return the number of rows that were deleted.
     */
    public int deleteAll(XPathQuery query);
}
