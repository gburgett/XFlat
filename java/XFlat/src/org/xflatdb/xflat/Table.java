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
import org.jdom2.Element;

/**
 * Represents a table in the database.  A Table provides CRUD access to the underlying
 * XML data store, and converts to and from the generic type.  This is the main
 * interface to access data inside XFlat.
 * @author gordon
 */
public interface Table<T> {
    
    //CREATE
    /**
     * Inserts an object as a row in the database.
     * @param row The value to insert as XML.
     */
    public void insert(T row)
            throws DuplicateKeyException;
    
    //READ
    /**
     * Finds one value by ID
     * @param id The ID of the value to find
     * @return The row value, or null if the row does not exist.
     */
    public T find(Object id);
    
    /**
     * Finds the first value matching the Xpath query.
     * @param query The query to match.
     * @return the value of the matched row, or null if no row was matched.
     */
    public T findOne(XPathQuery query);
    
    /**
     * Gets a cursor over all the values matching the Xpath query.
     * @param query The query to match.
     * @return A cursor over each matching row.
     */
    public Cursor<T> find(XPathQuery query);
    
    /**
     * Gets a list of all the values matching the Xpath query.
     * This is the same as {@link #find(org.gburgett.xflat.query.XpathQuery) }
     * but without the hassle of a cursor.
     * @param query The query to match.
     * @return A list of all the matching values.
     */
    public List<T> findAll(XPathQuery query);
    
    //UPDATE
    /**
     * Replaces a value with the new value by ID.
     * @param newValue The new value to replace the old value.
     */
    public void replace(T newValue)
            throws KeyNotFoundException;
    
    /**
     * Replaces the first value matched by the query with the new value.
     * @param query The query to match.
     * @param newValue The new value to replace the row.
     * @returns true if the query matched any rows, false otherwise.  If false,
     * the new value was not inserted.
     */
    public boolean replaceOne(XPathQuery query, T newValue);
    
    /**
     * Updates or inserts the row by ID.
     * @param newValue The new value to replace or insert.
     * @returns false if an existing row was updated, true if a row was inserted.
     */
    public boolean upsert(T newValue);
    
    /**
     * Applies an update to the data in a given row.
     * @param id The ID of the row to match.
     * @param update The update to apply.
     * @return true if the update actually applied, false if the row was found
     * but the update did not select an existing document element.
     * @throws KeyNotFoundException if the row does not exist.
     */
    public boolean update(Object id, XPathUpdate update)
            throws KeyNotFoundException;
    
    /**
     * Applies an update to all data matching a given query.
     * @param query The query to match.
     * @param update The update to apply to each matching row.
     * @returns the number of rows that were updated.
     */
    public int update(XPathQuery query, XPathUpdate update);
    
    
    //DELETE
    /**
     * Deletes the row with the given ID.
     * @param id The ID of the row to delete.
     */
    public void delete(Object id)
            throws KeyNotFoundException;
    
    /**
     * Deletes all rows matching the given query.
     * @param query The query selecting elements to delete.
     * @returns the number of rows that were deleted.
     */
    public int deleteAll(XPathQuery query);
}
