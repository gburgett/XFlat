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
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xflatdb.xflat.XFlatConstants;

/**
 *
 * @author gordon
 */
public class ElementTableTest {
    
    public ElementTableTest() {
    }
    
    ConversionService conversionService;
    Engine engine;
    IdGenerator idGenerator;
    
    private XPathFactory xpath;
    
    ElementTable getInstance(){
        ElementTable ret = new ElementTable("test");
        ret.setIdGenerator(idGenerator);
        ret.setEngineProvider(new EngineProvider() {
            @Override
            public Engine provideEngine() {
                return engine;
            }
        });
        return ret;
    };
    
    @Before
    public void setUp() {
        conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        
        engine = mock(Engine.class);
        idGenerator = mock(IdGenerator.class);
        
        xpath = XPathFactory.instance();
    }
    
    @After
    public void tearDown() {
    }

    private String getId(Element data){
        return data.getAttributeValue("id", XFlatConstants.xFlatNs);
    }
    
    private void setId(Element e, String id){
        if(id == null){
            e.removeAttribute("id", XFlatConstants.xFlatNs);
            return;
        }
        e.setAttribute("id", id, XFlatConstants.xFlatNs);
    }
    
    @Test
    public void testInsert_GeneratesId() throws Exception {
        System.out.println("testInsert_GeneratesId");
        
        Element foo = new Element("foo");
        foo.setContent(new Element("fooInt").setText("17"));
        
        String fooId = "testId";
        
        when(idGenerator.generateNewId(String.class))
                .thenReturn(fooId);
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        //ACT
        getInstance().insert(foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo(fooId)), dataCaptor.capture());
        
        Element data = dataCaptor.getValue();
        assertEquals("Should have set ID", fooId, getId(data));
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }//end testInsert_GeneratesId
    
    @Test
    public void testInsert_HasId_IdNotModified() throws Exception {
        System.out.println("testInsert_HasId_IdNotModified");
        
        Element foo = new Element("foo");
        foo.setContent(new Element("fooInt").setText("17"));
        setId(foo, "fooId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        //ACT
        getInstance().insert(foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo("fooId")), dataCaptor.capture());
        
        verify(idGenerator, never()).generateNewId(any(Class.class));
                
        Element data = dataCaptor.getValue();
        assertEquals("Should have kept ID", "fooId", getId(data));
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }//end testInsert_HasId_IdNotModified
    
    @Test
    public void testFind_Foo_IdDoesNotExist_ReturnsNull() throws Exception {
        System.out.println("testFind_Foo_IdDoesNotExist_ReturnsNull");
        
        String fooId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = getInstance().find(fooId);
        
        assertNull("Nothing in db, should find nothing", inDb);
        verify(engine).readRow(fooId);
    }//end testFind_Foo_IdDoesNotExist_ReturnsNull
    
    @Test
    public void testFind_Foo_GetsElement_DeserializesAndSetsId() throws Exception {
        System.out.println("testFind_Foo_GetsElement_DeserializesAndSetsId");
        
        String fooId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        when(idGenerator.stringToId(any(String.class), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element inDb = new Element("foo");
        setId(inDb, fooId);
        inDb.addContent(new Element("fooInt").setText("32"));
        when(engine.readRow(fooId))
                .thenReturn(inDb);
        
        //ACT
        Element found = getInstance().find(fooId);
        
        //ASSERT
        assertNotNull("Should have found some data", found);
        assertEquals("Should have found right data", "32", found.getChild("fooInt").getText());
        assertEquals("Should have set ID", fooId, getId(found));
    }//end testFind_Foo_GetsElement_DeserializesAndSetsId
    
    @Test
    public void testFindOne_Foo_DeserializesAndClosesCursor() throws Exception {
        System.out.println("testFindOne_Foo_DeserializesAndClosesCursor");
        
        String fooId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = new Element("foo");
        setId(inDb, fooId);
        inDb.addContent(new Element("fooInt").setText("32"));
        
        XPathQuery query = XPathQuery.eq(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        Element found = getInstance().findOne(query);
        
        //ASSERT
        assertNotNull("Should have found one", found);
        assertEquals("Should have got right one", "32", found.getChild("fooInt").getText());
        
        verify(mockCursor).close();
    }//end testFindOne_Foo_DeserializesAndClosesCursor

    @Test
    public void testFind_Foo_ReturnsConvertingCursor() throws Exception {
        System.out.println("testFind_Foo_ReturnsConvertingCursor");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        when(idGenerator.stringToId(any(String.class), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element inDb1 = new Element("foo").addContent(new Element("fooInt").setText("32"));
        setId(inDb1, "id 1");
        Element inDb2 = new Element("foo").addContent(new Element("fooInt").setText("33"));
        setId(inDb2, "id 2");
        
        XPathQuery query = XPathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        try (Cursor<Element> fooCursor = getInstance().find(query)) {
            Iterator<Element> i = fooCursor.iterator();
            Element found1 = i.next();
            Element found2 = i.next();
            
            assertEquals("Should have gotten correct values", "32", found1.getChild("fooInt").getText());
            assertEquals("Should have gotten correct values", "33", found2.getChild("fooInt").getText());
            assertEquals("Should have gotten correct IDs", "id 1", getId(found1));
            assertEquals("Should have gotten correct IDs", "id 2", getId(found2));
            
            //verify engine cursor is closed when we close returned cursor
            verify(mockCursor, never()).close();
        }
        
        verify(mockCursor).close();
        
    }//end testFind_Foo_ReturnsConvertingCursor
    
    
    @Test
    public void testFindAll_Foo_ConvertsAllToList() throws Exception {
        System.out.println("testFindAll_Foo_ConvertsAllToList");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        when(idGenerator.stringToId(any(String.class), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element inDb1 = new Element("foo").addContent(new Element("fooInt").setText("32"));
        setId(inDb1, "id 1");
        Element inDb2 = new Element("foo").addContent(new Element("fooInt").setText("33"));
        setId(inDb2, "id 2");
        
        XPathQuery query = XPathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        List<Element> fooList = getInstance().findAll(query);
        
        //ASSERT
        Element found1 = fooList.get(0);
        Element found2 = fooList.get(1);
        
        assertEquals("Should have gotten correct values", "32", found1.getChild("fooInt").getText());
        assertEquals("Should have gotten correct values", "33", found2.getChild("fooInt").getText());
        assertEquals("Should have gotten correct IDs", "id 1", getId(found1));
        assertEquals("Should have gotten correct IDs", "id 2", getId(found2));
        
    }//end testFindAll_Foo_ConvertsAllToList
    
    @Test
    public void testReplace_Foo_ReplacesRowWithSameId() throws Exception {
        System.out.println("testReplace_Foo_ReplacesRowWithSameId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element replacement = new Element("foo");
        replacement.setContent(new Element("fooInt").setText("17"));
        setId(replacement, "test id");
        
        //act
        this.getInstance().replace(replacement);
        
        //assert
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id"), captor.capture());
        
        assertEquals("Should have replaced with correct data", "17", 
                captor.getValue().getChild("fooInt").getText());
    }//end testReplace_Foo_ReplacesRowWithSameId
    
    @Test
    public void testReplace_Foo_NoId_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testReplace_Foo_NoId_ThrowsKeyNotFoundException");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element replacement = new Element("foo");
        replacement.setContent(new Element("fooInt").setText("17"));
        setId(replacement, null);
        
        boolean didThrow = false;
        try {
            //ACT
            this.getInstance().replace(replacement);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
    }//end testReplace_Foo_NoId_ThrowsKeyNotFoundException
    
    @Test
    public void testReplaceOne_Foo_FindsAndReplaces() throws Exception {
        System.out.println("testReplaceOne_Foo_FindsAndReplaces");
        
        when(idGenerator.stringToId(anyString(), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element existing = new Element("foo").addContent(new Element("fooInt").setText("17"));
        setId(existing, "test id 1");
        
        XPathQuery query = XPathQuery.eq(xpath.compile("foo/fooInt"), 17);
        
        Cursor<Element> mockCursor = getMockCursor(existing);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        Element newFoo = new Element("foo");
        newFoo.setContent(new Element("fooInt").setText("4"));
        setId(newFoo, "to be replaced");
        
        //ACT
        getInstance().replaceOne(query, newFoo);
        
        //ASSERT
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id 1"), captor.capture());
        
        assertEquals("Should have replaced value", "4",
                captor.getValue().getChild("fooInt").getText());
        assertEquals("Should have set ID on object", "test id 1",
                getId(newFoo));
    }//end testReplaceOne_Foo_FindsAndReplaces
    
    @Test
    public void testReplaceOne_ConcurrentRemove_FindsSecond() throws Exception {
        System.out.println("testReplaceOne_ConcurrentRemove_FindsSecond");
        
        when(idGenerator.stringToId(anyString(), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element existing = new Element("foo").addContent(new Element("fooInt").setText("17"));
        setId(existing, "test id 1");
        Element existing2 = new Element("foo").addContent(new Element("fooInt").setText("18"));
        setId(existing2, "test id 2");
        
        XPathQuery query = XPathQuery.gte(xpath.compile("foo/fooInt"), 17);
        
        Cursor<Element> cursor1 = getMockCursor(existing);
        Cursor<Element> cursor2 = getMockCursor(existing2);
        when(engine.queryTable(query))
                .thenReturn(cursor1, cursor2);
        
        doThrow(new KeyNotFoundException("test"))
            .when(engine).replaceRow(eq("test id 1"), any(Element.class));
        
        
        Element newFoo = new Element("foo");
        newFoo.setContent(new Element("fooInt").setText("4"));
        setId(newFoo, "to be replaced");
        
        //ACT
        getInstance().replaceOne(query, newFoo);
        
        //ASSERT
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id 2"), captor.capture());
        
        assertEquals("Should have replaced value", "4",
                captor.getValue().getChild("fooInt").getText());
        assertEquals("Should have set ID on object", "test id 2",
                getId(newFoo));
    }//end testReplaceOne_ConcurrentRemove_FindsSecond
    
    @Test
    public void testUpsert_Foo_NoId_InsertsWithNewId() throws Exception {
        System.out.println("testUpsert_Foo_NoId_InsertsWithNewId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        when(idGenerator.stringToId(any(String.class), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        String id = "test id";
        when(idGenerator.generateNewId(String.class))
                .thenReturn(id);
        
        Element toUpsert = new Element("foo");
        toUpsert.setContent(new Element("fooInt").setText("51"));
        
        //ACT
        boolean didInsert = this.getInstance().upsert(toUpsert);
        
        //ASSERT
        assertTrue("Should have inserted new foo", didInsert);
        
        ArgumentCaptor<Element> data = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(eq(id), data.capture());
        
        assertEquals("Should have inserted correct data", "51",
                data.getValue().getChild("fooInt").getText());
        
    }//end testUpsert_Foo_NoId_InsertsWithNewId
    
    @Test
    public void testUpsert_Foo_HasId_UpsertsWithId() throws Exception {
        System.out.println("testUpsert_Foo_HasId_UpsertsWithId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        when(idGenerator.stringToId(any(String.class), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        String id = "test id";
        
        Element toUpsert = new Element("foo");
        toUpsert.setContent(new Element("fooInt").setText("51"));
        setId(toUpsert, id);
        
        //ACT
        boolean didInsert = this.getInstance().upsert(toUpsert);
        
        //ASSERT
        assertFalse("Should have gotten default from upsertRow", didInsert);
        
        ArgumentCaptor<Element> data = ArgumentCaptor.forClass(Element.class);
        verify(engine).upsertRow(eq(id), data.capture());
        
        assertEquals("Should have inserted correct data", "51",
                data.getValue().getChild("fooInt").getText());
        
        verify(idGenerator, never()).generateNewId(any(Class.class));
    }//end testUpsert_Foo_HasId_UpsertsWithId
    
    
    @Test
    public void testUpdate_Id_ConvertsId() throws Exception {
        System.out.println("testUpdate_Id_ConvertsId");
        
        String id = "test id";
        
        XPathUpdate update = XPathUpdate.set(xpath.compile("fooInt"), 32);
        
        when(engine.update(id, update))
                .thenReturn(Boolean.TRUE);
        
        //ACT
        boolean didUpdate = this.getInstance().update(id, update);
        
        //ASSERT
        assertTrue("Should have invoked update to get true result", didUpdate);
        verify(engine).update(id, update);
        
    }//end testUpdate_Id_ConvertsId
    
    @Test
    public void testUpdate_Query_Passthrough() throws Exception {
        System.out.println("testUpdate_Query_Passthrough");
        
        XPathQuery query = XPathQuery.eq(xpath.compile("fooInt"), 17);
        XPathUpdate update = XPathUpdate.set(xpath.compile("fooInt"), 35);
        
        when(engine.update(query, update))
                .thenReturn(32);
        
        //ACT
        int rowsUpdated = this.getInstance().update(query, update);
        
        //ASSERT
        assertEquals("Should have gotten the result of our stubbed call",
                32, rowsUpdated);
        
    }//end testUpdate_Query_Passthrough
    
    @Test
    public void testDelete_Id_ConvertsId() throws Exception {
        System.out.println("testDelete_Id_ConvertsId");
        
        String id = "test id";
        
        //ACT
        this.getInstance().delete(id);
        
        //ASSERT
        verify(engine).deleteRow(id);
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
    
    private Answer<String> byCallingToString(){
        return new Answer<String>(){
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return args[0].toString();
            }
        };
    }
    
    private Answer<Object> byReturningFirstParam(){
        return new Answer<Object>(){
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return args[0];
            }
        };
    }
    
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
