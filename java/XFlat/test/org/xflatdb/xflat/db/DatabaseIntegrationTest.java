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
import java.util.List;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xflatdb.xflat.Cursor;
import org.xflatdb.xflat.KeyValueTable;
import org.xflatdb.xflat.Table;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.util.DocumentFileWrapper;
import test.Baz;
import test.Foo;
import test.Utils;

/**
 *
 * @author gordon
 */
public class DatabaseIntegrationTest {
    
    static File workspace = new File("integrationtests");
    
    @BeforeClass
    public static void setUpClass(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
        }
    }
    
    private Document loadTableDoc(String testName, String tableName) throws IOException, JDOMException {
        
        LogFactory.getLog(getClass())
                .trace(String.format("getting table doc %s", tableName));
        
        File doc = new File(new File(workspace, testName), tableName + ".xml");
        
        return new DocumentFileWrapper(doc).readFile();
    }
    
    private XFlatDatabase getDatabase(String testName){
        File dbDir = new File(workspace, testName);
        XFlatDatabase ret = new XFlatDatabase(dbDir);
        
        return ret;
    }
    
    @Test
    public void testInsertAndRetrieve_Foo() throws Exception {
        System.out.println("testInsertAndRetrieve_Foo");
        
        XFlatDatabase db = getDatabase("InsertAndRetrieveFoo");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.Initialize();
        try{
        
        Table<Foo> fooTable = db.getTable(Foo.class);
        
        Foo f = new Foo();
        f.fooInt = 26;
        fooTable.insert(f);
        
        Foo f2 = fooTable.find(f.getId());
        
        
        assertEquals("Should retrieve equal foo", f, f2);
        assertNotSame("Should not be the same foo", f, f2);
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("InsertAndRetrieveFoo", "Foo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 1 row on disk", 1, rows.size());
        assertEquals("Should have right data", "26",
                rows.get(0).getChild("foo").getChild("fooInt").getText());
        
    }//end testInsertAndRetrieve_Foo
    
    @Test
    public void testInsertAndDelete_Foo() throws Exception {
        System.out.println("testInsertAndDelete_Foo");
        
        XFlatDatabase db = getDatabase("InsertAndDeleteFoo");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.Initialize();
        try{
        
        Table<Foo> fooTable = db.getTable(Foo.class);
        
        Foo f = new Foo();
        f.fooInt = 26;
        fooTable.insert(f);
        
        Foo f2 = fooTable.find(f.getId());
        
        assertEquals("Should retrieve equal foo", f, f2);
        
        fooTable.delete(f.getId());
        
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("InsertAndDeleteFoo", "Foo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have no rows on disk", 0, rows.size());
        
    }//end testInsertAndDelete_Foo
    
    @Test
    public void testInsertMany_QueriesAll() throws Exception {
        System.out.println("testInsertMany_QueriesAll");
        
        XFlatDatabase db = getDatabase("InsertMany_QueriesAll");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.configureTable("Foo", new TableConfig()
                .withIdGenerator(BigIntIdGenerator.class));
        
        db.Initialize();
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);

            for(int i = 0; i < 500; i++){
                Foo f = new Foo();
                f.fooInt = i;
                fooTable.insert(f);
            }

            XPathExpression<Object> expression = XPathFactory.instance().compile("foo/fooInt");
            try(Cursor<Foo> fooCursor = fooTable.find(XPathQuery.gte(expression, 100))){

                int i = 100;
                for(Foo f2 : fooCursor){
                    assertThat("Expected items 100 to 499", f2.fooInt,
                            Matchers.allOf(Matchers.greaterThanOrEqualTo(100), Matchers.lessThan(500)));
                    assertThat("Expected to use integer ID generator", Integer.parseInt(f2.getId()),
                            Matchers.allOf(Matchers.greaterThan(0), Matchers.lessThanOrEqualTo(500)));
                    i++;
                }

                assertEquals("Expected to retrieve 400 items", 500, i);
            }
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("InsertMany_QueriesAll", "Foo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 500 rows on disk", 500, rows.size());
        
    }//end testInsertMany_QueriesAll
    
    
    @Test
    public void testInsertMany_DeleteMatching() throws Exception {
        System.out.println("testInsertMany_DeleteMatching");
        
        XFlatDatabase db = getDatabase("InsertMany_DeleteMatching");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.configureTable("Foo", new TableConfig()
                .withIdGenerator(BigIntIdGenerator.class));
        
        db.Initialize();
        
        XPathQuery query;
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);

            for(int i = 0; i < 500; i++){
                Foo f = new Foo();
                f.fooInt = i;
                fooTable.insert(f);
            }

            XPathExpression<Object> expression = XPathFactory.instance().compile("foo/fooInt");
            query = XPathQuery.or(
                        XPathQuery.lt(expression, 100),
                        XPathQuery.eq(expression, 175),
                        XPathQuery.and(
                            XPathQuery.gt(expression, 400),
                            XPathQuery.matches(expression, isEven(), Integer.class)
                        )
                    );
            
            int rowsDeleted = fooTable.deleteAll(query);
            //100 for lt(100)
            //of [401-499], only the even ones.  That is, 49
            //1 for eq(175)
            assertEquals("Expected 150 rows deleted", 150, rowsDeleted);
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("InsertMany_DeleteMatching", "Foo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 350 rows on disk", 350, rows.size());
        
        Matcher<Element> rowMatcher = query.getRowMatcher();
        for(Element row : rows){
            assertThat("Should not have any matching rows",
                    row, Matchers.not(rowMatcher));
        }
        
    }//end testInsertMany_DeleteMatching
    
    @Test
    public void testInsert_Resume_Read() throws Exception {
        System.out.println("testInsert_Resume_Read");
        
        XFlatDatabase db = getDatabase("Insert_Resume_Read");
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.Initialize();
        
        Foo f = new Foo();
        f.fooInt = 84;
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);
            
            fooTable.insert(f);
        }
        finally{
            db.shutdown();
        }
        
        db = getDatabase("Insert_Resume_Read");
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.Initialize();
        
        try{
            Table<Foo> fooTable = db.getTable(Foo.class);
            
            Foo f2 = fooTable.find(f.getId());
            
            assertEquals("Should be able to read data", f.fooInt, f2.fooInt);
            assertNotSame("Should be different instance", f, f2);
        }
        finally{
            db.shutdown();
        }
        
    }//end testInsert_Resume_Read
    
    @Test
    public void testInsert_Resume_ValidatesConfig() throws Exception {
        System.out.println("testInsert_Resume_ValidatesConfig");
        
        XFlatDatabase db = getDatabase("Insert_Resume_Validate");
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.configureTable("Foo", new TableConfig()
                .withIdGenerator(TimestampIdGenerator.class));
        
        db.Initialize();
        
        Foo f = new Foo();
        f.fooInt = 84;
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);
            
            fooTable.insert(f);
        }
        finally{
            db.shutdown();
        }
        
        db = getDatabase("Insert_Resume_Validate");
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        //use a different ID generator
        db.configureTable("Foo", new TableConfig()
                .withIdGenerator(BigIntIdGenerator.class));
        
        boolean didThrow = false;
        try {
            //ACT
            db.Initialize();
        } catch (XFlatException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown XflatException", didThrow);
        
    }//end testInsert_Resume_ValidatesConfig
    
    @Test
    public void testInsert_Baz_UsesJaxbConversionService() throws Exception {
        System.out.println("testInsert_Baz_UsesJaxbConversionService");
        XFlatDatabase db = getDatabase("Insert_Baz_UsesJaxb");
                
        db.Initialize();
        try{
        
        Table<Baz> bazTable = db.getTable(Baz.class);
        
        Baz b = new Baz();
        b.setAttrInt(81);
        b.getTestData().add("test data 1");
        b.getTestData().add("test data 2");
        b.getTestData().add("test data 3");
        
        bazTable.insert(b);
        
        Baz b2 = bazTable.find(b.getId());
        
        
        assertNotSame("Should not be the same baz", b, b2);
        assertEquals("should be same data", b.getAttrInt(), b2.getAttrInt());
        assertThat("should be same data", b2.getTestData(),
                Matchers.contains("test data 1", "test data 2", "test data 3"));
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("Insert_Baz_UsesJaxb", "Baz");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 1 row on disk", 1, rows.size());
        assertEquals("Should have right data", "81",
                rows.get(0).getChild("baz").getAttributeValue("attrInt"));
        
    }//end testInsert_Baz_UsesJaxbConversionService
    
    //<editor-fold desc="key value pair table">
    
    @Test
    public void testAdd_Baz_NewBazAdded() throws Exception {
        System.out.println("testAdd_Baz_NewBazAdded");
        
        XFlatDatabase db = getDatabase("Add_Baz_NewBazAdded");

        db.Initialize();
        try{
        
            KeyValueTable table = db.getKeyValueTable("kvFoo");

            Baz b = new Baz();
            b.setAttrInt(81);
            b.getTestData().add("test data 1");
            b.getTestData().add("test data 2");
            b.getTestData().add("test data 3");

            table.add("test", b);

            Baz b2 = table.get("test", Baz.class);
            
            assertNotSame("Should not be the same baz", b, b2);
            assertEquals("should be same data", b.getAttrInt(), b2.getAttrInt());
            assertThat("should be same data", b2.getTestData(),
                    Matchers.contains("test data 1", "test data 2", "test data 3"));
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("Add_Baz_NewBazAdded", "kvFoo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 1 row on disk", 1, rows.size());
        assertEquals("Should have right data", "81",
                rows.get(0).getChild("baz").getAttributeValue("attrInt"));
    }
    
    @Test
    public void testSet_Baz_ReplacesOld() throws Exception {
        System.out.println("testSet_Baz_ReplacesOld");
        
        XFlatDatabase db = getDatabase("Set_Baz_ReplacesOld");
                
        db.Initialize();
        try{
        
            KeyValueTable table = db.getKeyValueTable("kvFoo");

            Baz b = new Baz();
            b.setAttrInt(81);
            b.getTestData().add("test data 1");
            b.getTestData().add("test data 2");
            b.getTestData().add("test data 3");

            table.add("test", b);
            
            Baz b2 = new Baz();
            b2.setAttrInt(82);
            b2.getTestData().add("test data 4");

            table.set("test", b2);
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("Set_Baz_ReplacesOld", "kvFoo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 1 row on disk", 1, rows.size());
        assertEquals("Should have new data on disk", "82",
                rows.get(0).getChild("baz").getAttributeValue("attrInt"));
    }
    
    @Test
    public void testPut_Baz_ReplacesOld() throws Exception {
        System.out.println("testPut_Baz_ReplacesOld");
        
         XFlatDatabase db = getDatabase("Put_Baz_ReplacesOld");
                
        db.Initialize();
        try{
        
            KeyValueTable table = db.getKeyValueTable("kvFoo");

            Baz b = new Baz();
            b.setAttrInt(81);
            b.getTestData().add("test data 1");
            b.getTestData().add("test data 2");
            b.getTestData().add("test data 3");

            table.add("test", b);
            
            Baz b2 = new Baz();
            b2.setAttrInt(82);
            b2.getTestData().add("test data 4");

            Baz bOld = table.put("test", b2);
            
            assertEquals("should return old data", b.getAttrInt(), bOld.getAttrInt());
            assertThat("should return old data", bOld.getTestData(),
                    Matchers.contains("test data 1", "test data 2", "test data 3"));
        }
        finally{
            db.shutdown();
        }
        
        Document tableDoc = this.loadTableDoc("Put_Baz_ReplacesOld", "kvFoo");
        List<Element> rows = Utils.getRows(tableDoc);
        assertEquals("Should have 1 row on disk", 1, rows.size());
        assertEquals("Should have new data on disk", "82",
                rows.get(0).getChild("baz").getAttributeValue("attrInt"));
    }
    
    //</editor-fold>
    
    private Matcher<Integer> isEven(){
        return new TypeSafeMatcher<Integer>(){
            @Override
            protected boolean matchesSafely(Integer item) {
                return item % 2 == 0;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an even integer");
            }
        };
    }
}

