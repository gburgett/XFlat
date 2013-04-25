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

import org.xflatdb.xflat.db.LocalTransactionalDatabase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.TableConfig;
import java.io.File;
import org.xflatdb.xflat.db.EngineBase.SpinDownEventHandler;
import org.xflatdb.xflat.engine.CachedDocumentEngine;
import org.xflatdb.xflat.util.DocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import test.Foo;
import test.Utils;

/**
 *
 * @author Gordon
 */
public class XFlatDatabaseTest {
    
    private static final File workspace = new File("XFlatDatabaseTest");
    
    @Before
    @After
    public void tearDown(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
            workspace.delete();
        }
    }
    
    @Test
    public void testGetTable_SpinsUpNewEngine() throws Exception {
        System.out.println("testGetTable_SpinsUpNewEngine");
        
        Document doc = new Document();
        
        DocumentFileWrapper wrapper = mock(DocumentFileWrapper.class);
        when(wrapper.readFile())
            .thenReturn(doc);
        
        EngineBase mEngine = spy(new CachedDocumentEngine(wrapper, "test"));
        
        EngineFactory factory = mock(EngineFactory.class);
        when(factory.newEngine(any(File.class), anyString(), any(TableConfig.class)))
            .thenReturn(mEngine);
        
        LocalTransactionalDatabase db = new LocalTransactionalDatabase(workspace);
        db.setEngineFactory(factory);
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.initialize();
        try{
            //act
            db.getTable(Foo.class);

            verify(mEngine).spinUp();
        }
        finally{
            db.shutdown();
        }
        
        verify(mEngine).spinDown(any(SpinDownEventHandler.class));
    }

    @Test
    public void testGetTable_NoActivity_TableIsSpunDown() throws Exception {
        System.out.println("testGetTable_NoActivity_TableIsSpunDown");
                Document doc = new Document();
        
        DocumentFileWrapper wrapper = mock(DocumentFileWrapper.class);
        when(wrapper.readFile())
            .thenReturn(doc);
        
        EngineBase mEngine = spy(new CachedDocumentEngine(wrapper, "test"));
        
        EngineFactory factory = mock(EngineFactory.class);
        when(factory.newEngine(any(File.class), anyString(), any(TableConfig.class)))
            .thenReturn(mEngine)
            .thenReturn(null);
        
        LocalTransactionalDatabase db = new LocalTransactionalDatabase(workspace);
        db.setEngineFactory(factory);
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.configureTable("Foo", new TableConfig().withInactivityShutdownMs(10));
        
        db.initialize();
        try{
            //act
            db.getTable(Foo.class);

            verify(mEngine).spinUp();
            
            Thread.sleep(600); //long enough for the update task to process
            
            verify(mEngine).spinDown(any(SpinDownEventHandler.class));
        }
        finally{
            db.shutdown();
        }
    }

    @Test
    public void testGetTable_NoActivity_SpinsUpNewEngine() throws Exception {
        System.out.println("testGetTable_NoActivity_TableIsSpunDown");
                Document doc = new Document();
        
        DocumentFileWrapper wrapper = mock(DocumentFileWrapper.class);
        when(wrapper.readFile())
            .thenReturn(doc);
        
        EngineBase mEngine = spy(new CachedDocumentEngine(wrapper, "test"));
        EngineBase mEngine2 = spy(new CachedDocumentEngine(wrapper, "test"));
        
        EngineFactory factory = mock(EngineFactory.class);
        when(factory.newEngine(any(File.class), anyString(), any(TableConfig.class)))
            .thenReturn(mEngine)
            .thenReturn(mEngine2)
            .thenReturn(null);
        
        LocalTransactionalDatabase db = new LocalTransactionalDatabase(workspace);
        db.setEngineFactory(factory);
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.configureTable("Foo", new TableConfig().withInactivityShutdownMs(10));
        
        db.initialize();
        try{
            //act
            db.getTable(Foo.class);

            verify(mEngine).spinUp();
            
            Thread.sleep(600); //long enough for the update task to process
            
            verify(mEngine).spinDown(any(SpinDownEventHandler.class));
            
            db.getTable(Foo.class);
            
            verify(mEngine2).spinUp();
        }
        finally{
            db.shutdown();
        }
        
        verify(mEngine2).spinDown(any(SpinDownEventHandler.class));
    }
}
