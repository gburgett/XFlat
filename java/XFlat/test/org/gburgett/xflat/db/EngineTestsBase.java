/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.DuplicateKeyException;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.converters.JDOMConverters;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.gburgett.xflat.db.EngineBase.EngineState;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author gordon
 */
public abstract class EngineTestsBase<TEngine extends EngineBase> {
    
    protected ScheduledExecutorService executorService;
    
    protected ConversionService conversionService;
    
    protected AtomicBoolean spinDownInvoked = new AtomicBoolean(false);
    
    protected File workspace = new File("./engine tests");
    
    protected org.jdom2.xpath.XPathFactory xpath = org.jdom2.xpath.XPathFactory.instance();
    
    private TEngine instance;
    
    @Before
    public void setUp() throws IOException {
        if(!workspace.exists()){
            workspace.mkdirs();
        }
        
        executorService = new ScheduledThreadPoolExecutor(2);
        conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
    }
    
    
    @After
    public void tearDown() throws InterruptedException, TimeoutException{
        spinDown(instance);
        
        verifySpinDownComplete();
    
        deleteDir(this.workspace);
    }
    
    private void deleteDir(File directory){
        for(File f : directory.listFiles()){
            if(f.isDirectory()){
                deleteDir(f);
                f.delete();
            }
            else{
                f.delete();
            }
        }
    }
    
    protected void spinDown(EngineBase instance) throws InterruptedException, TimeoutException {
        this.spinDown(instance, true);
    }
    
    protected synchronized void spinDown(final EngineBase instance, boolean synchronous) throws InterruptedException, TimeoutException{
        
        if(spinDownInvoked.get()){
            return;
        }
        
        final AtomicBoolean didTimeOut = new AtomicBoolean();
        final Object notifyMe = new Object();
        
        spinDownInvoked.set(true);
        instance.spinDown(new EngineBase.SpinDownEventHandler(){
            @Override
            public void spinDownComplete(EngineBase.SpinDownEvent event) {
                synchronized(notifyMe){
                    notifyMe.notifyAll();
                }
            }
        });
        
        if(synchronous){
            executorService.schedule(new Runnable(){
                @Override
                public void run() {
                    synchronized(notifyMe){
                        if(instance.getState() == EngineBase.EngineState.SpunDown){
                            return;
                        }

                        instance.forceSpinDown();
                        didTimeOut.set(true);
                        notifyMe.notifyAll();
                    }
                }
            }, 200, TimeUnit.MILLISECONDS);
            
            while(instance.getState() != EngineState.SpunDown){
                synchronized(notifyMe){
                    notifyMe.wait();
                }
            }
            
            if(didTimeOut.get())
                throw new TimeoutException("spin down timed out");
        }
    }
    
    protected void verifySpinDownComplete() throws InterruptedException{
        if(instance.getState() != EngineState.SpunDown){
            //give it a little leeway
            Thread.sleep(500);
        }
        
        assertEquals("Should have spun down", EngineState.SpunDown, instance.getState());
    }
    
    private TEngine setupEngine(){
        TEngine instance = this.createInstance();
        instance.setExecutorService(executorService);
        instance.setConversionService(conversionService);
        return instance;
    }
    
    protected void spinUp(EngineBase instance){
        
        spinDownInvoked.set(false);
        instance.spinUp();
        instance.beginOperations();
    }
    
    /**
     * Gets the engine instance to test
     * @return 
     */
    protected abstract TEngine createInstance();
    
    /**
     * Prepares the underlying XML file by writing the given contents to it.
     * @param contents The contents which should be stored in the underlying XML file.
     */
    protected abstract void prepFileContents(Document contents) throws IOException;
    
    /**
     * Gets the contents of the written file as a Document.  This will be invoked
     * after spin down to get the final table contents.
     * @return 
     */
    protected abstract Document getFileContents() throws IOException, JDOMException;
    
    
    
    private Document makeDocument(String tableName, Element... rowData){
        Document ret = new Document();
        Element root = new Element("table", Database.xFlatNs)
                .setAttribute("name", tableName, Database.xFlatNs);
        ret.setRootElement(root);   
        
        int i = 0;
        for(Element e : rowData){
            root.addContent(new Element("row", Database.xFlatNs)
                    .setAttribute("id", Integer.toString(i++), Database.xFlatNs)
                    .setContent(e));
        }
        
        return ret;
    }
    
    private String getId(Element row){
        return row.getAttributeValue("id", Database.xFlatNs);
    }
    
    private Element setId(Element row, String id){
        row.setAttribute("id", id, Database.xFlatNs);
        return row;
    }
    
    private Element findId(Iterable<Element> rows, String id){
        for(Element r : rows){
            if(id.equals(getId(r)))
                return r;
        }
        
        return null;
    }
    
    @Test
    public void testInsert_NoValuesYet_Inserts() throws Exception {
        System.out.println("testInsert_NoValuesYet_Inserts");
        
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        instance.insertRow("test id", rowData);
        
        //ASSERT
        Element fromEngine = instance.readRow("test id");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        
        spinDown(instance);
        
        Document doc = getFileContents();
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        Element data = children.get(0).getChild("data");
        assertNotNull("row should have the data", data);
        assertEquals("row should have the data", "some text data", data.getText());
    }//end testInsert_NoValuesYet_Inserts
    
    @Test
    public void testInsert_HasValues_Inserts() throws Exception {
        System.out.println("testInsert_HasValues_Inserts");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        instance.insertRow("test id", rowData);
        
        
        Element fromEngine = instance.readRow("test id");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        
        spinDown(instance);
        
        //ASSERT
        Document doc = getFileContents();
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Should have 2 rows", 2, children.size());
        
        Element data = findId(children, "test id");
        assertNotNull("doc should have the row", data);
        assertEquals("row should have the data", "some text data", data.getChild("data").getText());
    }//end testInsert_HasValues_Inserts
    
    @Test
    public void testInsert_AlreadyHasId_ThrowsDuplicateKeyException() throws Exception {
        System.out.println("testInsert_AlreadyHasId_ThrowsDuplicateKeyException");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            instance.insertRow("0", rowData);
        } catch (DuplicateKeyException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown DuplicateKeyException", didThrow);
        
        
        spinDown(instance);
        
        //ASSERT
        Document doc = getFileContents();
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        
        Element data = findId(children, "0");
        assertNotNull("doc should have the row", data);
        assertEquals("row should have the original data", "other text data", data.getChild("data").getText());
    }//end testInsert_AlreadyHasId_ThrowsDuplicateKeyException
    
    
    @Test
    public void testReadRow_NoRowExists_ReturnsNull() throws Exception {
        System.out.println("testReadRow_NoRowExists_ReturnsNull");
        
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        
        //ACT
        Element data = instance.readRow("72");
        
        spinDown(instance);
        
        //ASSERT
        assertNull("Should not read data that does not exist", data);
        
    }//end testReadRow_NoRowExists_ReturnsNull
    
    @Test
    public void testReadRow_ReadsWrongRow_ReturnsNull() throws Exception {
        System.out.println("testReadRow_ReadsWrongRow_ReturnsNull");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        
        //ACT
        Element data = instance.readRow("72");
        
        spinDown(instance);
        
        //ASSERT
        assertNull("Should not read data that does not exist", data);
        
    }//end testReadRow_ReadsWrongRow_ReturnsNull
    
    @Test
    public void testReadRow_ReadsCorrectRow_ReturnsData() throws Exception {
        System.out.println("testReadRow_ReadsCorrectRow_ReturnsData");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("some text data"),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        
        //ACT
        Element data = instance.readRow("1");
        
        spinDown(instance);
        
        //ASSERT
        assertNotNull("Should have read some data", data);
        assertEquals("Should have read correct data", "other text data", 
                data.getText());
    }//end testReadRow_ReadsCorrectRow_ReturnsData
    
    @Test
    public void testQueryTable_NoData_NoResults() throws Exception {
        System.out.println("testQueryTable_NoData_NoResults");
        
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        
        //ACT
        XpathQuery query = XpathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(instance, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertFalse("Cursor should not have any results", cursor.iterator().hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete();
        
    }//end testQueryTable_NoData_NoResults
    
    @Test
    public void testQueryTable_WrongData_NoResults() throws Exception {
        System.out.println("testQueryTable_WrongData_NoResults");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        XpathQuery query = XpathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(instance, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertFalse("Cursor should not have any results", cursor.iterator().hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete();
        
    }//end testQueryTable_WrongData_NoResults
    
    @Test
    public void testQueryTable_OneMatch_OneResult() throws Exception {
        System.out.println("testQueryTable_OneMatch_OneResult");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("17")
                        ),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        XpathQuery query = XpathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(instance, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            Iterator<Element> it = cursor.iterator();
            assertTrue("Cursor should have a result", it.hasNext());
            assertEquals("Result should be correct", "17", it.next().getChild("fooInt").getText());
            
            assertFalse("Cursor should have only one result", it.hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete();
        
    }//end testQueryTable_OneMatch_OneResult
    
    @Test
    public void testQueryTable_MultipleMatches_MultipleResults() throws Exception {
        System.out.println("testQueryTable_MultipleMatches_MultipleResults");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("17")
                        ),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        XpathQuery query = XpathQuery.gte(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = instance.queryTable(query)){
        
            //cursors ought to still read during spin down,
            //but do it async cause we're using the cursor
            spinDown(instance, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertThat(cursor, Matchers.containsInAnyOrder(
                        hasChildText("fooInt", "17"),
                        hasChildText("fooInt", "18")
                    ));
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete();
        
    }//end testQueryTable_MultipleMatches_MultipleResults
    
    @Test
    public void testReplaceRow_NoData_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testReplaceRow_NoData_ThrowsKeyNotFoundException");
                
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            instance.replaceRow("test id", rowData);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
        
        spinDown(instance);
        
        //ASSERT
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have no data", 0, children.size());
    }//end testReplaceRow_NoData_ThrowsKeyNotFoundException
    
    @Test
    public void testReplaceRow_RowExists_Replaced() throws Exception {
        System.out.println("testReplaceRow_RowExists_Replaced");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        instance.replaceRow("0", rowData);

        //ASSERT
        Element fromEngine = instance.readRow("0");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("data", "some text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testReplaceRow_RowExists_Replaced
    
    @Test
    public void testUpdate_NoElementWithId_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testUpdate_NoElementWithId_ThrowsKeyNotFoundException");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            instance.update("14", update);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have old data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpdate_NoElementWithId_ThrowsKeyNotFoundException
    
    @Test
    public void testUpdate_RowHasNoUpdateableField_NoUpdatePerformed() throws Exception {
        System.out.println("testUpdate_RowHasNoUpdateableField_NoUpdatePerformed");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("fourth"), "updated text");
        
        //ACT
        boolean result = instance.update("0", update);
        
        //ASSERT
        assertFalse("Should have reported unsuccessful update", result);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have old data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpdate_RowHasNoUpdateableField_NoUpdatePerformed
    
    @Test
    public void testUpdate_ElementHasId_UpdatesElement() throws Exception {
        System.out.println("testUpdate_ElementHasId_UpdatesElement");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        boolean result = instance.update("0", update);
        
        //ASSERT
        assertTrue("Should have reported successful update", result);
        
        Element fromEngine = instance.readRow("0");
        assertEquals("Should have updated in engine", "other", fromEngine.getName());
        assertEquals("Should have updated in engine", "updated text", fromEngine.getText());

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have updated data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "updated text"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpdate_ElementHasId_UpdatesElement
    
    @Test
    public void testUpdate_NoMatchingElements_NoUpdates() throws Exception {
        System.out.println("testUpdate_NoMatchingElements_NoUpdates");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "23")
                    .setText("third text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathQuery query = XpathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XpathUpdate update = XpathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        int result = instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 0 rows updated", 0, result);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have old data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpdate_NoMatchingElements_NoUpdates
    
    @Test
    public void testUpdate_MatchingRowHasNoUpdateableField_NoUpdatesPerformed() throws Exception {
        System.out.println("testUpdate_MatchingRowHasNoUpdateableField_NoUpdatesPerformed");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathQuery query = XpathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XpathUpdate update = XpathUpdate.set(xpath.compile("fourth"), "updated text");
        
        //ACT
        int result = instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 0 rows updated", 0, result);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have old data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpdate_MatchingRowHasNoUpdateableField_NoUpdatesPerformed
    
    @Test
    public void testUpdate_MatchingRowHasUpdateableField_FieldIsUpdated() throws Exception {
        System.out.println("testUpdate_MatchingRowHasUpdateableField_FieldIsUpdated");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathQuery query = XpathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XpathUpdate update = XpathUpdate.set(xpath.compile("third"), "updated text");
        
        //ACT
        int result = instance.update(query, update);
                
        //ASSERT
        assertEquals("Should report 1 row updated", 1, result);
        
        Element fromEngine = instance.readRow("1");
        assertEquals("Should have updated in engine", "third", fromEngine.getName());
        assertEquals("Should have updated in engine", "updated text", fromEngine.getText());

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have updated data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "updated text")
                ));
    }//end testUpdate_MatchingRowHasUpdateableField_FieldIsUpdated
    
    @Test
    public void testUpdate_MultipleMatchingUpdateableRows_MultipleUpdatesPerformed() throws Exception {
        System.out.println("testUpdate_MultipleMatchingUpdateableRows_MultipleUpdatesPerformed");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                new Element("other")
                    .setAttribute("fooInt", "17")
                    .setContent(new Element("data")
                        .setText("other text data")
                ),
                new Element("third")
                    .setAttribute("fooInt", "18")
                    .setContent(new Element("data")
                        .setText("third text data")
                ),
                new Element("fourth")
                    .setAttribute("fooInt", "15")
                    .setContent(new Element("data")
                        .setText("fourth text data")
                )
            );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        XpathQuery query = XpathQuery.gte(xpath.compile("*/@fooInt"), 17);
        XpathUpdate update = XpathUpdate.set(xpath.compile("*/data"), "updated text");
        
        //ACT
        int result = instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 2 rows updated", 2, result);
        
        Element fromEngine = instance.readRow("0");
        assertEquals("Should have updated in engine", "other", fromEngine.getName());
        assertThat("Should have updated in engine", fromEngine, hasChildText("data", "updated text"));
        
        fromEngine = instance.readRow("1");
        assertEquals("Should have updated in engine", "third", fromEngine.getName());
        assertThat("Should have updated in engine", fromEngine, hasChildText("data", "updated text"));
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 3, children.size());
        
        assertThat("Document should have updated data", children,
                Matchers.containsInAnyOrder(
                    hasChildThat("other", hasChildText("data", "updated text")),
                    hasChildThat("third", hasChildText("data", "updated text")),
                    hasChildThat("fourth", hasChildText("data", "fourth text data"))
                ));
    }//end testUpdate_MultipleMatchingUpdateableRows_MultipleUpdatesPerformed
    
    @Test
    public void testUpsertRow_RowDoesntExist_Inserts() throws Exception {
        System.out.println("testUpsertRow_RowDoesntExist_Inserts");
        
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean inserted = instance.upsertRow("test id", rowData);
                
        //ASSERT
        assertTrue("Should report inserted", inserted);
        
        Element fromEngine = instance.readRow("test id");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());
        
        spinDown(instance);
        
        Document doc = getFileContents();
        
        assertNotNull("File contents should exist", doc);
        
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        
        Element data = children.get(0).getChild("data");
        assertNotNull("row should have the data", data);
        assertEquals("row should have the data", "some text data", data.getText());
    }//end testUpsertRow_RowDoesntExist_Inserts
    
    @Test
    public void testUpsertRow_RowExists_Replaces() throws Exception {
        System.out.println("testUpsertRow_RowExists_Replaces");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean inserted = instance.upsertRow("0", rowData);
       
        //ASSERT
        assertFalse("Should report as updated", inserted);
        
        Element fromEngine = instance.readRow("0");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("data", "some text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testUpsertRow_RowExists_Replaces
    
    @Test
    public void testDeleteRow_RowDoesntExist_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testDeleteRow_RowDoesntExist_ThrowsKeyNotFoundException");
        
        instance = setupEngine();
        
        prepFileContents(null);
        spinUp(instance);
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            instance.deleteRow("test id");
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have no elements", 0, children.size());
        
    }//end testDeleteRow_RowDoesntExist_ThrowsKeyNotFoundException
    
    @Test
    public void testDeleteRow_RowExists_Deletes() throws Exception {
        System.out.println("testDeleteRow_RowExists_Deletes");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        instance.deleteRow("0");

        //ASSERT
        Element fromEngine = instance.readRow("0");
        assertNull("Row should be deleted in engine", fromEngine);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have one fewer element", 1, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.contains(
                    hasChildText("third", "third text data")
                ));
    }//end testDeleteRow_RowExists_Deletes
    
    @Test
    public void testDeleteAll_MatchesNone_NoneDeleted() throws Exception {
        System.out.println("testDeleteAll_MatchesNone_NoneDeleted");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        XpathQuery query = XpathQuery.eq(xpath.compile("*/@fooInt"), 17);
        
        int numDeleted = instance.deleteAll(query);

        //ASSERT
        assertEquals("Should have deleted none", 0, numDeleted);
        
        Element fromEngine = instance.readRow("0");
        assertEquals("Should have not changed in engine", "other", fromEngine.getName());
        assertEquals("Should have not changed in engine", "other text data", fromEngine.getText());
        
        fromEngine = instance.readRow("1");
        assertEquals("Should have not changed in engine", "third", fromEngine.getName());
        assertEquals("Should have not changed in engine", "third text data", fromEngine.getText());

        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have same number of elements", 2, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.containsInAnyOrder(
                    hasChildText("other", "other text data"),
                    hasChildText("third", "third text data")
                ));
    }//end testDeleteAll_MatchesNone_NoneDeleted
    
    @Test
    public void testDeleteAll_MatchesMultiple_MultipleDeleted() throws Exception {
        System.out.println("testDeleteAll_MatchesMultiple_MultipleDeleted");
        
        instance = setupEngine();
        
        Document inFile = makeDocument(instance.getTableName(),
            new Element("other")
                .setAttribute("fooInt", "17")
                .setText("other text data"),
            new Element("third")
                .setAttribute("fooInt", "17")
                .setText("third text data"),
            new Element("fourth")
                .setAttribute("fooInt", "18")
                .setText("fourth text data")
        );
        
        prepFileContents(inFile);
        spinUp(instance);
        
        //ACT
        XpathQuery query = XpathQuery.eq(xpath.compile("*/@fooInt"), 17);
        
        int numDeleted = instance.deleteAll(query);

        //ASSERT
        assertEquals("Should have deleted two", 2, numDeleted);
        
        Element fromEngine = instance.readRow("0");
        assertNull("Should have deleted row", fromEngine);
        fromEngine = instance.readRow("1");
        assertNull("Should have deleted row", fromEngine);
        fromEngine = instance.readRow("2");
        assertNotNull("Should not have deleted row", fromEngine);
        
        spinDown(instance);
        
        Document doc = getFileContents();
        List<Element> children = doc.getRootElement().getChildren("row", Database.xFlatNs);
        assertEquals("Document should have fewer elements", 1, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.contains(
                    hasChildText("fourth", "fourth text data")
                ));
    }//end testDeleteAll_MatchesMultiple_MultipleDeleted
    
    private Matcher<Element> hasChildThat(final String childName, final Matcher<Element> matcher){
        return new TypeSafeMatcher<Element>(){
            @Override
            protected boolean matchesSafely(Element item) {
                List<Element> children = item.getChildren(childName);
                for(Element e : children){
                    if(matcher.matches(e))
                        return true;
                }
                
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has child named ")
                        .appendText(childName)
                        .appendText(" that ")
                        .appendDescriptionOf(matcher);
            }
        };
    }
    
    private Matcher<Element> hasChildText(final String childName, final String text){
        return new TypeSafeMatcher<Element>(){
            @Override
            protected boolean matchesSafely(Element item) {
                Element child = item.getChild(childName);
                if(child == null)
                    return false;
                
                return text.equals(child.getText());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has a child named ")
                        .appendText(childName)
                        .appendText(" containing text ")
                        .appendText(text);
            }
            
        };
    }
}
