/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
