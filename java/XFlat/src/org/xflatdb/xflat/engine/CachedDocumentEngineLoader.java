/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.Map;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.query.Interval;

/**
 *
 * @author Gordon
 */
public class CachedDocumentEngineLoader implements EngineLoader {

    @Override
    public Interval<Long> getRecommendedTableSize() {
        //0 bytes to 4 Mb
        return new Interval<>(0L, true, 4 * 1024L * 1024L, true);
    }

    @Override
    public boolean canSatisfy(File file, TableConfig config, Map<String, Object> requirements) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public EngineBase createEngine(File file, TableConfig config, Map<String, Object> requirements) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
