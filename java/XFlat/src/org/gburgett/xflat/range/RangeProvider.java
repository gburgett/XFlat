/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.range;

import org.gburgett.xflat.Range;

/**
 *
 * @author Gordon
 */
public interface RangeProvider<T> {
    
    public Range<T> getRange(T value);
    
    public Range<T> nextRange(Range<T> currentRange, long factor);
}
