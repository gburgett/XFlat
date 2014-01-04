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

import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.jdom2.Element;

/**
 * The base interface for an XFlat table engine.
 * 
 * An engine is responsible for storing and retrieving JDom {@link Element} to disk,
 * maintaining indexes and caching.  It presents a CRUD interface for JDom elements.
 * 
 * The DB is configured to use different engine classes depending on multiple
 * factors, the most important of which is the size of the table.
 * @author gordon
 */
public interface Engine {

    public String getTableName();
    
    //CREATE
    /**
     * Inserts the given data element into a row.  The data element
     * is associated to a unique ID.
     * @param id The unique Id of the row to insert.
     * @param data The XML data to insert as a row.
     * @throws DuplicateKeyException if the row already exists.
     * for the given Id.
     */
    public void insertRow(String id, Element data)
            throws DuplicateKeyException;
    
    
    //READ
    /**
     * Reads a row from the database, if it exists.
     * @param id The Id of the row to read.
     * @return The data in the row, or null if no element exists with that Id.
     */
    public Element readRow(String id);
    
    /**
     * Returns a cursor over the rows in the table matching the given
     * query.
     * @param query The XPath query selecting rows in the database.
     * @return A cursor iterating over each data element in each row.
     */
    public Cursor<Element> queryTable(XPathQuery query);
    
    //UPDATE
    
    /**
     * Updates the given row by replacing the data with the new element.
     * @param id The unique ID of the row to update.
     * @param data The data that replaces the old data.
     * @throws KeyNotFoundException if the row does not exist.
     */
    public Element replaceRow(String id, Element data)
            throws KeyNotFoundException;
    
    /**
     * Applies an update to the one row specified by the ID.
     * @param id The ID of the row to update.
     * @param update The update to apply.
     * @return true if the update actually applied.
     */
    public boolean update(String id, XPathUpdate update)
            throws KeyNotFoundException;
    
    /**
     * Applies the given update to all rows matching the query.
     * @param query The query to match.
     * @param update The update to apply.
     * @return the number of rows that were updated
     */
    public int update(XPathQuery query, XPathUpdate update);
    
    /**
     * Updates or inserts the given row.  If the row exists,
     * the row is replaced with the new data.  If the row does not exist,
     * the row is inserted.
     * @param id The Id of the row to upsert.
     * @param data The data to replace or insert.
     * @return false if an existing row was updated, true if a row was inserted.
     */
    public boolean upsertRow(String id, Element data);
    
    //DELETE
    
    /**
     * Deletes the given row.  The row is removed from the table.
     * @param id The ID of the row to delete.
     */
    public void deleteRow(String id)
            throws KeyNotFoundException;
    
    /**
     * Deletes all rows matching the given query.
     * @param query The query that matches database rows.
     * @return the number of rows that were deleted.
     */
    public int deleteAll(XPathQuery query);
}
