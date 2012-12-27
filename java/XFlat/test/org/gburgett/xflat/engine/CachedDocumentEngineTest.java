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

    File file;
    DocumentFileWrapper wrapper;
    String name;
    
    @Before
    @Override
    public void setUp() throws IOException{
        super.setUp();
        
        name = "CachedDocumentEngineTest";
        file = new File(this.workspace, name + ".xml");
        wrapper = new DocumentFileWrapper(file);
    }
    
    @Override
    protected EngineBase createInstance() {
        
        return new CachedDocumentEngine(wrapper, name);
    }

    @Override
    protected void prepFileContents(Document contents) throws IOException {
        if(contents == null){
            //ensure file doesn't exist
            if(file.exists())
                file.delete();
            
            return;
        }
        
        if(!file.exists())
            file.createNewFile();
        
        wrapper.writeFile(contents);
    }

    @Override
    protected Document getFileContents() throws IOException, JDOMException {
        return wrapper.readFile();
    }
    
    
   
}
