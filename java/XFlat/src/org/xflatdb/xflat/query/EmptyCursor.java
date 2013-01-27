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
package org.xflatdb.xflat.query;

import java.util.Iterator;
import org.xflatdb.xflat.Cursor;

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
        public void close() {
            //do nothing
        }
        
    };
    
    public static <T> Cursor<T> instance(){
        return (Cursor<T>)emptyCursor;
    }
}
