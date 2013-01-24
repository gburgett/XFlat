/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.TableConfig;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.PojoConverter;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineFactory;
import org.gburgett.xflat.db.ShardedEngineTestsBase;
import org.gburgett.xflat.db.TableMetadataFactory;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.Interval;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.query.NumericIntervalProvider;
import org.gburgett.xflat.query.IntervalProvider;
import org.gburgett.xflat.transaction.ThreadContextTransactionManager;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.gburgett.xflat.util.FakeDocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.xpath.XPathExpression;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.jdom2.output.XMLOutputter;

/**
 *
 * @author gordon
 */
public class IdShardedEngineTest extends ShardedEngineTestsBase<IdShardedEngine> {

    Log log = LogFactory.getLog(getClass());
    
    String name = "IdShardedEngineTest";
    
    XMLOutputter outputter = new XMLOutputter();
            
    @Override
    protected IdShardedEngine createInstance(TestContext ctx) {
        
        final Map<String, Document> docs = new ConcurrentHashMap<>();
        ctx.additionalContext.put("docs", docs);
        
        XFlatDatabase db = new XFlatDatabase(workspace, ctx.executorService);
        db.extendConversionService(new PojoConverter(){
            @Override
            public ConversionService extend(ConversionService service) {
                return conversionService;
            }

            @Override
            public XPathExpression<Object> idSelector(Class<?> clazz) {
                return null;
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
        db.setTransactionManager(ctx.transactionManager);
                
        IntervalProvider provider = NumericIntervalProvider.forInteger(1, 100);
        ctx.additionalContext.put("rangeProvider", provider);
        ShardsetConfig cfg = ShardsetConfig.create(XpathQuery.Id, Integer.class, provider);
        
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
