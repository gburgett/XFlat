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
 * An object representing the options that can be applied to a transaction.
 * <p/>
 * The TransactionOptions object is immutable; all set methods return a new
 * instance that has the given option set.
 * @author Gordon
 */
public class TransactionOptions {
    
    private boolean readOnly;
    /**
     * Gets whether this transaction is Read Only.  A ReadOnly transaction
     * cannot be committed.  Write operations during a ReadOnly transaction
     * throw an exception.
     */
    public boolean getReadOnly(){
        return this.readOnly;
    }
    
    private Isolation isolation;
    /**
     * Gets the transaction isolation level.
     */
    public Isolation getIsolationLevel(){
        return this.isolation;
    }
    
    
    /**
     * Sets whether this transaction is Read Only.  A ReadOnly transaction
     * cannot be committed.  Write operations during a ReadOnly transaction
     * throw an exception.
     * <p/>
     * This method returns a new TransactionOptions object with the specified
     * option set.
     * 
     * @param readOnly true to set the transaction to read only mode.
     * @return a new TransactionOptions object with ReadOnly == the given value.
     */
    public TransactionOptions withReadOnly(boolean readOnly){
        TransactionOptions ret = new TransactionOptions(this);
        ret.readOnly = readOnly;
        return ret;
    }
    
    /**
     * Sets the isolation level of this transaction.  The isolation level determines
     * how the database applies locking to the rows.
     * <p/>
     * This method returns a new TransactionOptions object with the specified
     * option set.
     * 
     * @param level The isolation level of the transaction.
     * @return a new TransactionOptions object with IsolationLevel == the given value.
     */
    public TransactionOptions withIsolationLevel(Isolation level){
        TransactionOptions ret = new TransactionOptions(this);
        ret.isolation = level;
        return ret;
    }
    
    /**
     * Creates a new TransactionOptions object with the default options.
     */
    public TransactionOptions(){   
        this.readOnly = false;
        this.isolation = Isolation.SNAPSHOT;
    }
    
    private TransactionOptions(TransactionOptions other){
        this.readOnly = other.readOnly;
        this.isolation = other.isolation;
    }
    
    /**
     * The default transaction options.
     * <p/>
     * ReadOnly: false, <br/>
     * IsolationLevel: SNAPSHOT <br/>
     */
    public static final TransactionOptions Default = new TransactionOptions();
    
}
