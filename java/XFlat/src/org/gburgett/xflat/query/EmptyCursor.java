/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.Iterator;
import org.gburgett.xflat.Cursor;

/**
 *
 * @author Gordon
 */
public class EmptyCursor {
    
    private static Cursor emptyCursor = new Cursor(){
        @Override
        public Iterator iterator() {
            return new Iterator(){
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public Object next() {
                    throw new IllegalStateException("Iterator does not have next");
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Remove not supported.");
                }
            };
        }

        @Override
        public void close() throws Exception {
            //do nothing
        }
        
    };
    
    public static <T> Cursor<T> instance(){
        return (Cursor<T>)emptyCursor;
    }
}
