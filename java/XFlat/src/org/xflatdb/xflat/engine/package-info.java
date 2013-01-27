/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * This package contains the implementations of {@link org.gburgett.xflat.db.Engine} that are provided
 * by XFlat out of the box.  Custom engines can be easily added by extending
 * {@link org.gburgett.xflat.db.EngineBase} and specifying a different {@link org.gburgett.xflat.db.EngineFactory},
 * in the Database configuration, but these engines should be suitable for most needs.
 * The DefaultEngineFactory chooses the best engine for the file among these.
 */
package org.xflatdb.xflat.engine;
