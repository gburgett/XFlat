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
package org.xflatdb.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xflatdb.xflat.ShardsetConfig;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.convert.ConversionService;
import org.xflatdb.xflat.convert.PojoConverter;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.db.EngineTransactionManager;
import org.xflatdb.xflat.db.ShardedEngineTestsBase;
import org.xflatdb.xflat.db.TableMetadataFactory;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.query.Interval;
import org.xflatdb.xflat.query.IntervalProvider;
import org.xflatdb.xflat.query.NumericIntervalProvider;
import org.xflatdb.xflat.transaction.TransactionManager;
import org.xflatdb.xflat.util.DocumentFileWrapper;

/**
 *
 * @author gordon
 */
public class IdShardedEngineTest extends ShardedEngineTestsBase<IdShardedEngine> {

    Log log = LogFactory.getLog(getClass());
    
    String name = "IdShardedEngineTest";
    
    XMLOutputter outputter = new XMLOutputter();
           
    @Override
    protected void prepContext(final TestContext ctx){
                final Map<String, Document> docs = new ConcurrentHashMap<>();
        ctx.additionalContext.put("docs", docs);
        
        XFlatDatabase db = new XFlatDatabase(workspace, ctx.executorService){
            //override to always return the executor service set on the context
            @Override
            protected ScheduledExecutorService getExecutorService(){
                return ctx.executorService;
            }
            
            @Override
            protected EngineTransactionManager getEngineTransactionManager(){
                return ctx.transactionManager;
            }
            
            @Override
            public TransactionManager getTransactionManager(){
                return ctx.transactionManager;
            }
            
            @Override
            public DatabaseState getState(){
                return DatabaseState.Running;
            }
        };
        
        db.setPojoConverter(new PojoConverter(){
            @Override
            public ConversionService extend(ConversionService service) {
                return conversionService;
            }
        });
        db.setEngineFactory(new EngineFactory(){
            @Override
            public EngineBase newEngine(final File file, String tableName, TableConfig config) {
                DocumentFileWrapper wrapper = new DocumentFileWrapper(file){
                    @Override
                    public Document readFile(){
                        return docs.get(file.getName());
                    }
                    
                    @Override
                    public void writeFile(Document doc){
                        log.debug("writing file " + file.getName());
                        if(log.isTraceEnabled())
                            log.trace(outputter.outputString(doc));
                        docs.put(file.getName(), doc);
                    }
                    
                    @Override
                    public boolean exists(){
                        return docs.containsKey(file.getName());
                    }
                    
                    @Override
                    public Document readFile(String fileName){
                        fail("Should not have invoked readFile(fileName)");
                        return null;
                    }
                    
                    @Override
                    public void writeFile(String fileName, Document doc){
                        fail("Should not have invoked writeFile(fileName, doc)");
                    }
                };
                
                EngineBase ret = new CachedDocumentEngine(wrapper, tableName);
                return ret;
            }
        });
        ctx.additionalContext.put("db", db);        
        
        IntervalProvider provider = NumericIntervalProvider.forInteger(1, 100);
        ctx.additionalContext.put("rangeProvider", provider);
        
        File file = spy(new File(ctx.workspace, name));
        when(file.exists()).thenReturn(true);
        when(file.isDirectory()).thenReturn(true);
        when(file.listFiles())
                .then(new Answer<File[]>(){

            @Override
            public File[] answer(InvocationOnMock invocation) throws Throwable {
                File[] ret = new File[docs.size()];
                int i = 0;
                for(String name : docs.keySet()){
                    ret[i++] = new File(name);
                }
                return ret;
            }
        });
        ctx.additionalContext.put("file", file);
    }
    
    @Override
    protected IdShardedEngine createInstance(TestContext ctx) {
        

        File file = (File)ctx.additionalContext.get("file");
        IntervalProvider provider = (IntervalProvider)ctx.additionalContext.get("rangeProvider");
        XFlatDatabase db = (XFlatDatabase)ctx.additionalContext.get("db");
        
        ShardsetConfig cfg = ShardsetConfig.byId(Integer.class, provider);
        
        IdShardedEngine ret = new IdShardedEngine(file, name, cfg);
        setMetadataFactory(ret, new TableMetadataFactory(db, file));
        
        return ret;
    }

    @Override
    protected void prepFileContents(TestContext ctx, Document contents) throws IOException {
        Map<String, Document> docs = (Map<String, Document>)ctx.additionalContext.get("docs");
        IntervalProvider<Integer> provider = (IntervalProvider<Integer>)ctx.additionalContext.get("rangeProvider");
        
        if(contents == null){
            docs.clear();
            return;
        }
            
                
        //shard by integer ID, as we had determined in the setup
        Map<Interval<Integer>, Document> files = new HashMap<>();
        for(Element row : contents.getRootElement().getChildren("row", XFlatDatabase.xFlatNs)){
            String id = getId(row);
            int iId = Integer.parseInt(id);
            
            Document shard = files.get(provider.getInterval(iId));
            if(shard == null){
                shard = new Document();
                shard.setRootElement(new Element("db", XFlatDatabase.xFlatNs));
                files.put(provider.getInterval(iId), shard);
            }
            
            shard.getRootElement().addContent(row.clone());
        }
        
        //put the sharded documents in the docs collection
        for(Map.Entry<Interval<Integer>, Document> doc : files.entrySet()){
            File f = new File(provider.getName(doc.getKey()) + ".xml");
            docs.put(f.getName(), doc.getValue());
        }
    }

    @Override
    protected Document getFileContents(TestContext ctx) throws IOException, JDOMException {
        log.debug("getting file contents");
        
        Map<String, Document> docs = (Map<String, Document>)ctx.additionalContext.get("docs");
        IntervalProvider<Integer> provider = (IntervalProvider<Integer>)ctx.additionalContext.get("rangeProvider");
        
        Document ret = new Document();
        ret.setRootElement(new Element("db", XFlatDatabase.xFlatNs));
        
        SortedMap<Integer, Document> sortedDocs = new TreeMap<>();
        //will be spread across multiple documents, sort them by ID
        for(Map.Entry<String, Document> doc : docs.entrySet()){
            if(log.isTraceEnabled())
                log.trace(outputter.outputString(doc.getValue()));
            
            Integer i = Integer.parseInt(getShardNameFromFile(doc.getKey()));
            sortedDocs.put(i, doc.getValue());
        }
        
        //each range is now in order, add all the rows from each document
        for(Document d : sortedDocs.values()){
            for(Element e : d.getRootElement().getChildren("row", XFlatDatabase.xFlatNs)){
                ret.getRootElement().addContent(e.clone());
            }
        }
        
        if(log.isTraceEnabled())
            log.trace(outputter.outputString(ret));
        
        return ret;
    }
    
    private String getShardNameFromFile(String file){
        if(!file.endsWith(".xml"))
            throw new RuntimeException("invalid file name " + file);
        
        return file.substring(0, file.length() - 4);
    }
   
}
