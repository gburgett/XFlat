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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.DuplicateKeyException;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.DefaultConversionService;
import org.xflatdb.xflat.convert.converters.JDOMConverters;
import org.xflatdb.xflat.convert.converters.StringConverters;
import org.xflatdb.xflat.db.EngineBase.SpinDownEventHandler;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.xflatdb.xflat.transaction.FakeThreadContextTransactionManager;
import org.xflatdb.xflat.transaction.Transaction;
import org.xflatdb.xflat.transaction.TransactionException;
import org.xflatdb.xflat.transaction.TransactionOptions;
import org.xflatdb.xflat.transaction.WriteConflictException;
import org.xflatdb.xflat.util.FakeDocumentFileWrapper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathFactory;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import test.Utils;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.xflatdb.xflat.transaction.TransactionScope;

/**
 *
 * @author gordon
 */
public abstract class EngineTestsBase<TEngine extends EngineBase> {
    
    protected static ConversionService conversionService;
    
    protected static org.jdom2.xpath.XPathFactory xpath = org.jdom2.xpath.XPathFactory.instance();
    
    protected static File workspace;
    
    @BeforeClass
    public static void setUpClass() {
        conversionService = new DefaultConversionService();        
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        
        workspace = new File("enginetests");
        if(!workspace.exists()){
            workspace.mkdirs();
        }
    }
    
    @AfterClass
    public static void tearDownClass(){
        Utils.deleteDir(workspace);
        workspace.delete();
    }
    
    
    
    /**
     * Gets a new test context for this test, creating the workspace
     * directory and setting up the engine instance.
     */
    protected TestContext getContext(){
        
        TestContext ctx = new TestContext();
        
        ctx.workspace = new File(workspace, Long.toHexString(ctx.id));
        if(!ctx.workspace.exists()){
            ctx.workspace.mkdirs();
        }
        else{
            Utils.deleteDir(ctx.workspace);
        }
        
        prepContext(ctx);
        
        ctx.instance = setupEngine(ctx);
        
        return ctx;
    }
    
    
    
    
    
    /**
     * Spins down the Engine, waiting for it to complete and throwing
     * an exception if it times out.
     * @param instance The engine to spin down
     * @throws InterruptedException
     * @throws TimeoutException 
     */
    protected void spinDown(TestContext ctx) throws InterruptedException, TimeoutException {
        this.spinDown(ctx, true);
    }
    
    
    /**
     * Spins down the Engine, optionally waiting for it to complete and throwing
     * an exception if it times out.
     * @param engine The engine to spin down
     * @param synchronous True to wait for the engine to spin down, with a timeout.
     * @throws InterruptedException
     * @throws TimeoutException 
     */
    protected void spinDown(final TestContext ctx, boolean synchronous) throws InterruptedException, TimeoutException{

        final EngineBase engine = ctx.instance;
        
        if(!ctx.spinDownInvoked.compareAndSet(false, true)){
            return;
        }
        
        if(engine.getState() != EngineState.Running){
            throw new UnsupportedOperationException("cannot spin down an engine in state " + engine.getState());
        }
        
        final AtomicReference<EngineState> didTimeOut = new AtomicReference(null);
        final AtomicBoolean didSpinDown = new AtomicBoolean(false);
        final Object notifyMe = new Object();
        
        engine.spinDown(new EngineBase.SpinDownEventHandler(){
            @Override
            public void spinDownComplete(EngineBase.SpinDownEvent event) {
                didSpinDown.get();
                synchronized(notifyMe){
                    notifyMe.notifyAll();
                }
            }
        });
        
        if(synchronous){
            ScheduledFuture<?> timeout = 
                ctx.executorService.schedule(new Runnable(){
                    @Override
                    public void run() {
                        if(engine.getState() == EngineState.SpunDown){
                            return;
                        }
                        
                        if(didSpinDown.get()){
                            return;
                        }

                        synchronized(notifyMe){
                            didTimeOut.set(engine.getState());
                            engine.forceSpinDown();
                            notifyMe.notifyAll();
                        }
                    }
                }, 500, TimeUnit.MILLISECONDS);
            
            while(engine.getState() != EngineState.SpunDown){
                synchronized(notifyMe){
                    notifyMe.wait();
                }
            }
            
            if(didTimeOut.get() != null){
                if(didSpinDown.get()){
                    fail("Spin down completed, but timeout was called with engine state " + didTimeOut.get());
                }
                throw new TimeoutException("spin down timed out with engine state " + didTimeOut.get());
            }
            else{
                if(timeout != null)
                    timeout.cancel(true);
            }
                
        }
    }
    
    protected void verifySpinDownComplete(TestContext ctx) throws InterruptedException{
        if(ctx.instance.getState() != EngineState.SpunDown){
            //give it a little leeway
            Thread.sleep(500);
        }
        
        assertEquals("Should have spun down", EngineState.SpunDown, ctx.instance.getState());
    }
    
    private TEngine setupEngine(TestContext ctx){
        TEngine instance = this.createInstance(ctx);
        instance.setExecutorService(ctx.executorService);
        instance.setConversionService(conversionService);
        instance.setTransactionManager(ctx.transactionManager);
        
        return instance;
    }
    
    protected void spinUp(TestContext ctx){
        
        ctx.spinDownInvoked.set(false);
        ctx.instance.spinUp();
        ctx.instance.beginOperations();
    }
    
    /**
     * Gets the engine instance to test.  This may be called more than once per test,
     * with the expectation that the instance will be for the same table after
     * spinning down the first instance.
     * @return 
     */
    protected abstract TEngine createInstance(TestContext ctx);
    
    /**
     * Prepares the test context by creating and storing dependencies
     * @param ctx 
     */
    protected abstract void prepContext(TestContext ctx);
    
    /**
     * Prepares the underlying XML file by writing the given contents to it.
     * @param contents The contents which should be stored in the underlying XML file.
     */
    protected abstract void prepFileContents(TestContext ctx, Document contents) throws IOException;
    
    /**
     * Gets the contents of the written file as a Document.  This will be invoked
     * after spin down to get the final table contents.
     * @return 
     */
    protected abstract Document getFileContents(TestContext ctx) throws IOException, JDOMException;
    
    
    
    protected String getId(Element row){
        return row.getAttributeValue("id", XFlatDatabase.xFlatNs);
    }
    
    protected Element setId(Element row, String id){
        row.setAttribute("id", id, XFlatDatabase.xFlatNs);
        return row;
    }
    
    protected Element findId(Iterable<Element> rows, String id){
        for(Element r : rows){
            if(id.equals(getId(r)))
                return r;
        }
        
        return null;
    }
    
    //<editor-fold desc="tests">
    
    //<editor-fold desc="transactionless">
    @Test
    public void testInsert_NoValuesYet_Inserts() throws Exception {
        System.out.println("testInsert_NoValuesYet_Inserts");
        
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        ctx.instance.insertRow("1", rowData);
        
        //ASSERT
        Element fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        Element data = children.get(0).getChild("data");
        assertNotNull("row should have the data", data);
        assertEquals("row should have the data", "some text data", data.getText());
        
    }//end testInsert_NoValuesYet_Inserts
    
    @Test
    public void testInsert_HasValues_Inserts() throws Exception {
        System.out.println("testInsert_HasValues_Inserts");
        
        TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        ctx.instance.insertRow("1", rowData);
        
        
        Element fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        
        spinDown(ctx);
        
        //ASSERT
        Document doc = getFileContents(ctx);
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Should have 2 rows", 2, children.size());
        
        Element data = findId(children, "1");
        assertNotNull("doc should have the row", data);
        assertEquals("row should have the data", "some text data", data.getChild("data").getText());
    }//end testInsert_HasValues_Inserts
    
    @Test
    public void testInsert_AlreadyHasId_ThrowsDuplicateKeyException() throws Exception {
        System.out.println("testInsert_AlreadyHasId_ThrowsDuplicateKeyException");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            ctx.instance.insertRow("0", rowData);
        } catch (DuplicateKeyException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown DuplicateKeyException", didThrow);
        
        
        spinDown(ctx);
        
        //ASSERT
        Document doc = getFileContents(ctx);
        
        assertNotNull("File contents should exist", doc);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        
        Element data = findId(children, "0");
        assertNotNull("doc should have the row", data);
        assertEquals("row should have the original data", "other text data", data.getChild("data").getText());
    }//end testInsert_AlreadyHasId_ThrowsDuplicateKeyException
    
    
    @Test
    public void testReadRow_NoRowExists_ReturnsNull() throws Exception {
        System.out.println("testReadRow_NoRowExists_ReturnsNull");
        
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        
        //ACT
        Element data = ctx.instance.readRow("72");
        
        spinDown(ctx);
        
        //ASSERT
        assertNull("Should not read data that does not exist", data);
        
    }//end testReadRow_NoRowExists_ReturnsNull
    
    @Test
    public void testReadRow_ReadsWrongRow_ReturnsNull() throws Exception {
        System.out.println("testReadRow_ReadsWrongRow_ReturnsNull");
        
        TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        
        //ACT
        Element data = ctx.instance.readRow("72");
        
        spinDown(ctx);
        
        //ASSERT
        assertNull("Should not read data that does not exist", data);
        
    }//end testReadRow_ReadsWrongRow_ReturnsNull
    
    @Test
    public void testReadRow_ReadsCorrectRow_ReturnsData() throws Exception {
        System.out.println("testReadRow_ReadsCorrectRow_ReturnsData");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("some text data"),
                new Element("data").setText("other text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        
        //ACT
        Element data = ctx.instance.readRow("1");
        
        spinDown(ctx);
        
        //ASSERT
        assertNotNull("Should have read some data", data);
        assertEquals("Should have read correct data", "other text data", 
                data.getText());
    }//end testReadRow_ReadsCorrectRow_ReturnsData
    
    @Test
    public void testQueryTable_NoData_NoResults() throws Exception {
        System.out.println("testQueryTable_NoData_NoResults");
        
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        
        //ACT
        XPathQuery query = XPathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = ctx.instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(ctx, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertFalse("Cursor should not have any results", cursor.iterator().hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete(ctx);
        
    }//end testQueryTable_NoData_NoResults
    
    @Test
    public void testQueryTable_WrongData_NoResults() throws Exception {
        System.out.println("testQueryTable_WrongData_NoResults");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        XPathQuery query = XPathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = ctx.instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(ctx, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertFalse("Cursor should not have any results", cursor.iterator().hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete(ctx);
        
    }//end testQueryTable_WrongData_NoResults
    
    @Test
    public void testQueryTable_OneMatch_OneResult() throws Exception {
        System.out.println("testQueryTable_OneMatch_OneResult");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("17")
                        ),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        XPathQuery query = XPathQuery.eq(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = ctx.instance.queryTable(query)){
        
            //cursors ought to still read during spin down
            //but do it async cause we're using the cursor
            spinDown(ctx, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            Iterator<Element> it = cursor.iterator();
            assertTrue("Cursor should have a result", it.hasNext());
            assertEquals("Result should be correct", "17", it.next().getChild("fooInt").getText());
            
            assertFalse("Cursor should have only one result", it.hasNext());
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete(ctx);
        
    }//end testQueryTable_OneMatch_OneResult
    
    @Test
    public void testQueryTable_MultipleMatches_MultipleResults() throws Exception {
        System.out.println("testQueryTable_MultipleMatches_MultipleResults");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("data").setText("other text data"),
                new Element("data").setContent(
                        new Element("fooInt").setText("17")
                        ),
                new Element("data").setContent(
                        new Element("fooInt").setText("18")
                        )
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        XPathQuery query = XPathQuery.gte(xpath.compile("data/fooInt"), 17);
        try(Cursor<Element> cursor = ctx.instance.queryTable(query)){
        
            //cursors ought to still read during spin down,
            //but do it async cause we're using the cursor
            spinDown(ctx, false);

            //ASSERT
            assertNotNull("Should have gotten a cursor", cursor);
            
            assertThat(cursor, Matchers.containsInAnyOrder(
                        hasChildText("fooInt", "17"),
                        hasChildText("fooInt", "18")
                    ));
        }
        
        //now verify that we spin down shortly after
        this.verifySpinDownComplete(ctx);
        
    }//end testQueryTable_MultipleMatches_MultipleResults
    
    @Test
    public void testReplaceRow_NoData_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testReplaceRow_NoData_ThrowsKeyNotFoundException");
                
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            ctx.instance.replaceRow("1", rowData);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
        
        spinDown(ctx);
        
        //ASSERT
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Document should have no data", 0, children.size());
    }//end testReplaceRow_NoData_ThrowsKeyNotFoundException
    
    @Test
    public void testReplaceRow_RowExists_Replaced() throws Exception {
        System.out.println("testReplaceRow_RowExists_Replaced");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        ctx.instance.replaceRow("0", rowData);

        //ASSERT
        Element fromEngine = ctx.instance.readRow("0");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathUpdate update = XPathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            ctx.instance.update("14", update);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathUpdate update = XPathUpdate.set(xpath.compile("fourth"), "updated text");
        
        //ACT
        boolean result = ctx.instance.update("0", update);
        
        //ASSERT
        assertFalse("Should have reported unsuccessful update", result);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathUpdate update = XPathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        boolean result = ctx.instance.update("0", update);
        
        //ASSERT
        assertTrue("Should have reported successful update", result);
        
        Element fromEngine = ctx.instance.readRow("0");
        assertEquals("Should have updated in engine", "other", fromEngine.getName());
        assertEquals("Should have updated in engine", "updated text", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "23")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("other"), "updated text");
        
        //ACT
        int result = ctx.instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 0 rows updated", 0, result);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("fourth"), "updated text");
        
        //ACT
        int result = ctx.instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 0 rows updated", 0, result);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("other").setText("other text data"),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("third"), "updated text");
        
        //ACT
        int result = ctx.instance.update(query, update);
                
        //ASSERT
        assertEquals("Should report 1 row updated", 1, result);
        
        Element fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have updated in engine", "third", fromEngine.getName());
        assertEquals("Should have updated in engine", "updated text", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
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
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.gte(xpath.compile("*/@fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("*/data"), "updated text");
        
        //ACT
        int result = ctx.instance.update(query, update);
        
        //ASSERT
        assertEquals("Should report 2 rows updated", 2, result);
        
        Element fromEngine = ctx.instance.readRow("0");
        assertEquals("Should have updated in engine", "other", fromEngine.getName());
        assertThat("Should have updated in engine", fromEngine, hasChildText("data", "updated text"));
        
        fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have updated in engine", "third", fromEngine.getName());
        assertThat("Should have updated in engine", fromEngine, hasChildText("data", "updated text"));
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean inserted = ctx.instance.upsertRow("1", rowData);
                
        //ASSERT
        assertTrue("Should report inserted", inserted);
        
        Element fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        
        assertNotNull("File contents should exist", doc);
        
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Should have 1 row", 1, children.size());
        
        Element data = children.get(0).getChild("data");
        assertNotNull("row should have the data", data);
        assertEquals("row should have the data", "some text data", data.getText());
    }//end testUpsertRow_RowDoesntExist_Inserts
    
    @Test
    public void testUpsertRow_RowExists_Replaces() throws Exception {
        System.out.println("testUpsertRow_RowExists_Replaces");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        Element rowData = new Element("data").setText("some text data");
        
        //ACT
        boolean inserted = ctx.instance.upsertRow("0", rowData);
       
        //ASSERT
        assertFalse("Should report as updated", inserted);
        
        Element fromEngine = ctx.instance.readRow("0");
        assertEquals("Should have updated in engine", "data", fromEngine.getName());
        assertEquals("Should have updated in engine", "some text data", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            ctx.instance.deleteRow("1");
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Document should have no elements", 0, children.size());
        
    }//end testDeleteRow_RowDoesntExist_ThrowsKeyNotFoundException
    
    @Test
    public void testDeleteRow_RowExists_Deletes() throws Exception {
        System.out.println("testDeleteRow_RowExists_Deletes");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        ctx.instance.deleteRow("0");

        //ASSERT
        Element fromEngine = ctx.instance.readRow("0");
        assertNull("Row should be deleted in engine", fromEngine);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        System.out.println(dumpDoc(doc));
        
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        
        
        assertEquals("Document should have one fewer element", 1, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.contains(
                    hasChildText("third", "third text data")
                ));
    }//end testDeleteRow_RowExists_Deletes
    
    @Test
    public void testDeleteAll_MatchesNone_NoneDeleted() throws Exception {
        System.out.println("testDeleteAll_MatchesNone_NoneDeleted");
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                    new Element("other").setText("other text data"),
                    new Element("third").setText("third text data")
                );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        
        int numDeleted = ctx.instance.deleteAll(query);

        //ASSERT
        assertEquals("Should have deleted none", 0, numDeleted);
        
        Element fromEngine = ctx.instance.readRow("0");
        assertEquals("Should have not changed in engine", "other", fromEngine.getName());
        assertEquals("Should have not changed in engine", "other text data", fromEngine.getText());
        
        fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have not changed in engine", "third", fromEngine.getName());
        assertEquals("Should have not changed in engine", "third text data", fromEngine.getText());

        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
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
        
        TestContext ctx = getContext();
        
        
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
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
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        
        int numDeleted = ctx.instance.deleteAll(query);

        //ASSERT
        assertEquals("Should have deleted two", 2, numDeleted);
        
        Element fromEngine = ctx.instance.readRow("0");
        assertNull("Should have deleted row", fromEngine);
        fromEngine = ctx.instance.readRow("1");
        assertNull("Should have deleted row", fromEngine);
        fromEngine = ctx.instance.readRow("2");
        assertNotNull("Should not have deleted row", fromEngine);
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        System.out.println(dumpDoc(doc));
        
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        assertEquals("Document should have fewer elements", 1, children.size());
        
        assertThat("Document should have correct data", children,
                Matchers.contains(
                    hasChildText("fourth", "fourth text data")
                ));
    }//end testDeleteAll_MatchesMultiple_MultipleDeleted
    
    //</editor-fold>
    
    //<editor-fold desc="transactional">
    
    @Test
    public void testUpdate_InTransaction_RevertRemovesData() throws Exception {
        System.out.println("testUpdate_InTransaction_RevertRemovesData");
        
        TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("third"), "updated text");
        
        //ACT
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            int result = ctx.instance.update(query, update);
            
            assertEquals("Should update in TX", 1, result);
            
            Element row = ctx.instance.readRow("0");
            assertEquals("Should update in TX", "updated text", row.getValue());
            
            tx.revert();
            
            row = ctx.instance.readRow("0");
            assertEquals("Should have reverted TX", "third text data", row.getValue());            
        }
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        
        assertEquals("Should have reverted data", "third text data", children.get(0).getChild("third").getText());
    }
    
    @Test
    public void testReplaceRow_InTransaction_CommitModifiesData() throws Exception {
        System.out.println("testReplaceRow_InTransaction_CommitModifiesData");
                TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        ctx.executorService = mock(ScheduledExecutorService.class);
        
        spinUp(ctx);
        
        Element fourth = new Element("fourth")
                    .setAttribute("fooInt", "17")
                    .setText("fourth text data");
        
        //ACT
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            ctx.instance.replaceRow("0", fourth);
            
            Element row = ctx.instance.readRow("0");
            assertEquals("Should update in TX", "fourth text data", row.getValue());
            
            tx.commit();
            
            row = ctx.instance.readRow("0");
            assertEquals("Should have not reverted TX", "fourth text data", row.getValue());            
        }
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        
        assertEquals("Should have committed data", "fourth text data", children.get(0).getChild("fourth").getText());
    }
    
    @Test
    public void testDeleteRow_InTransaction_RevertReturnsRow() throws Exception {
        System.out.println("testDeleteRow_InTransaction_RevertReturnsRow");
        
        TestContext ctx = getContext();
        
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
                new Element("third")
                    .setAttribute("fooInt", "17")
                    .setText("third text data")
            );
        
        prepFileContents(ctx, inFile);
        spinUp(ctx);
        
        //ACT
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            ctx.instance.deleteRow("0");
                        
            Element row = ctx.instance.readRow("0");
            assertNull("Should delete in TX", row);
            
            tx.revert();
            
            row = ctx.instance.readRow("0");
            assertNotNull("Should have reverted TX", row);
            assertEquals("Should have reverted TX", "third text data", row.getValue());
        }
        
        spinDown(ctx);
        
        Document doc = getFileContents(ctx);
        List<Element> children = doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
        
        assertEquals("Should have reverted data", "third text data", children.get(0).getChild("third").getText());
    }
    
    @Test
    public void testInsert_InTransaction_HasReadIsolation() throws Exception {
        System.out.println("testInsert_InTransaction_HasReadIsolation");
        
        TestContext ctx = getContext();
                
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        Element outsideTransaction;
        Element insideTransaction;
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            Element rowData = new Element("data").setText("some text data");

            //ACT
            ctx.instance.insertRow("1", rowData);
            
            //swap out the transaction
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(1L);
            outsideTransaction = ctx.instance.readRow("1");
            
            //swap the transaction back
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(0L);
            insideTransaction = ctx.instance.readRow("1");
            
            tx.commit();
        }
        
        assertNull("Outside the TX, should have no data", outsideTransaction);
        
        assertEquals("Inside the TX, should have the data", "data", insideTransaction.getName());
        assertEquals("Inside the TX, should have the data", "some text data", insideTransaction.getText());
        
        Element fromEngine = ctx.instance.readRow("1");
        assertEquals("Should have committed TX data", "data", fromEngine.getName());
        assertEquals("Should have committed TX data", "some text data", fromEngine.getText());

        
        spinDown(ctx);        
    }
    
    @Test
    public void testQuery_InTransaction_HasReadIsolation() throws Exception {
        System.out.println("testQuery_InTransaction_HasReadIsolation");
        
        TestContext ctx = getContext();
                
        Document inFile = Utils.makeDocument(ctx.instance.getTableName(),
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
        prepFileContents(ctx, inFile);
        
        spinUp(ctx);
        
        XPathQuery query = XPathQuery.eq(xpath.compile("*/@fooInt"), 17);
        
        
        List<Element> fromCursor = new ArrayList<>();
        try(TransactionScope tx = ctx.transactionManager.openTransaction(TransactionOptions.DEFAULT.withReadOnly(true))){
        
            Element rowData = new Element("data")
                    .setAttribute("fooInt", "17")
                    .setText("some text data");
            
            //now that we've opened the transaction, switch contexts and insert
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(1L);
            ctx.instance.insertRow("4", rowData);

            //ACT
            //switch back and query
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(0L);
            try(Cursor<Element> cursor = ctx.instance.queryTable(query)){
                Iterator<Element> it = cursor.iterator();
                
                fromCursor.add(it.next());
                
            
                //switch contexts and insert again
                ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(1L);
                ctx.instance.insertRow("5", new Element("fifth")
                    .setAttribute("fooInt", "17")
                    .setText("fifth text data"));
                
                
                ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(0L);
                while(it.hasNext()){
                    fromCursor.add(it.next());
                }
            }
        }
        
        assertEquals("Should have cursored over 2 items", 2, fromCursor.size());
        assertThat("should have correct 2 items", fromCursor, 
                Matchers.containsInAnyOrder(
                    Matchers.allOf(
                        isNamed("other"), hasText("other text data")
                    ),
                    Matchers.allOf(
                        isNamed("third"), hasText("third text data")
                    )));
        
        spinDown(ctx); 
    }
    
    @Test
    public void testConflictingWrite_SnapshotIsolation_ThrowsWriteConflictException() throws Exception {
        System.out.println("testConflictingWrite_SnapshotIsolation_ThrowsWriteConflictException");
        
        TestContext ctx = getContext();
                
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            Element rowData = new Element("data").setText("some text data");

            ctx.instance.insertRow("1", rowData);
            
            //swap out the transaction
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(1L);
            
            ctx.instance.insertRow("1", new Element("other").setText("other text data"));
            
            //swap the transaction back
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(0L);
            
            try{
                tx.commit();
                fail("should have thrown WriteConflictException");
            }catch(WriteConflictException ex){
                //expected
            }            
        }
    }
    
    @Test
    public void testConflictingWrite_SnapshotIsolation_TwoTransactions_ThrowsWriteConflictException() throws Exception {
        System.out.println("testConflictingWrite_SnapshotIsolation_ThrowsWriteConflictException");
        
        TestContext ctx = getContext();
                
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
        
            Element rowData = new Element("data").setText("some text data");

            ctx.instance.insertRow("1", rowData);
            
            //swap out the transaction
            ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(1L);
            try(TransactionScope tx2 = ctx.transactionManager.openTransaction()){
                //insert conflicting data
                ctx.instance.insertRow("1", new Element("other").setText("other text data"));

                //swap the transaction back
                ((FakeThreadContextTransactionManager)ctx.transactionManager).setContextId(0L);
                //should be OK
                tx.commit();

                try{
                    //ACT
                    tx2.commit();
                    fail("should have thrown WriteConflictException");
                }catch(WriteConflictException ex){
                    //expected
                }            

            }
        }
    }
 
    
    
    
    //</editor-fold>
    
    //<editor-fold desc="transaction durability">
    @Test
    public void testCommit_NoSimultaneousTasks_DataIsCommittedImmediately() throws Exception {
        System.out.println("testCommit_NoSimultaneousTasks_DataIsCommittedImmediately");
        
        TestContext ctx = getContext();
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        //shutdown any running tasks
        ctx.executorService.shutdown();
        ctx.executorService.awaitTermination(5, TimeUnit.SECONDS);
        
        //don't start up any new tasks, but don't throw an error if an attempt is made either.
        ctx.executorService = mock(ScheduledExecutorService.class);
        ctx.instance.setExecutorService(ctx.executorService);
        
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
            
            ctx.instance.insertRow("0", new Element("data").setText("some text data"));
            ctx.instance.insertRow("1", new Element("second").setText("second text data"));
            
            
            tx.commit();
        }
        
        //force it to immediately release all resources
        ctx.instance.forceSpinDown();
        
        //setup a new engine
        ctx.executorService = new ScheduledThreadPoolExecutor(2);
        ctx.instance = setupEngine(ctx);
        
        spinUp(ctx);
        
        Element row = ctx.instance.readRow("0");
        Element row2 = ctx.instance.readRow("1");
        
        spinDown(ctx);
        
        assertNotNull("Should have committed first row", row);
        assertEquals("Should have committed first row", "data", row.getName());
        assertEquals("Should have committed first row", "some text data", row.getText());
        
        
        assertNotNull("Should have committed second row", row2);
        assertEquals("Should have committed second row", "second", row2.getName());
        assertEquals("Should have committed second row", "second text data", row2.getText());
    }
    
    @Test
    public void testCommit_EngineHasCommittedData_RevertsCommittedData() throws Exception {
        System.out.println("testCommit_SecondEngineThrowsError_RecoversOnRestart");
        
        final TestContext ctx = getContext();
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        EngineBase eng2 = new EngineBase("bad_engine"){

            @Override
            public int hashCode(){
                //set up to return same hash code so that HashSet returns them in order.
                return ctx.instance.hashCode();
            }
            
            @Override
            public void commit(Transaction tx, TransactionOptions options){
                //set up the second engine to throw an exception on commit
            
                throw new RuntimeException("Test");
            }
            
            //<editor-fold desc="don't care">
            @Override
            protected boolean spinUp() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            protected boolean beginOperations() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            protected boolean spinDown(SpinDownEventHandler completionEventHandler) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            protected boolean forceSpinDown() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            protected boolean hasUncomittedData() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void insertRow(String id, Element data) throws DuplicateKeyException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Element readRow(String id) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Cursor<Element> queryTable(XPathQuery query) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void replaceRow(String id, Element data) throws KeyNotFoundException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public boolean update(String id, XPathUpdate update) throws KeyNotFoundException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public int update(XPathQuery query, XPathUpdate update) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public boolean upsertRow(String id, Element data) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void deleteRow(String id) throws KeyNotFoundException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public int deleteAll(XPathQuery query) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
            //</editor-fold>
        };
        
        Set<EngineBase> s = new HashSet<>();
        s.add(eng2);
        s.add(ctx.instance);
        Iterator<EngineBase> it = s.iterator();
        assertEquals("HashSet doesn't work as i thought", ctx.instance, it.next());
        assertEquals("HashSet doesn't work as i thought", eng2, it.next());
        
        try(TransactionScope tx = ctx.transactionManager.openTransaction()){
            
            ((FakeThreadContextTransactionManager)ctx.transactionManager).bindEngineToCurrentTransaction(eng2);
            
            ctx.instance.insertRow("0", new Element("data").setText("some text data"));
            ctx.instance.insertRow("1", new Element("second").setText("second text data"));
            
            try{
                tx.commit();
                fail("Expected TransactionException");
            }catch(TransactionException ex){
                //expected
            }
        }
        
        assertNull("Should have reverted data after partial commit", ctx.instance.readRow("0"));
        assertNull("Should have reverted data after partial commit", ctx.instance.readRow("1"));
    }
    
    /**
     * Test that the data persisted to disk is full enough that a new engine can revert
     * a committed transaction.  This tests durability of transactions.
     * @throws Exception 
     */
    @Test
    public void testCommit_EngineShutDown_NewEngineCanRevertCommittedData() throws Exception {
        System.out.println("testCommit_EngineShutDown_NewEngineCanRevertCommittedData");
        
        final TestContext ctx = getContext();
        
        EngineTransactionManager txManager = mock(EngineTransactionManager.class);
        ctx.transactionManager = txManager;
        ctx.instance.setTransactionManager(txManager);
        
        
        final AtomicLong txIds = new AtomicLong(37);
        
        when(txManager.transactionlessCommitId())
                .then(new Answer<Long>(){
            @Override
            public Long answer(InvocationOnMock invocation) throws Throwable {
                return txIds.incrementAndGet();
            }
                });
        
        prepFileContents(ctx, null);
        spinUp(ctx);
        
        ctx.instance.insertRow("0", new Element("data").setText("some text data"));
        ctx.instance.insertRow("1", new Element("second").setText("second text data"));
        
        //now we open a transaction - should not need transactionless ID anymore
        when(txManager.transactionlessCommitId())
                .thenThrow(Exception.class);
        Transaction tx = mock(Transaction.class);
        //assign TX and commit IDs
        long txId = txIds.incrementAndGet();
        when(tx.getTransactionId())
                .thenReturn(txId);
        long commitId = txIds.incrementAndGet();
        when(tx.getCommitId())
                .thenReturn(commitId);
        
        when(txManager.getTransaction())
                .thenReturn(tx);
        when(txManager.isTransactionCommitted(txId))
                .thenReturn(-1L);
        when(txManager.anyOpenTransactions())
                .thenReturn(true);
        when(txManager.getLowestOpenTransaction())
                .thenReturn(txId);
        
        
        //insert the transactional data
        ctx.instance.insertRow("2", new Element("transactional3").setText("tx text 3"));
        ctx.instance.update("1", XPathUpdate.set(XPathFactory.instance().compile("second"), "tx updated text"));
        ctx.instance.deleteRow("0");
        
        //ACT
        when(txManager.isCommitInProgress(tx.getCommitId()))
                .thenReturn(true);
        ctx.instance.commit(tx, TransactionOptions.DEFAULT);
        //pretend the process is failing, but let it do it's normal tasks (we're still in the process of committing anyways).
        ctx.executorService.shutdown();
        ctx.executorService.awaitTermination(5, TimeUnit.SECONDS);
        
        ctx.instance.forceSpinDown();
        
        System.out.println("partially committed data");
        System.out.println(dumpDoc(getFileContents(ctx)));
        
        //spin up a new engine instance and ask it to revert the old transaction
        ctx.executorService = new ScheduledThreadPoolExecutor(2);
        //ctx.executorService = mock(ScheduledExecutorService.class); //uncomment this for debugging        
        ctx.transactionManager = new FakeThreadContextTransactionManager(new FakeDocumentFileWrapper(ctx.transactionJournal));
        ctx.instance = setupEngine(ctx);
        
        spinUp(ctx);
        
        //wait a sec cause it might take us a while to actually end up reverting when we spin up
        Thread.sleep(1000);
        
        ctx.instance.revert(tx.getTransactionId(), true);
        
        //ASSERT
        Element row = ctx.instance.readRow("0");
        assertNotNull("Original data should exist", row);
        assertThat("Should have original data", row, isNamed("data"));
        assertThat("Should have original data", row, hasText("some text data"));
        
        row = ctx.instance.readRow("1");
        assertNotNull("Original data should exist", row);
        assertThat("Should have original data", row, isNamed("second"));
        assertThat("Should have original data", row, hasText("second text data"));
        
        row = ctx.instance.readRow("2");
        assertNull("Should not have kept transactionally inserted data", row);
    }
    
    //</editor-fold>
    
    //</editor-fold>
    
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
    
    private Matcher<Element> isNamed(final String name){
        return new TypeSafeMatcher<Element>(){
            @Override
            protected boolean matchesSafely(Element item) {
                return item.getName().equals(name);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is named ")
                        .appendText(name);
            }
            
        };
    }
    
    private Matcher<Element> hasText(final String text){
        return new TypeSafeMatcher<Element>(){
            @Override
            protected boolean matchesSafely(Element item) {
                return item.getText().equals(text);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has text ")
                        .appendText(text);
            }
            
        };
    }
    
    private static final XMLOutputter outputter = new XMLOutputter();
    /**
     * Dumps a document to a string for debugging purposes.
     * @param doc
     * @return The string representation of the document.
     */
    protected String dumpDoc(Document doc){
        return outputter.outputString(doc);
    }
    
    protected class TestContext{
        public TEngine instance;
        
        public AtomicBoolean spinDownInvoked = new AtomicBoolean(false);
    
        public File workspace;
        
        public long id;
        
        public AtomicReference<Document> transactionJournal = new AtomicReference<>(new Document().setRootElement(new Element("transactionJournal")));
        
        public EngineTransactionManager transactionManager = new FakeThreadContextTransactionManager(new FakeDocumentFileWrapper(transactionJournal));
        
        public final Map<String, Object> additionalContext;
        
        
        public ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2);
        
        public TestContext(){
            this.id = Thread.currentThread().getId();
            additionalContext = new HashMap<>();
        }
    }
}
