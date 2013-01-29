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

/**
 * This exception is thrown when a query is invalid based on one or more of
 * the indexes on a table.  
 * <p/>
 * Indexes generally ensure that the selected value
 * is of a certain type.  If a query then expects the value to be a different type,
 * then this exception is generated.
 * @author Gordon
 */
public class InvalidQueryException extends RuntimeException {
    
    private final XPathQuery query;
    /**
     * Gets the query that was invalid.
     * @return The query that caused the exception.
     */
    public XPathQuery getQuery(){
        return query;
    }
    
    private final Class<?> indexType;
    /**
     * Gets the type of the index.
     * @return the class representing the index's type.
     */
    public Class<?> getIndexType(){
        return indexType;
    }
    
    public InvalidQueryException(XPathQuery q, Class<?> indexType){
        super(String.format("Query %s expected value to be type %s, but index requires value to be %s", q.toString(), q.getValueType().toString(), indexType.toString()));
        
        this.query = q;
        this.indexType = indexType;
    }
}
