/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import org.gburgett.xflat.TableConfig;
import java.io.File;
import org.gburgett.xflat.db.EngineBase;
import org.gburgett.xflat.db.EngineFactory;
import org.gburgett.xflat.engine.CachedDocumentEngine;

/**
 *
 * @author Gordon
 */
public class DefaultEngineFactory implements EngineFactory {

    @Override
    public EngineBase newEngine(File file, String tableName, TableConfig config) {
        return new CachedDocumentEngine(file, tableName);
    }
    
}
