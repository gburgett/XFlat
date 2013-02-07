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
 * An object representing the options that can be applied to a transaction. <br/>
 * Note that these options may be ignored if a currently open transaction is
 * propagated.  Control this with the {@link #getPropagation() Propagation} option.
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
    
    private Propagation propagation;
    
    
    public Propagation getPropagation(){
        return propagation;
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
     * Sets the propagation behavior to apply when opening this transaction.
     * The propagation behavior determines how the transaction manager should
     * react to the current transaction scope when creating this transaction.
     * 
     * @param propagation The propagation to apply when this transaction is opened.
     * @return a new TransactionOptions object with Propagation == the given value.
     */
    public TransactionOptions withPropagation(Propagation propagation) {
        TransactionOptions ret = new TransactionOptions(this);
        ret.propagation = propagation;
        return ret;
    }
    
    /**
     * Creates a new TransactionOptions object with the default options.
     */
    public TransactionOptions(){   
        this.readOnly = false;
        this.isolation = Isolation.SNAPSHOT;
        this.propagation = Propagation.REQUIRED;
    }
    
    private TransactionOptions(TransactionOptions other){
        this.readOnly = other.readOnly;
        this.isolation = other.isolation;
        this.propagation = other.propagation;
    }
    
    /**
     * Gets the default transaction options.  Equivalent to instantiating
     * a new instance.
     */
    public static TransactionOptions DEFAULT = new TransactionOptions();

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.readOnly ? 1 : 0);
        hash = 97 * hash + (this.isolation != null ? this.isolation.hashCode() : 0);
        hash = 97 * hash + (this.propagation != null ? this.propagation.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TransactionOptions other = (TransactionOptions) obj;
        if (this.readOnly != other.readOnly) {
            return false;
        }
        if (this.isolation != other.isolation) {
            return false;
        }
        if (this.propagation != other.propagation) {
            return false;
        }
        return true;
    }
}
