/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.range;

import org.gburgett.xflat.Range;

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
public class NumericRangeProvider {
    /**
     * Creates a RangeProvider for {@link Integer} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static RangeProvider<Integer> forInteger(final int base, final int width){
        return new RangeProvider<Integer>(){
            @Override
            public Range<Integer> getRange(Integer value) {
                
                //972 - 50 % 100 = 22, lower = 950 upper = 1050
                int diff = (value - base) % width; 
                int lower = value - diff;
                int upper = lower + width;
                
                return new NumericRange<>(lower, upper);
            }

            @Override
            public Range<Integer> nextRange(Range<Integer> currentRange, long factor) {
                NumericRange<Integer> r = (NumericRange<Integer>) currentRange;
                int lower = (int) (r.getLower() + (width * factor));
                int upper = lower + width;
                return new NumericRange<>(lower, upper);
            }
        };
    }
    /**
     * Creates a RangeProvider for {@link Long} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static RangeProvider<Long> forLong(final long base, final long width){
        return new RangeProvider<Long>(){
            @Override
            public Range<Long> getRange(Long value) {
                long diff = (value - base) % width; 
                long lower = value - diff;
                long upper = lower + width;
                
                return new NumericRange<>(lower, upper);
            }

            @Override
            public Range<Long> nextRange(Range<Long> currentRange, long factor) {
                NumericRange<Long> r = (NumericRange<Long>) currentRange;
                long lower = (r.getLower() + (width * factor));
                long upper = lower + width;
                return new NumericRange<>(lower, upper);
            }
        };
    }
    
    /**
     * Creates a RangeProvider for {@link Double} based ranges.
     * @param base The base from which ranges should be calculated.  Usually 0.
     * @param width The width of one range.
     * @return A RangeProvider providing ranges based on these settings.
     */
    public static RangeProvider<Double> forDouble(final double base, final double width){
        return new RangeProvider<Double>(){

            @Override
            public Range<Double> getRange(Double value) {
                double diff = (value - base) % width; 
                double lower = value - diff;
                double upper = lower + width;
                
                return new NumericRange<>(lower, upper);
            }

            @Override
            public Range<Double> nextRange(Range<Double> currentRange, long factor) {
                NumericRange<Double> r = (NumericRange<Double>) currentRange;
                double lower = (r.getLower() + (width * factor));
                double upper = lower + width;
                return new NumericRange<>(lower, upper);
            }
            
        };
        
    }
}
