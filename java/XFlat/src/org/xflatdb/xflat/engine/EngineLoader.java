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
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.Map;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.query.Interval;


/**
 * This is a Java Service interface which allows engine implementations to be
 * automatically detected on the classpath and loaded by the {@link ServiceLoaderEngineFactory}.
 * <p/>
 * Jars which contain engine implementations must define them in a file named
 * org.xflatdb.xflat.engine.EngineLoader inside META-INF/services (see the documentation
 * for {@link java.util.ServiceLoader}).  The standard XFlat database will then
 * use them to construct Engine instances on the fly.
 * @author Gordon
 */
public interface EngineLoader {
    
    /**
     * Gets the recommended file size that can be managed by this engine.<br/>
     * This can be unbounded if not applicable, i.e. (-∞, ∞) or [0, ∞).
     * @return An interval indicating the file size recommendations, in bytes.
     */
    public Interval<Long> getRecommendedTableSize();
    
    
    /**
     * Indicates whether the Engine that is loaded by this loader can satisfy
     * the given requirements.  The provided engine must satisfy all requirements,
     * any requirements present in the map which are unknown should be treated as
     * unsatisfied.
     * @param file The directory that will be managed by the database.
     * @param requirements The requirements that must be satisfied.
     * @param config The configuration that will be applied to the table.
     * @return true if and only if the loader can construct an engine that
     * satisfies every requirement.
     */
    public boolean canSatisfy(File file, TableConfig config, Map<String, Object> requirements);
 
    /**
     * Creates an engine for the given file, which satisfies the requirements.
     * @param file The directory that will be managed by the database.
     * @param requirements The requirements that must be satisfied.
     * @param config The configuration that will be applied to the table.
     * @return A new engine which can be managed by the Local Database.
     */
    public EngineBase createEngine(File file, TableConfig config, Map<String, Object> requirements);    
}
