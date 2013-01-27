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
 * an EventObject for Transaction events.
 * The source is the TransactionManager that managed the transaction.
 * @author Gordon
 */
public class TransactionEventObject extends java.util.EventObject {
    /**
     * Indicates a transaction committed event
     */
    public static final int COMMITTED = 1;
    /**
     * Indicates a transaction reverted event
     */
    public static final int REVERTED = 2;
    
    private int eventType;
    /**
     * Gets the transaction event type.
     * one of {@link #COMMITTED} or {@link #REVERTED}
     * @return 
     */
    public int getEventType(){
        return eventType;
    }
    
    private Transaction tx;
    /**
     * Gets the transaction in which the event occurred.
     * @return 
     */
    public Transaction getTransaction(){
        return tx;
    }
    
    public TransactionEventObject(TransactionManager source, Transaction tx, int eventType){
        super(source);
        this.tx = tx;
        this.eventType = eventType;
    }
}
