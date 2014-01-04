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

import org.xflatdb.xflat.util.DocumentFileWrapper;
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
