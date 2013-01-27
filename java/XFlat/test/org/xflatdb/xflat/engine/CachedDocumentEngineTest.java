/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.engine;

import org.xflatdb.xflat.engine.CachedDocumentEngine;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineTestsBase;
import org.xflatdb.xflat.util.DocumentFileWrapper;
import org.xflatdb.xflat.util.FakeDocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.JDOMException;

/**
 *
 * @author gordon
 */
public class CachedDocumentEngineTest extends EngineTestsBase {

    String name = "CachedDocumentEngineTest";
    
    @Override
    protected void prepContext(TestContext ctx){
        File file = new File(ctx.workspace, name + ".xml");
        ctx.additionalContext.put("file", file);
        
        AtomicReference<Document> doc = new AtomicReference<>();
        ctx.additionalContext.put("doc", doc);
        
    }
    
    @Override
    protected EngineBase createInstance(TestContext ctx) {
        AtomicReference<Document> doc = (AtomicReference<Document>)ctx.additionalContext.get("doc");
        return new CachedDocumentEngine(new FakeDocumentFileWrapper(doc), name);
    }

    @Override
    protected void prepFileContents(TestContext ctx, Document contents) throws IOException {
        AtomicReference<Document> doc = (AtomicReference<Document>)ctx.additionalContext.get("doc");
        
        new FakeDocumentFileWrapper(doc).writeFile(contents);
    }

    @Override
    protected Document getFileContents(TestContext ctx) throws IOException, JDOMException {
        AtomicReference<Document> doc = (AtomicReference<Document>)ctx.additionalContext.get("doc");
        return new FakeDocumentFileWrapper(doc).readFile();
    }
    
    
   
}
