/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat;

import java.util.Comparator;

/**
 *
 * @author Gordon
 */
public interface Range<T> {
    /**
     * Determines whether the value fits within this range.
     * @param value The value to test.
     * @return true if the value fits in the range, false otherwise.
     */
    public boolean contains(T value);
    
    /**
     * Returns an integer describing whether the given value is less than,
     * greater than, or contained within the limits of the given range.
     * @param value The value to compare to this range.
     * @return 0 if (@link #contains(T ) } is true, otherwise a negative or positive
     * value indicating whether the value is less than or greater than this range.
     */
    public int compareTo(T value);
    
    /**
     * Gets a unique name for this range that will distinguish it from other ranges.
     * @return 
     */
    public String getName();
}
