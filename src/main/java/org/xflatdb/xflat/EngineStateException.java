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

import org.xflatdb.xflat.db.EngineState;

/**
 * An XflatException thrown when the engine is in an unusable state for the 
 * current operation.
 * @author gordon
 */
public class EngineStateException extends XFlatException {

    private final EngineState state;
    public EngineState getEngineState(){
        return state;
    }

    /**
     * Constructs an instance of
     * <code>EngineStateException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public EngineStateException(String msg, EngineState state) {
        super(msg);
        this.state = state;
    }
}
