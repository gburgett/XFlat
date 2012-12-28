/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineTestsBase;
import org.gburgett.xflat.util.DocumentFileWrapper;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.junit.Before;

/**
 *
 * @author gordon
 */
public class CachedDocumentEngineTest extends EngineTestsBase {

    String name = "CachedDocumentEngineTest";
    @Override
    protected EngineBase createInstance(TestContext ctx) {
        File file = new File(ctx.workspace, name + ".xml");
        ctx.additionalContext.put("file", file);
        return new CachedDocumentEngine(new DocumentFileWrapper(file), name);
    }

    @Override
    protected void prepFileContents(TestContext ctx, Document contents) throws IOException {
        File file = (File)ctx.additionalContext.get("file");
        if(contents == null){
            //ensure file doesn't exist
            if(file.exists())
                file.delete();
            
            return;
        }
        
        new DocumentFileWrapper(file).writeFile(contents);
    }

    @Override
    protected Document getFileContents(TestContext ctx) throws IOException, JDOMException {
        File file = (File)ctx.additionalContext.get("file");
        return new DocumentFileWrapper(file).readFile();
    }
    
    
   
}
