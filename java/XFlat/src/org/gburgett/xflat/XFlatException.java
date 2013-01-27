/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

/**
 * The base class for all normal exceptions thrown by XFlat.
 * @author gordon
 */
public class XFlatException extends RuntimeException {
    
    public XFlatException(String msg){
        super(msg);
    }
    
    public XFlatException(String msg, Throwable cause){
        super(msg, cause);
    }
}
