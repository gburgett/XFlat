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
package org.xflatdb.xflat;

/**
 * Thrown when an operation violates some constraint of the underlying data store.
 * These exceptions are indicative of some problem with the data being manipulated,
 * rather than a configuration or IO error
 * @author gordon
 */
public class XFlatDataException extends Exception {

    /**
     * Creates a new instance of
     * <code>XFlatDataException</code> without detail message.
     */
    public XFlatDataException(String msg, Throwable cause) {
    }

    /**
     * Constructs an instance of
     * <code>XFlatDataException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public XFlatDataException(String msg) {
        super(msg);
    }
}
