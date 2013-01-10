/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import org.gburgett.xflat.TableConfig;
import java.io.File;

/**
 * An EngineFactory chooses which Engine to instantiate based on the given {@link File}.
 * @author Gordon
 */
public interface EngineFactory {
    /**
     * Creates a new Engine in the {@link EngineBase.EngineState#Uninitialized} state.
     * The engine should be one that is appropriate for the given table.
     * @param file A file representing one table in the database, for which an engine is
     * needed.
     * @param tableName The name of the table for which an engine is needed.
     * @param TableConfig The TableConfig for the table.
     * @return An appropriate engine for the given file. 
     */
    public EngineBase newEngine(File file, String tableName, TableConfig config);
}
