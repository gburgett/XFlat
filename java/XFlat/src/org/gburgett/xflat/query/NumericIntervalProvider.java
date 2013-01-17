/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.Comparator;
import org.gburgett.xflat.util.ComparableComparator;

/**
 * A class containing a number of factory methods for getting RangeProviders for
 * numbers.
 * 
 * Each RangeProvider provides instances of {@link NumericRange} behind the scenes.
 * The lower and upper values for the NumericRange are calculated based on the
 * width and base parameters to each function.  The base is the offset on the number line
 * from which to start, and the width is the size of each range on the number line.
 * <p/>
 * Example: <br/>
 * for base = 25 and width = 100, ranges would be the following: <br/>
 * ... [-175, -75) [-75, 25) [25, 125) [125, 225) ...
 * @author Gordon
 */
public class NumericIntervalProvider {
    
    private NumericIntervalProvider(){
        
    }
    
    /**
     * Creates a RangeProvider for {@link Integer} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static IntervalProvider<Integer> forInteger(final int base, final int width){
        return new IntervalProvider<Integer>(){
            @Override
            public Interval<Integer> getInterval(Integer value) {
                
                //972 - 50 % 100 = 22, lower = 950 upper = 1050
                int diff = (value - base) % width; 
                int lower = value - diff;
                int upper = lower + width;
                
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Interval<Integer> nextInterval(Interval<Integer> current, long factor) {
                
                int lower = (int) (current.getBegin() + (width * factor));
                int upper = lower + width;
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Comparator<Integer> getComparator() {
                return ComparableComparator.getComparator(Integer.class);
            }
            
            @Override
            public String getName(Interval<Integer> interval){
                return interval.getBegin() + "_" + interval.getEnd();
            }
        };
    }    
    
    /**
     * Creates a RangeProvider for {@link Long} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static IntervalProvider<Long> forLong(final long base, final long width){
        return new IntervalProvider<Long>(){
            @Override
            public Interval<Long> getInterval(Long value) {
                long diff = (value - base) % width; 
                long lower = value - diff;
                long upper = lower + width;
                
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Interval<Long> nextInterval(Interval<Long> current, long factor) {
                long lower = (current.getBegin() + (width * factor));
                long upper = lower + width;
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Comparator<Long> getComparator() {
                return ComparableComparator.getComparator(Long.class);
            }
            
            @Override
            public String getName(Interval<Long> interval){
                return interval.getBegin() + "_" + interval.getEnd();
            }
        };
    }
    
    /**
     * Creates a RangeProvider for {@link Double} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static IntervalProvider<Double> forDouble(final double base, final double width){
        return new IntervalProvider<Double>(){

            @Override
            public Interval<Double> getInterval(Double value) {
                double diff = (value - base) % width; 
                double lower = value - diff;
                double upper = lower + width;
                
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Interval<Double> nextInterval(Interval<Double> current, long factor) {
                double lower = (current.getBegin() + (width * factor));
                double upper = lower + width;
                return new Interval<>(lower, true, upper, false);
            }

            @Override
            public Comparator<Double> getComparator() {
                return ComparableComparator.getComparator(Double.class);
            }
            
            @Override
            public String getName(Interval<Double> interval){
                return interval.getBegin() + "_" + interval.getEnd();
            }
        };
        
    }
}
