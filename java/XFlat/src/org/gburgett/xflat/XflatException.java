/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

/**
 *
 * @author gordon
 */
public class XflatException extends RuntimeException {
    
    public XflatException(String msg){
        super(msg);
    }
    
    public XflatException(String msg, Throwable cause){
        super(msg, cause);
    }
}
