/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

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
