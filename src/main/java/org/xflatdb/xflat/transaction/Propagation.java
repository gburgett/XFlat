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
 * Represents the different propagation behavior for a transaction. <br/>
 * Propagation defines the behavior of {@link TransactionManager#openTransaction() }
 * when a transaction already exists.
 * <p/>
 * see http://static.springsource.org/spring/docs/3.2.x/javadoc-api/
 * @author Gordon
 */
public enum Propagation {
    /**  Support a current transaction, throw an exception if none exists. */
    MANDATORY,
    
    /** Execute within a nested transaction if a current transaction exists, behave like REQUIRED else. */
    NESTED,
    
    /** Execute non-transactionally, throw an exception if a transaction exists. */
    NEVER,
    
    /** Execute non-transactionally, suspend the current transaction if one exists. */
    NOT_SUPPORTED,
    
    /** Support a current transaction, create a new one if none exists.  
     * This is the default propagation behavior.
     */
    REQUIRED,
    
    /** Create a new transaction, suspend the current transaction if one exists. Analogous to EJB transaction attribute of the same name. */
    REQUIRES_NEW,
    
    /** Support a current transaction, execute non-transactionally if none exists. */
    SUPPORTS
}
