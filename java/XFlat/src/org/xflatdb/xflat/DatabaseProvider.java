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

import java.net.URI;
import java.util.Map;

/**
 *
 * @author gordon
 */
public interface DatabaseProvider {
    
    /**
     * Indicates whether the Database Provider can provide a database satisfying
     * the given requirements.  The provided database must satisfy all requirements,
     * any requirements present in the map which are unknown should be treated as
     * unsatisfied.
     * @param uri The URI that will be managed by the database.
     * @param requirements The requirements that must be satisfied.
     * @return true if and only if the provider can construct a database that
     * satisfies every requirement.
     */
    public boolean canSatisfy(URI uri, Map<String, Object> requirements);

    /**
     * Constructs a new instance of the database provided by this provider.
     * The database must satisfy the requirements passed in to a previous call to 
     * {@link #canSatisfy(URI, java.util.Map) }.
     * @param uri The URI that the database will manage.
     * @param config The configuration of the database.
     * @param requirements The requirements that must be satisfied.
     * @param tableConfigs The configurations for specific tables in the database.
     * @return A new, initialized Database instance.
     */
    public Database construct(URI uri, DatabaseConfig config, Map<String, TableConfig> tableConfigs, Map<String, Object> requirements);
}
