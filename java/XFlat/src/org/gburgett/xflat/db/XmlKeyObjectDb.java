/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import org.gburgett.xflat.query.XpathQuery;
import org.jdom.Element;
import org.jdom.xpath.XPath;

/**
 * Represents a simple DB-like structure that stores key-value pairs of
 * string keys and Xml Elements.  Can be queried using Hamcrest matchers.
 * @author Gordon
 */
public interface XmlKeyObjectDb {

    //<editor-fold desc="Read" >
    /**
     * Finds one element in the database by key
     * @param key The key to find in the DB
     * @return The first element matching the key
     */
    public Element findOneByKey(String key);

    /**
     *
     * @param key
     * @return
     */
    public Iterable<Element> findByKey(String key);

    /**
     * Finds the first element in the db matching the given query.
     * @param query The query that matches database rows.
     * @return The first matching element
     */
    public Element findOne(XpathQuery query);

    /**
     * Finds all elements matching the given query in the db.
     * @param query The query that matches database rows.
     * @return All matching elements.
     */
    public Iterable<Element> find(XpathQuery query);

    //</editor-fold>

    //<editor-fold desc="create">
    /**
     * Inserts an element into the DB with the given key.
     * The element will become a row, tagged with the key.
     * @param key The key to tag the element
     * @param value The element to store in the DB.
     * @return
     */
    public boolean insert(String key, Element value);

    //</editor-fold>

    //<editor-fold desc="Update">

    /**
     * Replaces the first element with the given key with the new element.
     * @param key The key to search for
     * @param newValue The new value that replaces the first element found with the key.
     * @return The old element that used to have that key.
     */
    public Element replaceFirst(String key, Element newValue);

    /**
     * Replaces the first element matching the query with the new element.
     * @param query The query that matches elements.
     * @param newValue The new value that replaces the first element found matching
     * the query.
     * @return The old element that used to match that query.
     */
    public Element replaceFirst(XpathQuery query, Element newValue);

    /**
     * Replaces the existing value for the key or inserts a new value for that key.
     * @param key The key to search for
     * @param newValue The new value that replaces the first element found with the key.
     * @return The old element that used to have that key, or null if no element existed.
     */
    public Element upsert(String key, Element newValue);

    /**
     * Replaces the first element matching the query with the new element, or inserts the new element.
     * @param query The query that matches elements.
     * @param newValue The new value that replaces the first element found matching
     * the query.
     * @return The old element that used to match that query.
     */
    public Element upsert(XpathQuery key, Element newValue);

    //</editor-fold>

    //<editor-fold desc="delete">

    /**
     * Deletes the first element found with the given key.
     * @param key The key to match.
     * @return The element that was deleted.
     */
    public Element deleteFirst(String key);

    /**
     * Deletes the first element matching the query.
     * @param query The query to match.
     * @return The element that was deleted.
     */
    public Element deleteFirst(XpathQuery query);

    /**
     * Deletes all the elements matching that key out of the DB.
     * @param key The key to delete.
     * @return All the deleted elements.
     */
    public Iterable<Element> deleteAll(String key);

    /**
     * Delete all the elements matching the selector out of the DB.
     * @param selector The query matching elements to delete.
     * @return All the deleted elements.
     */
    public Iterable<Element> deleteAll(XpathQuery query);

    //</editor-fold>

}
