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
package org.xflatdb.xflat.query;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents one interval within an interval set.  The interval
 * is represented by two values, the begin and the end,
 * which can be inclusive or exclusive.
 * @param T The type of values representing the interval.
 */
public class Interval<T> {
    final T begin;
    final T end;

    boolean endInclusive;
    boolean beginInclusive;
    
    /**
     * Gets the begin value of this interval.  A null value means negative infinity.
     */
    public T getBegin() {
        return begin;
    }

    /**
     * Gets the end value of this interval.  A null value means positive infinity.
     */
    public T getEnd() {
        return end;
    }

    /**
     * Gets whether the interval is inclusive of the beginning.
     * True means inclusive, false means exclusive.
     */
    public boolean getBeginInclusive() {
        //Too late I realized it would have been better to do this having the booleans mean inclusive.
        //Unfortunately too much work to undo it now, fortunately I hadn't written any public facing
        //stuff based on that paradigm yet.
        return this.beginInclusive;
    }
    

    /**
     * Gets whether the interval is inclusive of the end.
     * True means inclusive, false means exclusive.
     */
    public boolean getEndInclusive() {
        return this.endInclusive;
    }

    public Interval(T begin, boolean beginInclusive, T end, boolean endInclusive) {
        this.begin = begin;
        this.end = end;
        this.beginInclusive = beginInclusive;
        this.endInclusive = endInclusive;
    }

    /**
     * Returns true iff the given value is contained by this Interval, according
     * to the given comparator.
     * @param value The value to test.
     * @param comparator The comparator used to compare values.
     * @return true if the value is contained by the Interval, false otherwise.
     */
    public boolean contains(T value, Comparator<T> comparator) {
        int lower = comparator.compare(value, begin);
        if (lower < 0) {
            return false;
        }
        if (lower == 0) {
            return this.beginInclusive;
        }
        int upper = comparator.compare(value, end);
        if (upper > 0) {
            return false;
        }
        if (upper == 0) {
            return this.endInclusive;
        }
        return true;
    }
    
    static <U> int compareBegin(Interval<U> val1, Interval<U> val2, Comparator<U> itemComparer){
        if(val1.begin == null){
            if(val2.begin == null){
                //val1 == val2;
                return compareExclusivity(val1.beginInclusive, val2.beginInclusive);
            }
            //if val2 != null, then val1 < val2 cause we're comparing beginning
            return -1;
        }
        
        if(val2.begin == null){
            //val1 is not null, so val1 is > val2
            return 1;
        }

        int ret = itemComparer.compare(val1.begin, val2.begin);
        if(ret == 0){
            return compareExclusivity(val1.beginInclusive, val2.beginInclusive);
        }
        return ret;
    }
    
    static <U> int compareEnd(Interval<U> val1, Interval<U> val2, Comparator<U> itemComparer){
        if(val1.end == null){
            if(val2.end == null){
                //val1 == val2;
                return -1 * compareExclusivity(val1.beginInclusive, val2.beginInclusive);
            }
            //if val2 != null, then val1 > val2 cause we're comparing ends
            return 1;
        }
        
        if(val2.end == null){
            //val1 is not null, so val1 is < val2
            return -1;
        }

        int ret = itemComparer.compare(val1.end, val2.end);
        if(ret == 0){
            return -1 * compareExclusivity(val1.endInclusive, val2.endInclusive);
        }
        return ret;
    }
    
    static <U> int compareEndBegin(Interval<U> val1, Interval<U> val2, Comparator<U> itemComparer){
        if(val1.end == null){
            //val2 begin cannot be +∞, so val1 end > val2 begin
            return 1;
        }
        
        if(val2.begin == null){
            //val1 end cannot be -∞, so val1 end > val2 begin
            return 1;
        }

        int ret = itemComparer.compare(val1.end, val2.begin);
        if(ret == 0){
            if(val1.endInclusive && val2.beginInclusive){
                return  0;
            }
            //else val1 is lower than val2
            return -1;
        }
        return ret;
    }
    
    private static int compareExclusivity(boolean val1Inclusive, boolean val2Inclusive){
        if(val1Inclusive){
            //if val2 is exclusive of beginning, val 1 is less
            return val2Inclusive ? 0 : -1;
        }
        //val1 is exclusive of begin, val2 is less if inclusive
        return val2Inclusive ? 1 : 0;
    }
    
    
    
    /**
     * Returns true iff the given interval intersects this interval, according
     * to the given comparator.
     * @param other The other interval to test against this interval.
     * @param comparator The comparator used to compare values.
     * @return true if the other interval intersects this one, false otherwise.
     */
    public boolean intersects(Interval<T> other, Comparator<T> comparator){
        int compare = compareBegin(this, other, comparator);
        
        if(compare <= 0){
            compare = compareEndBegin(this, other, comparator);
            if(compare < 0){
                return false;
            }
            else{
                return true;
            }
        }
        else {
            compare = compareEndBegin(other, this, comparator);
            if(compare < 0){
                return false;
            }
            else{
                return true;
            }
        }
    }
    
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.begin);
        hash = 67 * hash + Objects.hashCode(this.end);
        hash = 67 * hash + (this.beginInclusive ? 1 : 0);
        hash = 67 * hash + (this.endInclusive ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Interval<T> other = (Interval<T>) obj;
        if (!Objects.equals(this.begin, other.begin)) {
            return false;
        }
        if (!Objects.equals(this.end, other.end)) {
            return false;
        }
        if (this.beginInclusive != other.beginInclusive) {
            return false;
        }
        if (this.endInclusive != other.endInclusive) {
            return false;
        }
        return true;
    }

    /**
     * Represents this object as a string, appended to the given StringBuilder.
     * @param sb The StringBuilder to which this object's string representation
     * should be appended.
     */
    public void toString(StringBuilder sb) {
        if (beginInclusive) {
            sb.append('[');
        } else {
            sb.append('(');
        }
        if (begin != null) {
            sb.append(begin);
        } else {
            sb.append("-∞");
        }
        sb.append(", ");
        if (end != null) {
            sb.append(end);
        } else {
            sb.append("∞");
        }
        if (endInclusive) {
            sb.append(']');
        } else {
            sb.append(')');
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

}
