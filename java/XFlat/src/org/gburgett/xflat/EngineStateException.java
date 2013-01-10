/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import org.gburgett.xflat.db.EngineState;

/**
 *
 * @author gordon
 */
public class EngineStateException extends XflatException {

    private final EngineState state;
    public EngineState getEngineState(){
        return state;
    }

    /**
     * Constructs an instance of
     * <code>EngineStateException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public EngineStateException(String msg, EngineState state) {
        super(msg);
        this.state = state;
    }
}
