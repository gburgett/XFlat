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
import java.util.concurrent.atomic.AtomicReference;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineTestsBase;
import org.xflatdb.xflat.util.FakeDocumentFileWrapper;

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
