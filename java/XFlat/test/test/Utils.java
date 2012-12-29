/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.File;
import java.util.List;
import org.gburgett.xflat.db.XFlatDatabase;
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
