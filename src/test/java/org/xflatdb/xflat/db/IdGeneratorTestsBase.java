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
