/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Gordon
 */
public abstract class IdGeneratorTestsBase<T extends IdGenerator> {
 
    protected abstract T getInstance();
    
    
    @Test
    public void testGenerateIds_HeavyUse_GeneratesUniqueIds() throws Exception {
        System.out.println("testGenerateIds_HeavyUse_GeneratesUniqueIds");
        
        final T instance = getInstance();
        
        final Set<List<String>> ids = Collections.synchronizedSet(new HashSet<List<String>>());

        Runnable r = new Runnable(){
            @Override
            public void run() {
                List<String> idList = new ArrayList<>(500);

                for(int i = 0; i < 500; i++){
                    idList.add((String)instance.generateNewId(String.class));
                }

                ids.add(idList);
            }
        };

        Thread th1 = new Thread(r);
        Thread th2 = new Thread(r);
        Thread th3 = new Thread(r);

        String start = (String)instance.generateNewId(String.class);

        th1.start();
        th2.start();
        th3.start();
        r.run();

        th1.join();
        th2.join();
        th3.join();

        String end = (String)instance.generateNewId(String.class);


        int maxUniquifier = 0;

        Set<String> finalIds = new HashSet<>();
        for(List<String> idList : ids){
            for(String l : idList){
                assertTrue("Duplicate IDs generated: " + l, finalIds.add(l));
            }
        }

        System.out.println("Max uniquifier: " + maxUniquifier);
    }
}
