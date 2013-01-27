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
package org.xflatdb.xflat.util;

import java.io.File;
import java.io.FileInputStream;
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
        if(!this.file.exists())
            return null;
        
        try (InputStream stream = new FileInputStream(file)){
            Document doc = builder.build(stream);
            return doc;
        }
    }
    
    public Document readFile(String fileName) throws IOException, JDOMException{
        File file = new File(this.file, fileName);
        
        if(!file.exists())
            return null;
        
        try (InputStream stream = new FileInputStream(file)){
            Document doc = builder.build(stream);
            return doc;
        }
    }
    
    public void writeFile(Document doc) throws IOException{
        this.ensureDirectoryExists(file);
        
        try(OutputStream out = new FileOutputStream(file)) {
            outputter.output(doc, out);
        }
    }
    
    public void writeFile(String fileName, Document doc) throws IOException{
        File file = new File(this.file, fileName);
        
        this.ensureDirectoryExists(file);
        
        try(OutputStream out = new FileOutputStream(file)) {
            outputter.output(doc, out);
        }
    }
    
    private void ensureDirectoryExists(File file){
        if(file.exists()){
            return;
        }
        
        File parent = file.getParentFile();
        if(!parent.exists()){
            parent.mkdirs();
        }
    }
    
    @Override
    public String toString(){
        return this.file.toString();
    }
}
