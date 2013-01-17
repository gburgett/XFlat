/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.Comparator;

/**
 *
 * @author Gordon
 */
public interface IntervalProvider<T> {
    
    /**
     * Gets the range that contains the given value.
     * @param value The value for which a range is needed.
     * @return The range for that value.
     */
    public Interval<T> getInterval(T value);
    
    /**
     * Gets another range, which is a number of ranges away from the 
     * given range.
     * <p/>
     * For instance, on an integer range provider, if the current range is
     * [0, 10) and the factor is 1, the nextRange is [10, 20).<br/>
     * If the factor is -2, the nextRange is [-20, -10).
     * @param currentInterval The base range
     * @param factor The number of ranges to skip.
     * @return A new range that is factor ranges away from currentRange.
     */
    public Interval<T> nextInterval(Interval<T> currentInterval, long factor);
    
    /**
     * Returns a comparator that can compare the values provided by this range.
     * @return 
     */
    public Comparator<T> getComparator();
    
    /**
     * Gets a unique name for this interval.  This name should be unique
     * within the intervals provided by this IntervalProvider.
     * @param interval
     * @return 
     */
    public String getName(Interval<T> interval);
}
