/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

/**
 * Represents an action that a table can perform with an engine.
 * @param <T>
 */
public interface EngineAction<T> {

    public T act(Engine engine);
    
}
