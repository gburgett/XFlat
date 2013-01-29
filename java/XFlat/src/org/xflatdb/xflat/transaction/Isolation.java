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
package org.xflatdb.xflat.transaction;

/**
 * Enumerates the different transaction isolation levels supported in XFlat.
 * @author Gordon
 */
public enum Isolation {
    /**
     * Snapshot isolation level.
     * <p/>
     * see <a href="http://en.wikipedia.org/wiki/Snapshot_isolation">http://en.wikipedia.org/wiki/Snapshot_isolation</a>
     */
    SNAPSHOT,
}
