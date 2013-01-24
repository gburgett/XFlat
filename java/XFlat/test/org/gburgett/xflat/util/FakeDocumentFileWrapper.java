/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.util.concurrent.atomic.AtomicReference;
import org.jdom2.Document;

/**
 *
 * @author Gordon
 */
public class FakeDocumentFileWrapper extends DocumentFileWrapper{
    
    private AtomicReference<Document> doc;
    
    public FakeDocumentFileWrapper(AtomicReference<Document> doc){
        super(null, null, null);
        this.doc = doc;
    }
    
    @Override
    public boolean exists(){
        return doc.get() != null;
    }
    
    @Override
    public Document readFile(){
        return doc.get();
    }
    
    @Override
    public void writeFile(Document doc){
        this.doc.set(doc);
    }
    
    @Override
    public void writeFile(String fileName, Document doc){
        throw new UnsupportedOperationException();
    }
    
    @Override
    public Document readFile(String fileName){
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String toString(){
        return "mock";
    }
}
