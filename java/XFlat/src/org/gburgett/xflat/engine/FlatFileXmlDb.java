///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package org.gburgett.xflat.engine;
//
//import org.gburgett.xflat.db.XmlKeyObjectDb;
//import org.gburgett.xflat.query.XpathQuery;
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.hamcrest.StringDescription;
//import org.jdom.Document;
//import org.jdom.Element;
//import org.jdom.Attribute;
//import org.jdom.Namespace;
//
///**
// * Implements an {@link XmlKeyObjectDb} using a flat file.
// * @author Gordon
// */
//@Deprecated() //going to refactor this to implement Engine
//public class FlatFileXmlDb implements XmlKeyObjectDb {
//
//    public static Namespace dbNs = Namespace.getNamespace("db", "http://www.simplytrackable.com/xmldb");
//
//    protected File flatFile;
//
//
//    protected JdomDiskLoader documentLoader = new JdomDiskLoader();
//    /**
//     * injects a new Document Loader, if none is injected it uses the default
//     * instance.
//     * @param documentLoader
//     */
//    public void setDocumentLoader(JdomDiskLoader documentLoader){
//        this.documentLoader = documentLoader;
//    }
//
//    public FlatFileXmlDb(File flatFile){
//        this.flatFile = flatFile;
//
//    }
//
//    private Document cachedDoc = null;
//    /**
//     * Loads the document from disk, or creates a new one if the file doesnt exist.
//     */
//    protected Document getDoc(){
//        if(cachedDoc == null){
//            cachedDoc = documentLoader.loadQuietly(flatFile);
//            if(cachedDoc == null){
//                cachedDoc = new Document();
//                cachedDoc.setRootElement(new Element("XmlDb", dbNs));
//            }
//        }
//
//        return cachedDoc;
//    }
//
//    private Date nextFlush = new Date();
//    /**
//     * Flushes the cached document to disk.
//     * Does not flush if last flushed in the past 10 seconds and force is not on.
//     * @param force true to force a flush even if one has been performed in the last 10 secs.
//     * @throws IOException
//     */
//    public void flushDoc(boolean force) throws IOException {
//        if(cachedDoc == null)
//            return;
//
//        if(!force && new Date().before(nextFlush)){
//            //don't flush more than every 10 secs.
//            return;
//        }
//
//        documentLoader.saveTo(flatFile, cachedDoc);
//        //10s in the future
//        nextFlush = new Date(System.currentTimeMillis() + 10000);
//    }
//
//    /**
//     * Called by every CRUD operation to flush if necessary.
//     */
//    protected void internalFlush(){
//        try{
//            flushDoc(false);
//        } catch(IOException ex) {
//            Log log = LogFactory.getLog(getClass());
//            log.warn("Could not flush data to disk", ex);
//        }
//    }
//
//    private Map<String, List<Element>> keyIndex;
//    private void createIndex(){
//        keyIndex = new HashMap<>();
//        for(Element row : getRows()){
//            Attribute attr = row.getAttribute("key", dbNs);
//            if(attr == null){
//                continue;
//            }
//
//            List<Element> values = keyIndex.get(attr.getValue());
//            if(values == null){
//                values = new ArrayList<>();
//                keyIndex.put(attr.getValue(), values);
//            }
//
//            values.add(row);
//        }
//    }
//
//    /**
//     * Gets all the elements that have been indexed for this key.
//     * @param key The key which we are looking up.
//     * @return All the elements with this key.
//     */
//    protected List<Element> getIndexedElements(String key){
//        if(keyIndex == null){
//            createIndex();
//        }
//
//        List<Element> ret = keyIndex.get(key);
//
//        return ret;
//    }
//
//    /**
//     * Adds an element to the index.
//     */
//    protected void addToIndex(Element row){
//        Attribute attr = row.getAttribute("key", dbNs);
//        if(attr == null)
//            return;
//
//        if(keyIndex == null)
//            createIndex();
//
//        List<Element> indexed = keyIndex.get(attr.getValue());
//
//        if(indexed == null){
//            indexed = new ArrayList<>();
//            keyIndex.put(attr.getValue(), indexed);
//        }
//        if(!indexed.contains(row))
//            indexed.add(row);
//
//    }
//
//    /**
//     * Removes an element from the index.
//     */
//    protected void removeFromIndex(Element row){
//        Attribute attr = row.getAttribute("key", dbNs);
//        if(attr == null)
//            return;
//
//        if(keyIndex == null)
//            createIndex();
//
//        List<Element> indexed = keyIndex.get(attr.getValue());
//        if(indexed == null)
//            return;
//
//        indexed.remove(row);
//        if(indexed.isEmpty()){
//            keyIndex.remove(attr.getValue());
//        }
//    }
//
//    /**
//     * Iterates all the rows in the document.
//     * @return
//     */
//    protected Iterable<Element> getRows(){
//        Document doc = getDoc();
//        for(Object rowObj : doc.getRootElement().getChildren("row", dbNs)){
//            final Element row = (Element)rowObj;
//            lombok.Yield.yield(row);
//        }
//    }
//
//    /**
//     * Iterates all the rows matching the given key in the document.
//     * @param key
//     * @return
//     */
//    protected Iterable<Element> internalFind(final String key){
//        List<Element> indexed = getIndexedElements(key);
//        if(indexed != null){
//            for(Element row1 : indexed){
//                lombok.Yield.yield(row1);
//            }
//        }
//        else{
//            //table scan
//            for(Element row2 : getRows()){
//                Attribute attr = row2.getAttribute("key", dbNs);
//                if(attr == null || !key.equals(attr.getValue())){
//                    continue;
//                }
//
//                lombok.Yield.yield(row2);
//            }
//        }
//    }
//
//    /**
//     * Iterates over all the rows that match the given XpathQuery.
//     * @param query The query used to find rows.
//     * @return
//     */
//    protected Iterable<Element> internalFind(final XpathQuery query){
//        for(Element row : getRows()){
//            if(query.getRowMatcher().matches(row)){
//                lombok.Yield.yield(row);
//            }
//        }
//    }
//
//    /**
//     * Lombok first class function that clones elements.
//     * @see lombok.Functions.Function1
//     */
//    @lombok.Function
//    protected static Element doClone(Element el){
//        return (Element)el.clone();
//    }
//
//    @Override
//    public Element findOneByKey(String key) {
//        return findByKey(key).firstOrDefault();
//    }
//
//    @Override
//    public Iterable<Element> findByKey(String key) {
//        return internalFind(key).select(doClone());
//    }
//
//    @Override
//    public Element findOne(XpathQuery query) {
//        return find(query).firstOrDefault();
//    }
//
//    @Override
//    public Iterable<Element> find(XpathQuery query) {
//        return internalFind(query).select(doClone());
//    }
//
//    @Override
//    public boolean insert(String key, Element value) {
//        Document doc = getDoc();
//
//        value.setAttribute("key", key, dbNs);
//
//        doc.getRootElement().addContent(value);
//        this.addToIndex(value);
//
//        return true;
//    }
//
//    /**
//     * Replaces an old element in the DOM with the new element.
//     * Does not update indexes.
//     * @param old the old element to replace.
//     * @param newElement The new element value
//     */
//    protected void internalReplace(Element old, Element newElement){
//        Element parent = old.getParentElement();
//        int index = parent.indexOf(old);
//        if(index < 0){
//            //just append it
//            parent.addContent(newElement);
//        }
//        else{
//            parent.addContent(index, newElement);
//        }
//        parent.removeContent(old);
//    }
//
//    @Override
//    public Element replaceFirst(String key, Element newValue) {
//        Element replaced = internalFind(key).firstOrDefault();
//
//        if(replaced == null){
//            throw new IllegalStateException("No elements matching key " + key + " exist in the db");
//        }
//
//        newValue.setAttribute("key", key, dbNs);
//        internalReplace(replaced, newValue);
//
//        return replaced;
//    }
//
//    @Override
//    public Element replaceFirst(XpathQuery query, Element newValue) {
//        Element replaced = internalFind(query).firstOrDefault();
//
//        if(replaced == null){
//            StringDescription desc = new StringDescription();
//            query.getRowMatcher().describeTo(desc);
//            throw new IllegalStateException("No elements exist in the db matching " + desc.toString());
//        }
//
//        Attribute key = replaced.getAttribute("key", dbNs);
//        newValue.setAttribute((Attribute)key.clone());
//        internalReplace(replaced, newValue);
//
//        return replaced;
//    }
//
//    @Override
//    public Element upsert(String key, Element newValue) {
//        Element replaced = internalFind(key).firstOrDefault();
//
//        if(replaced == null){
//            //insert
//            getDoc().getRootElement().addContent(newValue);
//        }
//
//        newValue.setAttribute("key", key, dbNs);
//        internalReplace(replaced, newValue);
//
//        return replaced;
//    }
//
//    @Override
//    public Element upsert(XpathQuery query, Element newValue) {
//        Element replaced = internalFind(query).firstOrDefault();
//
//        if(replaced == null){
//            //insert
//            getDoc().getRootElement().addContent(newValue);
//        }
//
//        Attribute key = replaced.getAttribute("key", dbNs);
//        newValue.setAttribute((Attribute)key.clone());
//        internalReplace(replaced, newValue);
//
//        return replaced;
//    }
//
//    /**
//     * Deletes an element from the DOM.  Does not modify indexes.
//     * @param el
//     */
//    private void internalDelete(Element el){
//        el.getParent().removeContent(el);
//    }
//
//    @Override
//    public Element deleteFirst(String key) {
//        Element deleted = internalFind(key).firstOrDefault();
//
//        if(deleted == null){
//            throw new IllegalStateException("No element matching key " + key + " to delete");
//        }
//
//        internalDelete(deleted);
//        removeFromIndex(deleted);
//
//        return deleted;
//    }
//
//    @Override
//    public Element deleteFirst(XpathQuery query) {
//        Element deleted = internalFind(query).firstOrDefault();
//
//        if(deleted == null){
//            StringDescription desc = new StringDescription();
//            query.getRowMatcher().describeTo(desc);
//            throw new IllegalStateException("No element to delete matching " + desc.toString());
//        }
//
//        internalDelete(deleted);
//        removeFromIndex(deleted);
//
//        return deleted;
//    }
//
//    @Override
//    public Iterable<Element> deleteAll(String key) {
//        List<Element> ret = new ArrayList<>();
//        for(Element deleted : internalFind(key)){
//
//            internalDelete(deleted);
//            removeFromIndex(deleted);
//
//            ret.add(deleted);
//        }
//
//        return ret;
//    }
//
//    @Override
//    public Iterable<Element> deleteAll(XpathQuery query) {
//        List<Element> ret = new ArrayList<>();
//        for(Element deleted : internalFind(query)){
//
//            internalDelete(deleted);
//            removeFromIndex(deleted);
//
//            ret.add(deleted);
//        }
//
//        return ret;
//    }
//}
