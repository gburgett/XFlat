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
import org.gburgett.xflat.Range;
import org.gburgett.xflat.ShardsetConfig;
import org.gburgett.xflat.TableConfig;
import org.gburgett.xflat.convert.ConversionService;
import org.gburgett.xflat.convert.PojoConverter;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineFactory;
import org.gburgett.xflat.db.ShardedEngineTestsBase;
import org.gburgett.xflat.db.TableMetadataFactory;
import org.gburgett.xflat.db.XFlatDatabase;
import org.gburgett.xflat.query.XpathQuery;
import org.gburgett.xflat.range.NumericRangeProvider;
import org.gburgett.xflat.range.RangeProvider;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.xpath.XPathExpression;
import static org.junit.Assert.*;

/**
 *
 * @author gordon
 */
public class IdShardedEngineTest extends ShardedEngineTestsBase<IdShardedEngine> {

    String name = "IdShardedEngineTest";
    @Override
    protected IdShardedEngine createInstance(TestContext ctx) {
        
        final Map<File, Document> docs = new ConcurrentHashMap<>();
        ctx.additionalContext.put("docs", docs);
        
        XFlatDatabase db = new XFlatDatabase(workspace, executorService);
        db.extendConversionService(new PojoConverter(){
            @Override
            public ConversionService extend(ConversionService service) {
                return conversionService;
            }

            @Override
            public XPathExpression<?> idSelector(Class<?> clazz) {
                return null;
            }
        });
        db.setEngineFactory(new EngineFactory(){
            @Override
            public EngineBase newEngine(final File file, String tableName, TableConfig config) {
                DocumentFileWrapper wrapper = new DocumentFileWrapper(file){
                    @Override
                    public Document readFile(){
                        return docs.get(file);
                    }
                    
                    @Override
                    public void writeFile(Document doc){
                        docs.put(file, doc);
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
        
        RangeProvider provider = NumericRangeProvider.forInteger(1, 100);
        ctx.additionalContext.put("rangeProvider", provider);
        ShardsetConfig cfg = ShardsetConfig.create(XpathQuery.Id, Integer.class, provider);
        
        File file = new File(ctx.workspace, name);
        IdShardedEngine ret = new IdShardedEngine(file, name, cfg);
        setMetadataFactory(ret, new TableMetadataFactory(db, file));
        return ret;
    }

    @Override
    protected void prepFileContents(TestContext ctx, Document contents) throws IOException {
        Map<File, Document> docs = (Map<File, Document>)ctx.additionalContext.get("docs");
        RangeProvider<Integer> provider = (RangeProvider<Integer>)ctx.additionalContext.get("rangeProvider");
        
        if(contents == null){
            docs.clear();
            return;
        }
            
                
        //shard by integer ID, as we had determined in the setup
        Map<Range<Integer>, Document> files = new HashMap<>();
        for(Element row : contents.getRootElement().getChildren("row", XFlatDatabase.xFlatNs)){
            String id = getId(row);
            int iId = Integer.parseInt(id);
            
            Document shard = files.get(provider.getRange(iId));
            if(shard == null){
                shard = new Document();
                shard.setRootElement(new Element("db", XFlatDatabase.xFlatNs));
                files.put(provider.getRange(iId), shard);
            }
            
            shard.getRootElement().addContent(row.detach());
        }
        
        //put the sharded documents in the docs collection
        for(Map.Entry<Range<Integer>, Document> doc : files.entrySet()){
            File f = new File(doc.getKey().getName() + ".xml");
            docs.put(f, doc.getValue());
        }
    }

    @Override
    protected Document getFileContents(TestContext ctx) throws IOException, JDOMException {
        Map<File, Document> docs = (Map<File, Document>)ctx.additionalContext.get("docs");
        RangeProvider<Integer> provider = (RangeProvider<Integer>)ctx.additionalContext.get("rangeProvider");
        
        Document ret = new Document();
        ret.setRootElement(new Element("db", XFlatDatabase.xFlatNs));
        
        SortedMap<Integer, Document> sortedDocs = new TreeMap<>();
        //will be spread across multiple documents, sort them by ID
        for(Map.Entry<File, Document> doc : docs.entrySet()){
            Integer i = Integer.parseInt(doc.getKey().getName().split("_")[0]);
            sortedDocs.put(i, doc.getValue());
        }
        
        //each range is now in order, add all the rows from each document
        for(Document d : sortedDocs.values()){
            for(Element e : d.getRootElement().getChildren("row", XFlatDatabase.xFlatNs)){
                ret.getRootElement().addContent(e.detach());
            }
        }
        
        return ret;
    }
    
    
   
}
