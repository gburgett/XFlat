/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.KeyNotFoundException;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.converters.JDOMConverters;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.XpathUpdate;
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
import test.Bar;
import test.Foo;

/**
 *
 * @author gordon
 */
public class ConvertingTableTest {
    
    public ConvertingTableTest() {
    }
    
    ConversionService conversionService;
    Engine engine;
    IdGenerator idGenerator;
    Database database;
    
    private XPathFactory xpath;
    
    ConvertingTable<Foo> getFooInstance(){
        conversionService.addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        conversionService.addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        ConvertingTable<Foo> fooInstance = new ConvertingTable<>(database, Foo.class, "foo");
        fooInstance.setConversionService(conversionService);
        fooInstance.setEngine(engine);
        fooInstance.setIdGenerator(idGenerator);
        return fooInstance;
    };
    
    ConvertingTable<Bar> getBarInstance(){
        conversionService.addConverter(Bar.class, Element.class, new Bar.ToElementConverter());
        conversionService.addConverter(Element.class, Bar.class, new Bar.FromElementConverter());
        
        ConvertingTable<Bar> barInstance = new ConvertingTable<>(database, Bar.class, "foo");
        barInstance.setConversionService(conversionService);
        barInstance.setEngine(engine);
        barInstance.setIdGenerator(idGenerator);
        
        return barInstance;
    };
    
    @Before
    public void setUp() {
        conversionService = new DefaultConversionService();
        StringConverters.registerTo(conversionService);
        JDOMConverters.registerTo(conversionService);
        
        database = mock(Database.class);
        engine = mock(Engine.class);
        idGenerator = mock(IdGenerator.class);
        
        xpath = XPathFactory.instance();
    }
    
    @After
    public void tearDown() {
    }

    private String getId(Element data){
        return data.getAttributeValue("id", Database.xFlatNs);
    }
    
    private void setId(Element e, String id){
        e.setAttribute("id", id, Database.xFlatNs);
    }
    
    @Test
    public void testInsert_Foo_GeneratesId() throws Exception {
        System.out.println("testInsert_Foo_GeneratesId");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        String fooId = "testId";
        
        when(idGenerator.generateNewId(String.class))
                .thenReturn(fooId);
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        //ACT
        getFooInstance().insert(foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo(fooId)), dataCaptor.capture());
        
        Element data = dataCaptor.getValue();
        assertEquals("Should have set ID", fooId, getId(data));
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }//end testInsert_Foo_GeneratesId
    
    @Test
    public void testInsert_Foo_HasId_IdNotModified() throws Exception {
        System.out.println("testInsert_Foo_HasId_IdNotModified");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        foo.setId("fooId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        //ACT
        getFooInstance().insert(foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo("fooId")), dataCaptor.capture());
        
        verify(idGenerator, never()).generateNewId(any(Class.class));
                
        Element data = dataCaptor.getValue();
        assertEquals("Should have kept ID", "fooId", getId(data));
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }//end testInsert_Foo_HasId_IdNotModified
    
    @Test
    public void testInsert_Bar_GeneratesId() throws Exception {
        System.out.println("testInsert_Bar_GeneratesId");
        
        
        Bar bar = new Bar();
        bar.barString = "some data";
        
        String barId = "testId";
        
        when(idGenerator.generateNewId(String.class))
                .thenReturn(barId);
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        //ACT
        getBarInstance().insert(bar);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo(barId)), dataCaptor.capture());
        
        Element data = dataCaptor.getValue();
        assertEquals("Should have set ID", barId, getId(data));
        assertEquals("Data should be a bar", "bar", data.getName());
        assertEquals("Should have stored bar in element", "some data", data.getChild("barString").getValue());
    }//end testInsert_Bar_GeneratesId
    
    @Test
    public void testFind_Foo_IdDoesNotExist_ReturnsNull() throws Exception {
        System.out.println("testFind_Foo_IdDoesNotExist_ReturnsNull");
        
        String fooId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Foo inDb = getFooInstance().find(fooId);
        
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
        Foo found = getFooInstance().find(fooId);
        
        //ASSERT
        assertNotNull("Should have found some data", found);
        assertEquals("Should have deserialized", 32, found.fooInt);
        assertEquals("Should have set ID", fooId, found.getId());
    }//end testFind_Foo_GetsElement_DeserializesAndSetsId
    
    @Test
    public void testFind_Bar_GetsElement_Deserializes() throws Exception {
        System.out.println("testFind_Bar_GetsElement_Deserializes");
        
        String barId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = new Element("bar");
        setId(inDb, barId);
        inDb.addContent(new Element("barString").setText("stuff"));
        when(engine.readRow(barId))
                .thenReturn(inDb);
        
        //ACT
        Bar found = getBarInstance().find(barId);
        
        //ASSERT
        assertNotNull("Should get a bar", found);
        assertEquals("Should deserialize correctly", "stuff", found.barString);
    }//end testFind_Bar_GetsElement_Deserializes
    
    @Test
    public void testFindOne_Foo_DeserializesAndClosesCursor() throws Exception {
        System.out.println("testFindOne_Foo_DeserializesAndClosesCursor");
        
        String fooId = "test id";
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = new Element("foo");
        setId(inDb, fooId);
        inDb.addContent(new Element("fooInt").setText("32"));
        
        XpathQuery query = XpathQuery.eq(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        Foo found = getFooInstance().findOne(query);
        
        //ASSERT
        assertNotNull("Should have found one", found);
        assertEquals("Should have deserialized", 32, found.fooInt);
        
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
        
        XpathQuery query = XpathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        
        //ACT
        Cursor<Foo> fooCursor = getFooInstance().find(query);
        
        //ASSERT
        Iterator<Foo> i = fooCursor.iterator();
        Foo found1 = i.next();
        Foo found2 = i.next();
        
        assertEquals("Should have gotten correct values", 32, found1.fooInt);
        assertEquals("Should have gotten correct values", 33, found2.fooInt);
        assertEquals("Should have gotten correct IDs", "id 1", found1.getId());
        assertEquals("Should have gotten correct IDs", "id 2", found2.getId());
        
        //verify engine cursor is closed when we close returned cursor
        verify(mockCursor, never()).close();
        fooCursor.close();
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
        
        XpathQuery query = XpathQuery.gte(xpath.compile("foo/fooInt"), 32);
        
        Cursor<Element> mockCursor = getMockCursor(inDb1, inDb2);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        //ACT
        List<Foo> fooList = getFooInstance().findAll(query);
        
        //ASSERT
        Foo found1 = fooList.get(0);
        Foo found2 = fooList.get(1);
        
        assertEquals("Should have gotten correct values", 32, found1.fooInt);
        assertEquals("Should have gotten correct values", 33, found2.fooInt);
        assertEquals("Should have gotten correct IDs", "id 1", found1.getId());
        assertEquals("Should have gotten correct IDs", "id 2", found2.getId());
        
    }//end testFindAll_Foo_ConvertsAllToList
    
    @Test
    public void testReplace_Foo_ReplacesRowWithSameId() throws Exception {
        System.out.println("testReplace_Foo_ReplacesRowWithSameId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Foo replacement = new Foo();
        replacement.fooInt = 17;
        replacement.setId("test id");
        
        //act
        this.getFooInstance().replace(replacement);
        
        //assert
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id"), captor.capture());
        
        assertEquals("Should have replaced with serialized data", "17", 
                captor.getValue().getChild("fooInt").getText());
    }//end testReplace_Foo_ReplacesRowWithSameId
    
    @Test
    public void testReplace_Foo_NoId_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testReplace_Foo_NoId_ThrowsKeyNotFoundException");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Foo replacement = new Foo();
        replacement.fooInt = 17;
        replacement.setId(null);
        
        boolean didThrow = false;
        try {
            //ACT
            this.getFooInstance().replace(replacement);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
    }//end testReplace_Foo_NoId_ThrowsKeyNotFoundException
    
    @Test
    public void testReplace_Bar_IdIsKnown_ReplacesWithCorrectId() throws Exception {
        System.out.println("testReplace_Bar_IdIsKnown_ReplacesWithCorrectId");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = new Element("bar").setContent(new Element("barString").setText("test val"));
        setId(inDb, "bar id 1");
        when(engine.readRow("bar id 1"))
                .thenReturn(inDb);
        
        ConvertingTable<Bar> instance = getBarInstance();
        Bar barInDb = instance.find("bar id 1");
        
        barInDb.barString = "other value";
        
        //ACT
        instance.replace(barInDb);
        
        //ASSERT
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("bar id 1"), captor.capture());
        
        assertEquals("Should have updated to other value", "other value", 
                captor.getValue().getChild("barString").getText());
    }//end testReplace_Bar_IdIsKnown_ReplacesWithCorrectId
    
    @Test
    public void testReplace_Bar_NewInstance_ThrowsKeyNotFoundException() throws Exception {
        System.out.println("testReplace_Bar_NewInstance_ThrowsKeyNotFoundException");
        
        when(idGenerator.idToString(anyObject()))
                .thenAnswer(byCallingToString());
        
        Element inDb = new Element("bar").setContent(new Element("barString").setText("test val"));
        setId(inDb, "bar id 1");
        when(engine.readRow("bar id 1"))
                .thenReturn(inDb);
        
        ConvertingTable<Bar> instance = getBarInstance();
        Bar barInDb = instance.find("bar id 1");
        
        Bar newBar = new Bar();
        newBar.barString = "test val";
        
        //ACT
        boolean didThrow = false;
        try {
            //ACT
            instance.replace(newBar);
        } catch (KeyNotFoundException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown KeyNotFoundException", didThrow);
        
        //ASSERT
        verify(engine, never()).replaceRow(any(String.class), any(Element.class));
    }//end testReplace_Bar_NewInstance_ThrowsKeyNotFoundException
    
    @Test
    public void testReplaceOne_Foo_FindsAndReplaces() throws Exception {
        System.out.println("testReplaceOne_Foo_FindsAndReplaces");
        
        when(idGenerator.stringToId(anyString(), eq(String.class)))
                .thenAnswer(byReturningFirstParam());
        
        Element existing = new Element("foo").addContent(new Element("fooInt").setText("17"));
        setId(existing, "test id 1");
        
        XpathQuery query = XpathQuery.eq(xpath.compile("foo/fooInt"), 17);
        
        Cursor<Element> mockCursor = getMockCursor(existing);
        when(engine.queryTable(query))
                .thenReturn(mockCursor);
        
        Foo newFoo = new Foo();
        newFoo.fooInt = 4;
        newFoo.setId("to be replaced");
        
        //ACT
        getFooInstance().replaceOne(query, newFoo);
        
        //ASSERT
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id 1"), captor.capture());
        
        assertEquals("Should have replaced value", "4",
                captor.getValue().getChild("fooInt").getText());
        assertEquals("Should have set ID on object", "test id 1",
                newFoo.getId());
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
        
        XpathQuery query = XpathQuery.gte(xpath.compile("foo/fooInt"), 17);
        
        Cursor<Element> cursor1 = getMockCursor(existing);
        Cursor<Element> cursor2 = getMockCursor(existing2);
        when(engine.queryTable(query))
                .thenReturn(cursor1, cursor2);
        
        doThrow(new KeyNotFoundException("test"))
            .when(engine).replaceRow(eq("test id 1"), any(Element.class));
        
        
        Foo newFoo = new Foo();
        newFoo.fooInt = 4;
        newFoo.setId("to be replaced");
        
        //ACT
        getFooInstance().replaceOne(query, newFoo);
        
        //ASSERT
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).replaceRow(eq("test id 2"), captor.capture());
        
        assertEquals("Should have replaced value", "4",
                captor.getValue().getChild("fooInt").getText());
        assertEquals("Should have set ID on object", "test id 2",
                newFoo.getId());
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
        
        Foo toUpsert = new Foo();
        toUpsert.fooInt = 51;
        
        //ACT
        boolean didInsert = this.getFooInstance().upsert(toUpsert);
        
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
        
        Foo toUpsert = new Foo();
        toUpsert.fooInt = 51;
        toUpsert.setId(id);
        
        //ACT
        boolean didInsert = this.getFooInstance().upsert(toUpsert);
        
        //ASSERT
        assertFalse("Should have gotten default from upsertRow", didInsert);
        
        ArgumentCaptor<Element> data = ArgumentCaptor.forClass(Element.class);
        verify(engine).upsertRow(eq(id), data.capture());
        
        assertEquals("Should have inserted correct data", "51",
                data.getValue().getChild("fooInt").getText());
        
        verify(idGenerator, never()).generateNewId(any(Class.class));
    }//end testUpsert_Foo_HasId_UpsertsWithId
    
    @Test
    public void testUpsert_Bar_NewInstance_GeneratesNewIdAndRemembersIt() throws Exception {
        System.out.println("testUpsert_Bar_NewInstance_GeneratesNewIdAndRemembersIt");
        
        Bar toUpsert = new Bar();
        toUpsert.barString = "test data";
        
        String id = "test id";
        when(idGenerator.generateNewId(String.class))
                .thenReturn(id, "second invocation");
        
        //ACT
        ConvertingTable<Bar> instance = this.getBarInstance();
        boolean didInsert = instance.upsert(toUpsert);
        
        //ASSERT
        assertTrue("Should have inserted new foo", didInsert);
        
        ArgumentCaptor<Element> data = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(eq(id), data.capture());
        
        assertEquals("Should have inserted correct data", "test data",
                data.getValue().getChild("barString").getText());
        
        //verify we remembered the ID
        instance.replace(toUpsert);
        verify(engine).replaceRow(eq(id), any(Element.class));
        
        //verify we only generated the ID once
        verify(idGenerator, times(1)).generateNewId(String.class);
        
    }//end testUpsert_Bar_NewInstance_GeneratesNewIdAndRemembersIt
    
    
    @Test
    public void testUpsert_Bar_RememberedInstance_Upserts() throws Exception {
        System.out.println("testUpsert_Bar_RememberedInstance_Upserts");
        
        Bar toUpsert = new Bar();
        toUpsert.barString = "test data";
        
        String id = "test id";
        when(idGenerator.generateNewId(String.class))
                .thenReturn(id, "second invocation");
        
        ConvertingTable<Bar> instance = this.getBarInstance();
        instance.insert(toUpsert);
        
        toUpsert.barString = "new data";
        
        //ACT
        boolean didInsert = instance.upsert(toUpsert);
        
        //ASSERT
        assertFalse("should get default value from calling stub upsert", didInsert);
        
        ArgumentCaptor<Element> captor = ArgumentCaptor.forClass(Element.class);
        verify(engine).upsertRow(eq(id), captor.capture());
        
        assertEquals("Should update with correct data", "new data", 
                captor.getValue().getChild("barString").getText());
        
        verify(idGenerator, times(1)).generateNewId(any(Class.class));
        
    }//end testUpsert_Bar_RememberedInstance_Upserts
        
    @Test
    public void testUpdate_Id_ConvertsId() throws Exception {
        System.out.println("testUpdate_Id_ConvertsId");
        
        String id = "test id";
        Object oId = new Object();
        when(idGenerator.idToString(oId))
                .thenReturn(id);
        
        XpathUpdate update = XpathUpdate.set(xpath.compile("fooInt"), 32);
        
        when(engine.update(id, update))
                .thenReturn(Boolean.TRUE);
        
        //ACT
        boolean didUpdate = this.getFooInstance().update(oId, update);
        
        //ASSERT
        assertTrue("Should have invoked update to get true result", didUpdate);
        verify(engine).update(id, update);
        
    }//end testUpdate_Id_ConvertsId
    
    @Test
    public void testUpdate_Query_Passthrough() throws Exception {
        System.out.println("testUpdate_Query_Passthrough");
        
        XpathQuery query = XpathQuery.eq(xpath.compile("fooInt"), 17);
        XpathUpdate update = XpathUpdate.set(xpath.compile("fooInt"), 35);
        
        when(engine.update(query, update))
                .thenReturn(32);
        
        //ACT
        int rowsUpdated = this.getFooInstance().update(query, update);
        
        //ASSERT
        assertEquals("Should have gotten the result of our stubbed call",
                32, rowsUpdated);
        
    }//end testUpdate_Query_Passthrough
    
    @Test
    public void testDelete_Id_ConvertsId() throws Exception {
        System.out.println("testDelete_Id_ConvertsId");
        
        String id = "test id";
        Object oId = new Object();
        when(idGenerator.idToString(oId))
                .thenReturn(id);
        
        //ACT
        this.getFooInstance().delete(oId);
        
        //ASSERT
        verify(engine).deleteRow(id);
    }//end testDelete_Id_ConvertsId
    
    @Test
    public void testDeleteAll_Passthrough() throws Exception {
        System.out.println("testDeleteAll_Passthrough");
        
        XpathQuery query = XpathQuery.eq(xpath.compile("foo/fooInt"), 17);
        
        //ACT
        this.getFooInstance().deleteAll(query);
        
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
