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
package org.xflatdb.xflat.db;

/**
 * Represents the progression of states in an engine as it is used.
 * @author Gordon
 */
public enum EngineState {
    /** 
     * The state of a newly instantiated engine, before it is ready. 
     * Any operations on this engine will throw.
     */
    Uninitialized, 
    /** 
     * The state of an engine that is in the process of initializing. 
     * At this point the engine can obtain a read-lock on the underlying data. 
     */
    SpinningUp, 
    /**
     * The state of an engine that has finished initializing and is waiting 
     * to be notified that it can have sole access to the underlying data.
     */
    SpunUp,
    /**
     * The state of an engine that has a write lock and sole access to the underlying
     * data store.
     */
    Running,
    /**
     * The state of an engine that is in the process of shutting down.
     * At this point the engine releases its write lock and only responds to 
     * read requests from outstanding cursors.
     */
    SpinningDown,
    /**
     * The state of an engine that has finished shutting down and is no longer
     * expecting even read operations.  Any operations on this engine will throw.
     */
    SpunDown
    
}
