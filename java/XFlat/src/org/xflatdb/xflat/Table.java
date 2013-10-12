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
     * @throws DuplicateKeyException if the row already exists
     * @throws XFlatException if some other error occurs
     */
    public void insert(T row)
            throws DuplicateKeyException, XFlatException;
    
    //READ
    /**
     * Finds one value by ID
     * @param id The ID of the value to find
     * @return The row value, or null if the row does not exist.
     * @throws XFlatException if some IO or other error occurs
     */
    public T find(Object id)
            throws XFlatException;
    
    /**
     * Finds the first value matching the Xpath query.
     * @param query The query to match.
     * @return the value of the matched row, or null if no row was matched.
     * @throws XFlatException if some IO or other error occurs
     */
    public T findOne(XPathQuery query)
            throws XFlatException;
    
    /**
     * Gets a cursor over all the values matching the Xpath query.
     * @param query The query to match.
     * @return A cursor over each matching row.
     * @throws XFlatException if some IO or other error occurs
     */
    public Cursor<T> find(XPathQuery query)
            throws XFlatException;
    
    /**
     * Gets a list of all the values matching the Xpath query.
     * This is the same as {@link #find(org.xflatdb.xflat.query.XPathQuery) }
     * but without the hassle of a cursor.
     * @param query The query to match.
     * @return A list of all the matching values.
     * @throws XFlatException if some IO or other error occurs
     */
    public List<T> findAll(XPathQuery query)
            throws XFlatException;
    
    //UPDATE
    /**
     * Replaces a value with the new value by ID.  This is the same as "Save"
     * in some other document databases.
     * @param newValue The new value to replace the old value.
     * @throws KeyNotFoundException if the ID of the newValue is null or 
     * no row with that ID exists in the database
     * @throws XFlatException if some IO or other error occurs
     */
    public void replace(T newValue)
            throws KeyNotFoundException, XFlatException;
    
    /**
     * Replaces the first value matched by the query with the new value.
     * This is similar to an Update operation with a whole-document replace.
     * @param query The query to match.
     * @param newValue The new value to replace the row.
     * @return true if the query matched any rows, false otherwise.  If false,
     * the new value was not inserted.
     * @throws XFlatException if some IO or other error occurs
     */
    public boolean replaceOne(XPathQuery query, T newValue)
            throws XFlatException;
    
    /**
     * Updates or inserts the row by ID.
     * @param newValue The new value to replace or insert.
     * @return false if an existing row was updated, true if a row was inserted.
     * @throws XFlatException if some IO or other error occurs
     */
    public boolean upsert(T newValue)
            throws XFlatException;
    
    /**
     * Applies an update to the data in a given row.
     * @param id The ID of the row to match.
     * @param update The update to apply.
     * @return true if the update actually applied, false if the row was found
     * but the update did not select an existing document element.
     * @throws KeyNotFoundException if the row does not exist.
     * @throws XFlatException if some IO or other error occurs
     */
    public boolean update(Object id, XPathUpdate update)
            throws KeyNotFoundException, XFlatException;
    
    /**
     * Applies an update to all data matching a given query.
     * @param query The query to match.
     * @param update The update to apply to each matching row.
     * @return the number of rows that were updated.
     * @throws XFlatException if some IO or other error occurs
     */
    public int update(XPathQuery query, XPathUpdate update)
            throws XFlatException;
    
    
    //DELETE
    /**
     * Deletes the row with the given ID.
     * @param id The ID of the row to delete.
     * @throws XFlatException if some IO or other error occurs
     */
    public void delete(Object id)
            throws KeyNotFoundException, XFlatException;
    
    /**
     * Deletes all rows matching the given query.
     * @param query The query selecting elements to delete.
     * @return the number of rows that were deleted.
     * @throws XFlatException if some IO or other error occurs
     */
    public int deleteAll(XPathQuery query)
            throws XFlatException;
}
