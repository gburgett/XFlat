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
     * Represents an isolation level of Snapshot.  <br/>
     * Snapshot isolation level means that operations within the transaction
     * context operate on a "snapshot" of the data taken at the time the transaction
     * was opened.  <br/>
     * When the transaction is committed, if concurrent operations have modified the
     * same rows a {@link WriteConflictException} is thrown.
     * <p/>
     * see <a href="http://en.wikipedia.org/wiki/Snapshot_isolation">http://en.wikipedia.org/wiki/Snapshot_isolation</a>
     */
    SNAPSHOT,
}
