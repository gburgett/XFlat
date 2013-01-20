/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.Table;
import org.gburgett.xflat.TableConfig;
import org.gburgett.xflat.db.IntegerIdGenerator;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.NumericIntervalProvider;
import org.gburgett.xflat.query.XpathQuery;
import org.jdom2.Element;
import org.junit.BeforeClass;
import org.junit.Test;
import test.Foo;
import test.Utils;
import static org.junit.Assert.*;

/**
 *
 * @author Gordon
 */
public class IdShardedEngineIntegrationTests {
    static File workspace = new File(new File("DbIntegrationTests"), "IdShardedEngineIntegrationTests");
    
    static String tbl = "table";
    
    @BeforeClass
    public static void setUpClass(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
        }
    }
    
    private XFlatDatabase getDatabase(String testName){
        File dbDir = new File(workspace, testName);
        XFlatDatabase ret = new XFlatDatabase(dbDir);
        
        ret.configureTable(tbl, TableConfig.Default
                                    .setIdGenerator(IntegerIdGenerator.class)
                                    .sharded(ShardsetConfig.create(XpathQuery.Id, Integer.class, NumericIntervalProvider.forInteger(2, 100))));
        
        ret.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        ret.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        return ret;
    }
    
    
    @Test
    public void testInsertRetrieve_SingleShard_OneFileCreated() throws Exception {
        String testName = "testInsertRetrieve_SingleShard_OneFileCreated";
        System.out.println(testName);
        
        XFlatDatabase db = getDatabase(testName);
        
        db.Initialize();
        
        Table<Foo> table = db.getTable(Foo.class, this.tbl);
        
        Foo foo = new Foo();
        foo.fooInt = 1;
        
        table.insert(foo);
        
        Foo foo2 = table.find(foo.getId());
        
        assertEquals("should retrieve same data", foo, foo2);
        
        db.shutdown();
        
        File shardDir = new File(new File(workspace, testName), this.tbl + ".xml");
        assertTrue("shard directory should exist", shardDir.exists());
        assertTrue("shard directory should be a directory", shardDir.isDirectory());
        
        File[] shards = shardDir.listFiles();
        assertEquals("should be one shard", 1, shards.length);
        assertTrue("Should be named after the range of data", new File(shardDir, "-98.xml").exists());
    }
    
    @Test
    public void testInsertRetrieve_MultipleShards_MultipleFilesCreated() throws Exception {
        String testName = "testInsertRetrieve_MultipleShards_MultipleFilesCreated";
        System.out.println(testName);
        
        XFlatDatabase db = getDatabase(testName);
        
        db.Initialize();
        
        Table<Foo> table = db.getTable(Foo.class, this.tbl);
        
        Foo foo = new Foo();
        foo.fooInt = 1;        
        table.insert(foo);
        
        foo = new Foo();
        foo.fooInt = 2;
        table.insert(foo);
        
        foo = new Foo();
        foo.fooInt = 3;
        table.insert(foo);
        
        List<Foo> fooList = table.findAll(XpathQuery.gt(XpathQuery.Id, 1));
        
        //can't trust ordering of a query
        Collections.sort(fooList, new Comparator<Foo>(){
            @Override
            public int compare(Foo o1, Foo o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        
        assertEquals("Should get 2 foos", 2, fooList.size());
        assertEquals("should retrieve same data", 2, fooList.get(0).fooInt);
        assertEquals("should retrieve same data", 3, fooList.get(1).fooInt);
        
        
        db.shutdown();
        
        File shardDir = new File(new File(workspace, testName), this.tbl + ".xml");
        assertTrue("shard directory should exist", shardDir.exists());
        assertTrue("shard directory should be a directory", shardDir.isDirectory());
        
        File[] shards = shardDir.listFiles();
        assertEquals("should be two shards", 2, shards.length);
        assertTrue("Should be named after the range of data", new File(shardDir, "-98.xml").exists());
        assertTrue("Should be named after the range of data", new File(shardDir, "2.xml").exists());
    }
    
}
