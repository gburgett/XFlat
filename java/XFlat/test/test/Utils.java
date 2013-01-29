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
package test;

import java.io.File;
import java.util.List;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class Utils {
    
    public static void deleteDir(File directory){
        for(File f : directory.listFiles()){
            if(f.isDirectory()){
                deleteDir(f);
                f.delete();
            }
            else{
                f.delete();
            }
        }
    }

    
    public static Document makeDocument(String tableName, Element... rowData){
        Document ret = new Document();
        Element root = new Element("table", XFlatDatabase.xFlatNs)
                .setAttribute("name", tableName, XFlatDatabase.xFlatNs);
        ret.setRootElement(root);   
        
        int i = 0;
        for(Element e : rowData){
            root.addContent(new Element("row", XFlatDatabase.xFlatNs)
                    .setAttribute("id", Integer.toString(i++), XFlatDatabase.xFlatNs)
                    .setContent(e));
        }
        
        return ret;
    }
    
    public static List<Element> getRows(Document doc){
        return doc.getRootElement().getChildren("row", XFlatDatabase.xFlatNs);
    }
    
}
