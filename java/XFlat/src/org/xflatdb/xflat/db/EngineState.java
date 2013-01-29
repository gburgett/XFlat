/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

/**
 * Represents the progression of states in an engine as it is used.
 * @author Gordon
 */
public enum EngineState {
    /** 
     * The state of a newly instantiated engine, before it is ready. 
     * Any operations on this engine will throw.
     */
    Uninitialized, 
    /** 
     * The state of an engine that is in the process of initializing. 
     * At this point the engine can obtain a read-lock on the underlying data. 
     */
    SpinningUp, 
    /**
     * The state of an engine that has finished initializing and is waiting 
     * to be notified that it can have sole access to the underlying data.
     */
    SpunUp,
    /**
     * The state of an engine that has a write lock and sole access to the underlying
     * data store.
     */
    Running,
    /**
     * The state of an engine that is in the process of shutting down.
     * At this point the engine releases its write lock and only responds to 
     * read requests from outstanding cursors.
     */
    SpinningDown,
    /**
     * The state of an engine that has finished shutting down and is no longer
     * expecting even read operations.  Any operations on this engine will throw.
     */
    SpunDown
    
}
