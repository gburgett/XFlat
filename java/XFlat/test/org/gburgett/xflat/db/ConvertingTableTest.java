/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.DefaultConversionService;
import org.gburgett.xflat.convert.converters.JDomConverters;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.gburgett.xflat.engine.Engine;
import org.hamcrest.Matchers;
import org.jdom2.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
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
    
    ConvertingTable<Foo> fooInstance;
    
    @Before
    public void setUp() {
        conversionService = new DefaultConversionService();
        StringConverters.RegisterTo(conversionService);
        JDomConverters.RegisterTo(conversionService);
        conversionService.addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        conversionService.addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        database = mock(Database.class);
        engine = mock(Engine.class);
        idGenerator = mock(IdGenerator.class);
        
        fooInstance = new ConvertingTable<>(database, Foo.class, "foo");
        fooInstance.setConversionService(conversionService);
        fooInstance.setEngine(engine);
        fooInstance.setIdGenerator(idGenerator);
    }
    
    @After
    public void tearDown() {
    }

    private String getId(Element data){
        return data.getAttributeValue("id", Database.xFlatNs);
    }
    
    @Test
    public void testInsert_Foo_GeneratesId() throws Exception {
        System.out.println("testInsert_Foo_GeneratesId");
        
        Foo foo = new Foo();
        foo.fooInt = 17;
        
        String fooId = "testId";
        
        when(idGenerator.generateNewId(String.class))
                .thenReturn(fooId);
        when(idGenerator.idToString(fooId))
                .thenReturn(fooId);
        
        //ACT
        fooInstance.insert(foo);
        
        //ASSERT
        ArgumentCaptor<Element> dataCaptor = ArgumentCaptor.forClass(Element.class);
        verify(engine).insertRow(argThat(Matchers.equalTo(fooId)), dataCaptor.capture());
        
        Element data = dataCaptor.getValue();
        assertEquals("Should have set ID", fooId, getId(data));
        assertEquals("Data should be a foo", "foo", data.getName());
        assertEquals("Should have stored foo in element", "17", data.getChild("fooInt").getValue());
    }//end testInsert_Foo_GeneratesId
}
