/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.query.Interval;

/**
 * An engine factory which attempts to load engines using the {@link java.util.ServiceLoader}.
 * @author Gordon
 */
public class ServiceLoaderEngineFactory implements EngineFactory {

    private List<EngineLoader> knownEngines;
    
    private ServiceLoader<EngineLoader> loader = 
            ServiceLoader.load(EngineLoader.class);
    
    private Map<String, Object> requirements;
    
    public ServiceLoaderEngineFactory(Map<String, Object> requirements){
        this.requirements = new HashMap<>(requirements);
        
        knownEngines = load();
        Collections.sort(knownEngines, new Comparator<EngineLoader>(){
            @Override
            public int compare(EngineLoader o1, EngineLoader o2) {
                return ServiceLoaderEngineFactory.this.compare(o1, o2);
            }
        });
    }
    
    private List<EngineLoader> load(){
        List<EngineLoader> ret = new ArrayList<>();
        for(EngineLoader l : loader){
            ret.add(l);
        }
        return ret;
    }
    
    /** 
     * Compare by the lower bound size, since this is the size at which it becomes
     * reasonable to use the engine.
     */
    private static int compare(EngineLoader engine1, EngineLoader engine2){
        Interval<Long> fileSize1 = engine1.getReccomendedFileSize();
        Interval<Long> fileSize2 = engine2.getReccomendedFileSize();
        Long begin1 = fileSize1.getBegin();
        Long begin2 = fileSize2.getBegin();
        
        begin1 = begin1 == null || begin1 <= 0 ? 0L : begin1;
        begin2 = begin2 == null || begin2 <= 0 ? 0L : begin2;
        
        return begin1.compareTo(begin2);
    }
    
    @Override
    public EngineBase newEngine(File file, String tableName, TableConfig config) {
        
        
        for(EngineLoader loader : knownEngines){
            if(loader.canSatisfy(file, config, requirements)){
                return loader.createEngine(file, config, requirements);
            }
        }
        
        return null;
    }
    
}
