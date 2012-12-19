/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import java.util.List;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.jdom.Element;

/**
 * Represents a table in the database.  Converts from the POJO type to JDOM
 * {@link Element} objects before saving to an engine.
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
    public T findOne(XpathQuery query);
    
    /**
     * Gets a cursor over all the values matching the Xpath query.
     * @param query The query to match.
     * @return A cursor over each matching row.
     */
    public Cursor<T> find(XpathQuery query);
    
    /**
     * Gets a list of all the values matching the Xpath query.
     * This is the same as {@link #find(org.gburgett.xflat.query.XpathQuery) }
     * but without the hassle of a cursor.
     * @param query The query to match.
     * @return A list of all the matching values.
     */
    public List<T> findAll(XpathQuery query);
    
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
     */
    public void replaceOne(XpathQuery query, T newValue);
    
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
     */
    public void update(Object id, XpathUpdate update)
            throws KeyNotFoundException;
    
    /**
     * Applies an update to all data matching a given query.
     * @param query The query to match.
     * @param update The update to apply to each matching row.
     */
    public void update(XpathQuery query, XpathUpdate update);
    
    
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
     */
    public void deleteAll(XpathQuery query);
}
