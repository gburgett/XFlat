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
package org.xflatdb.xflat.util;

import java.util.Comparator;

/**
 * A Comparator that compares Comparable values.
 * This class allows comparison logic to be injected, even for classes that already
 * implement comparable.
 * It expects that the values are not null, since there is no well defined comparison
 * for null values.
 * @author Gordon
 */
public class ComparableComparator<T extends Comparable<T>> implements Comparator<T> {
    
    @Override
    public int compare(T o1, T o2) {
        return o1.compareTo(o2);
    }
    
    private static ComparableComparator singleton = new ComparableComparator();
    
    /**
     * Gets the singleton comparator instance typed as needed.
     * @param <U>
     * @return 
     */
    public static <U extends Comparable<U>> ComparableComparator<U> getComparator(Class<U> clazz){
        return singleton;
    };
}
