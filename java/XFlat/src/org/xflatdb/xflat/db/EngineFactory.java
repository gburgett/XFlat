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

import java.io.File;
import org.xflatdb.xflat.TableConfig;

/**
 * An EngineFactory chooses which Engine to instantiate based on the given {@link File}.
 * @author Gordon
 */
public interface EngineFactory {
    /**
     * Creates a new Engine in the {@link EngineState#Uninitialized} state.
     * The engine should be one that is appropriate for the given table.
     * @param file A file representing one table in the database, for which an engine is
     * needed.
     * @param tableName The name of the table for which an engine is needed.
     * @param config The TableConfig for the table.
     * @return An appropriate engine for the given file. 
     */
    public EngineBase newEngine(File file, String tableName, TableConfig config);
}
