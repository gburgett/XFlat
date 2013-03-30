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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.KeyNotFoundException;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.DefaultConversionService;
import org.xflatdb.xflat.convert.converters.JDOMConverters;
import org.xflatdb.xflat.convert.converters.StringConverters;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.hamcrest.Matchers;
import org.jdom2.Element;
import org.jdom2.xpath.XPathFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.xflatdb.xflat.transaction.TransactionOptions;
import org.xflatdb.xflat.transaction.TransactionScope;
import test.Bar;
import test.Foo;

/**
 *
 * @author gordon
 */
public class ConvertingKeyValueTableTest {
    
    public ConvertingKeyValueTableTest() {
    }
    
    ConversionService conversionService;
    Engine engine;
    TransactionManager transactionManager;
    
    private XPathFactory xpath;
    
    ConvertingKeyValueTable getInstance(){
        conversionService.addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        conversionService.addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        conversionService.addConverter(Bar.class, Element.class, new Bar.ToElementConverter());
        conversionService.addConverter(Element.class, Bar.class, new Bar.FromElementConverter());
        
        
        ConvertingKeyValueTable fooInstance = new ConvertingKeyValueTable("foo");
        fooInstance.setConversionService(conversionService);
        fooInstance.setEngineProvider(new EngineProvider() {
            @Override
            public Engine provideEngine() {
                return engine;
            }
        });
        fooInstance.setTransactionService(transactionManager);
        
        return fooInstance;
    };
    
    @Before
    public void setUp() {
        conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        
        engine = mock(Engine.class);
        transactionManager = mock(TransactionManager.class);
        
        xpath = XPathFactory.instance();
    }
    
    @After
    public void tearDown() {
    }

    private String getId(Element data){
        return data.getAttributeValue("id", XFlatDatabase.xFlatNs);
    }
    
    private void setId(Element e, String id){
        e.setAttribute("id", id, XFlatDatabase.xFlatNs);
    }
    
    @Test
    public void testAdd_Foo_FooIsAdded() throws Exception {
        System.out.println("testAdd_Foo_FooIsAdded");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        //ACT
        getInstance().add("foo", foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo("foo")), dataCaptor.capture());
                
        Element data = dataCaptor.getValue();
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());        
    }
    
    @Test
    public void testSet_Foo_FooIsSet() throws Exception {
        System.out.println("testSet_Foo_FooIsSet");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        //ACT
        getInstance().set("foo", foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).upsertRow(argThat(Matchers.equalTo("foo")), dataCaptor.capture());
                
        Element data = dataCaptor.getValue();
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());   
    }
    
    @Test
    public void testPut_Foo_RowDoesNotExist_RowIsInserted() throws Exception {
        System.out.println("testPut_Foo_RowDoesNotExist_RowIsInserted");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        TransactionScope mockTxScope = mock(TransactionScope.class);
        when(transactionManager.openTransaction(any(TransactionOptions.class)))
                .thenReturn(mockTxScope);
                
        
        //ACT
        Foo old = getInstance().put("foo", foo);
        
        //ASSERT
        assertNull(old);
        
        verify(mockTxScope).commit();
        
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo("foo")), dataCaptor.capture());
        
        
        Element data = dataCaptor.getValue();
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }
    
    @Test
    public void testPut_Foo_RowExists_OldRowIsReturned() throws Exception {
        System.out.println("testPut_Foo_RowExists_OldRowIsReturned");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        TransactionScope mockTxScope = mock(TransactionScope.class);
        when(transactionManager.openTransaction(any(TransactionOptions.class)))
                .thenReturn(mockTxScope);
                
        when(engine.readRow("foo"))
                .thenReturn(new Element("Foo").setContent( new Element("fooInt").setText("16")));
        
        
        //ACT
        Foo old = getInstance().put("foo", foo);
        
        //ASSERT
        assertNotNull(old);
        assertEquals(16, old.fooInt);
        
        verify(mockTxScope).commit();
        
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(argThat(Matchers.equalTo("foo")), dataCaptor.capture());
        
        
        Element data = dataCaptor.getValue();
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }
    
    @Test
    public void testGet_NoDataInDb_NothingReturned() throws Exception {
        System.out.println("testGet_NoDataInDb_NothingReturned");
        
        //act
        Foo inDb = getInstance().get("foo", Foo.class);
        
        //assert
        assertNull(inDb);
    }
    
    @Test
    public void testGet_DataInDb_GetsData() throws Exception {
        System.out.println("testGet_DataInDb_GetsData");
        
        when(engine.readRow("foo"))
                .thenReturn(new Element("Foo").setContent( new Element("fooInt").setText("16")));
        
        //act
        Foo inDb = getInstance().get("foo", Foo.class);
        
        //assert
        assertNotNull(inDb);
        assertEquals(16, inDb.fooInt);
    }

    @Test
    public void testFindOne_Foo_DeserializesAndClosesCursor() throws Exception {
        System.out.println("testFindOne_Foo_DeserializesAndClosesCursor");
        
        
        Element inDb = new Element("foo");
        inDb.addContent(new Element("fooInt").setText("32"));
        
        XPathQuery query = XPathQuery.eq(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        Foo found = getInstance().findOne(query, Foo.class);
        
        //ASSERT
        assertNotNull("Should have found one", found);
        assertEquals("Should have deserialized", 32, found.fooInt);
        
        verify(mockCursor).close();
    }//end testFindOne_Foo_DeserializesAndClosesCursor

    @Test
    public void testFind_Foo_ReturnsConvertingCursor() throws Exception {
        System.out.println("testFind_Foo_ReturnsConvertingCursor");
        
        Element inDb1 = new Element("foo")
                .addContent(new Element("fooInt").setText("32"));
        Element inDb2 = new Element("foo")
                .addContent(new Element("fooInt").setText("33"));
        
        XPathQuery query = XPathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        
        //ACT
        Cursor<Foo> fooCursor = getInstance().find(query, Foo.class);
        
        //ASSERT
        Iterator<Foo> i = fooCursor.iterator();
        Foo found1 = i.next();
        Foo found2 = i.next();
        
        assertEquals("Should have gotten correct values", 32, found1.fooInt);
        assertEquals("Should have gotten correct values", 33, found2.fooInt);
        
        //verify engine cursor is closed when we close returned cursor
        verify(mockCursor, never()).close();
        fooCursor.close();
        verify(mockCursor).close();
        
    }//end testFind_Foo_ReturnsConvertingCursor
    
    
    @Test
    public void testFindAll_Foo_ConvertsAllToList() throws Exception {
        System.out.println("testFindAll_Foo_ConvertsAllToList");
                
        Element inDb1 = new Element("foo")
                .addContent(new Element("fooInt").setText("32"));
        Element inDb2 = new Element("foo")
                .addContent(new Element("fooInt").setText("33"));
        
        XPathQuery query = XPathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        List<Foo> fooList = getInstance().findAll(query, Foo.class);
        
        //ASSERT
        Foo found1 = fooList.get(0);
        Foo found2 = fooList.get(1);
        
        assertEquals("Should have gotten correct values", 32, found1.fooInt);
        assertEquals("Should have gotten correct values", 33, found2.fooInt);
        
    }//end testFindAll_Foo_ConvertsAllToList
    
    @Test
    public void testReplace_Foo_ReplacesRowWithSameId() throws Exception {
        System.out.println("testReplace_Foo_ReplacesRowWithSameId");
                
        Foo replacement = new Foo();
        replacement.fooInt = 17;
        replacement.setId("test id");
        
        //act
        this.getInstance().replace("test id", replacement);
        
        //assert
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id"), captor.capture());
        
        assertEquals("Should have replaced with serialized data", "17", 
                captor.getValue().getChild("fooInt").getText());
    }//end testReplace_Foo_ReplacesRowWithSameId

    @Test
    public void testUpdate_SendsThroughToEngine() throws Exception {
        System.out.println("testUpdate_SendsThroughToEngine");
        
        XPathUpdate update = XPathUpdate.set(xpath.compile("fooInt"), 32);
        
        when(engine.update("test", update))
                .thenReturn(Boolean.TRUE);
        
        //ACT
        boolean didUpdate = this.getInstance().update("test", update);
        
        //ASSERT
        assertTrue("Should have invoked update to get true result", didUpdate);
        verify(engine).update("test", update);
        
    }//end testUpdate_Id_ConvertsId
    
    @Test
    public void testDelete_SendsThroughToEngine() throws Exception {
        System.out.println("testDelete_SendsThroughToEngine");
        
        //ACT
        this.getInstance().delete("test");
        
        //ASSERT
        verify(engine).deleteRow("test");
    }//end testDelete_Id_ConvertsId
    
    @Test
    public void testDeleteAll_Passthrough() throws Exception {
        System.out.println("testDeleteAll_Passthrough");
        
        XPathQuery query = XPathQuery.eq(xpath.compile("foo/fooInt"), 17);
        
        //ACT
        this.getInstance().deleteAll(query);
        
        //ASSERT
        verify(engine).deleteAll(query);
    }//end testDeleteAll_Passthrough
    
    private Cursor<Element> getMockCursor(Element... elements){
        return getMockCursor(Arrays.asList(elements));
    }
    
    private Cursor<Element> getMockCursor(final Iterable<Element> elements){
        Cursor<Element> ret = mock(Cursor.class);
        when(ret.iterator())
                .thenReturn(elements.iterator());
        
        return ret;
    }
    
}
