/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.JDOMException;

/**
 *
 * @author Gordon
 */
public class JdomDiskLoader {

    public Document loadFrom(File file)
            throws IOException, JDOMException
    {
        if(file.exists()){
            //build the document object from memory
            return new org.jdom.input.SAXBuilder()
                    .build(file);
        }//end if exists

        return null;
    }

    public Document loadQuietly(File file){
        try {
            return loadFrom(file);
        } catch (IOException | JDOMException ex) {
            LogFactory.getLog(getClass()).warn("File " + file + " could not be read", ex);
            return null;
        }
    }

    public boolean saveTo(File file, Document doc)
            throws IOException
    {
        if(!file.exists()){
                //get the directory path
            File dir = file.getParentFile();
            if(!dir.exists())
                dir.mkdirs();
            else if (!dir.isDirectory()){
                throw new IOException("File has non-directory parent");
            }

            file.createNewFile();
        }

        org.jdom.output.XMLOutputter outputter;
        try(OutputStream outputStream = new java.io.FileOutputStream(file)) {
                //can change the output formatter to eliminate whitespace
            outputter = new org.jdom.output.XMLOutputter(org.jdom.output.Format.getPrettyFormat());
            outputter.output(doc, outputStream);
        }

        return true;
    }
}
