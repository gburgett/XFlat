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
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Element;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatConstants;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.db.EngineBase;
import org.xflatdb.xflat.db.EngineFactory;
import org.xflatdb.xflat.query.Interval;
import org.xflatdb.xflat.util.ComparableComparator;

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
        Interval<Long> fileSize1 = engine1.getRecommendedTableSize();
        Interval<Long> fileSize2 = engine2.getRecommendedTableSize();
        Long begin1 = fileSize1.getBegin();
        Long begin2 = fileSize2.getBegin();
        
        begin1 = begin1 == null || begin1 <= 0 ? 0L : begin1;
        begin2 = begin2 == null || begin2 <= 0 ? 0L : begin2;
        
        return begin1.compareTo(begin2);
    }
    
    @Override
    public EngineBase newEngine(File file, String tableName, TableConfig config, Element savedData) {
        
        EngineLoader currentEngine = firstEngineNamed(savedData.getAttributeValue("current", XFlatConstants.xFlatNs));
        Long size = getSize(file);
        if(currentEngine != null){
            //if it couldn't satisfy the requirements we wouldn't have used it previously,
            //so just check if the file size is outside the recommended range.
            if(currentEngine.getRecommendedTableSize().contains(size, ComparableComparator.getComparator(Long.class))){
                return currentEngine.createEngine(file, config, requirements);
            }
        }

        //need to look up a new engine
        EngineLoader newEngine = lookup(file, config, size);
        if(newEngine != null){
            savedData.setAttribute("current", newEngine.getClass().getName(), XFlatConstants.xFlatNs);
            
            return newEngine.createEngine(file, config, requirements);
        }
        
        if(currentEngine != null){
            EngineBase ret = currentEngine.createEngine(file, config, requirements);
            //even though it doesn't fit the recommended table size anymore, let's hope we can still use it.
            LogFactory.getLog(getClass()).warn(String.format("Using engine %s even though it doesn't fit table size %d because there are no other options.",
                    ret.getClass().getName(), size));
            return ret;
        }
        
        throw new XFlatException(String.format("Unable to create new engine for file %s, no engines found matching the requirements", file.getAbsolutePath()));
    }
    
    private EngineLoader firstEngineNamed(String name){
        if(name == null)
            return null;
        
        for(EngineLoader l : knownEngines){
            if(name.equals(l.getClass().getName())){
                return l;
            }
        }
        
        return null;
    }
    
    private EngineLoader lookup(File file, TableConfig config, Long size){
        for(EngineLoader l : knownEngines){
            if(l.canSatisfy(file, config, requirements)){
                if(l.getRecommendedTableSize().contains(size, ComparableComparator.getComparator(Long.class))){
                    //this one satisfies and handles the table size
                    return l;
                }
            }
        }
        
        return null;
    }
    
    private static long getSize(File file){
        if(!file.exists())
            return 0;
        
        if(file.isFile())
            return file.length();
        
        if(file.isDirectory()){
            long length = 0;
            File[] files = file.listFiles();
            for(int i = 0; i < files.length; i++){
                length += getSize(files[i]);
            }
            return length;
        }
            
        return 0;
    }
    
}
