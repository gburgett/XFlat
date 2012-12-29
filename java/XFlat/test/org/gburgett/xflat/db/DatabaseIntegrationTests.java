/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gburgett.xflat.Cursor;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.XflatException;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import test.Foo;
import test.Utils;

/**
 *
 * @author gordon
 */
public class DatabaseIntegrationTests {
    
    static File workspace = new File("DbIntegrationTests");
    
    @BeforeClass
    public static void setUpClass(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
        }
    }
    
    private Document loadTableDoc(String testName, String tableName) throws IOException, JDOMException {
        File doc = new File(new File(workspace, testName), tableName + ".xml");
        
        return new DocumentFileWrapper(doc).readFile();
    }
    
    private XFlatDatabase getDatabase(String testName){
        File dbDir = new File(workspace, testName);
        XFlatDatabase ret = new XFlatDatabase(dbDir);
        
        return ret;
    }
    
    @Test
    public void testStartupShutdown_NoProblems() throws Exception {
        System.out.println("testStartupShutdown_NoProblems");
        
        XFlatDatabase db = getDatabase("StartupShutdown_NoProblems");
        
        db.Initialize();
        try{

            Thread.sleep(100);

            db.shutdown();
        }
        finally{
            db.shutdown();
        }
    }//end testStartupShutdown_NoProblems
    
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
        
        db.configureTable("Foo", TableConfig.defaultConfig
                .setIdGenerator(IntegerIdGenerator.class));
        
        db.Initialize();
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);

            for(int i = 0; i < 500; i++){
                Foo f = new Foo();
                f.fooInt = i;
                fooTable.insert(f);
            }

            XPathExpression<Object> expression = XPathFactory.instance().compile("foo/fooInt");
            try(Cursor<Foo> fooCursor = fooTable.find(XpathQuery.gte(expression, 100))){

                int i = 100;
                for(Foo f : fooCursor){
                    assertThat("Expected items 100 to 499", f.fooInt,
                            Matchers.allOf(Matchers.greaterThanOrEqualTo(100), Matchers.lessThan(500)));
                    assertThat("Expected to use integer ID generator", Integer.parseInt(f.getId()),
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
        
        db.configureTable("Foo", TableConfig.defaultConfig
                .setIdGenerator(IntegerIdGenerator.class));
        
        db.Initialize();
        
        XpathQuery query;
        try{
        
            Table<Foo> fooTable = db.getTable(Foo.class);

            for(int i = 0; i < 500; i++){
                Foo f = new Foo();
                f.fooInt = i;
                fooTable.insert(f);
            }

            XPathExpression<Object> expression = XPathFactory.instance().compile("foo/fooInt");
            query = XpathQuery.or(
                        XpathQuery.lt(expression, 100),
                        XpathQuery.eq(expression, 175),
                        XpathQuery.and(
                            XpathQuery.gt(expression, 400),
                            XpathQuery.matches(expression, isEven(), Integer.class)
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
        
        db.configureTable("Foo", TableConfig.defaultConfig
                .setIdGenerator(TimestampIdGenerator.class));
        
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
        db.configureTable("Foo", TableConfig.defaultConfig
                .setIdGenerator(IntegerIdGenerator.class));
        
        boolean didThrow = false;
        try {
            //ACT
            db.Initialize();
        } catch (XflatException expected) {
            didThrow = true;
        }
        assertTrue("Should have thrown XflatException", didThrow);
        
    }//end testInsert_Resume_ValidatesConfig
    
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

