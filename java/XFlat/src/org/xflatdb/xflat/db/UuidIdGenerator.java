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

import java.util.UUID;

/**
 * Implements an ID generator which generates IDs using java's {@link UUID} class
 * @author gordon
 */
public class UuidIdGenerator extends IdGenerator {

    @Override
    public boolean supports(Class<?> idType) {
        return String.class.equals(idType) || 
                idType.isAssignableFrom(UUID.class);
    }

    @Override
    public Object generateNewId(Class<?> idType) {
        UUID id = UUID.randomUUID();
        
        if(idType.isAssignableFrom(UUID.class)){
            return id;
        }
        
        if(String.class.equals(idType)){
            return id.toString();
        }
        
        throw new UnsupportedOperationException("ID type " + idType + 
                " is not supported by this ID generator.");
    }

    @Override
    public String idToString(Object id) {
        if(id == null){
            return null;
        }
        
        //always either UUID or string
        return id.toString();
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        if(id == null)
            return null;
        
        if(String.class.equals(idType))
            return id;
        
        //else its a UUID
        return UUID.fromString(id);
    }
}
