/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

/**
 * Provides a simple abstraction around {@link File} for reading and writing
 * JDOM {@link Document} documents.
 * @author gordon
 */
public class DocumentFileWrapper {
    
    private File file;
    
    private XMLOutputter outputter;
    private SAXBuilder builder;
    
    public DocumentFileWrapper(File file){
        this(file, new SAXBuilder(), new XMLOutputter());
    }
    
    public DocumentFileWrapper(File file, SAXBuilder builder, XMLOutputter outputter){
        this.file = file;
        this.outputter = outputter;
        this.builder = builder;
    }
    
    public boolean exists(){
        return file.exists();
    }
    
    public Document readFile() throws IOException, JDOMException{
        try (InputStream stream = new FileInputStream(file)){
            Document doc = builder.build(stream);
            return doc;
        }
    }
    
    public void writeFile(Document doc) throws IOException{
        try(OutputStream out = new FileOutputStream(file)) {
            outputter.output(doc, out);
        }
    }
}
