/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents one interval within an interval set.  The interval
 * is represented by two values, the begin and the end,
 * which can be inclusive or exclusive.
 * @param <U>
 */
public class Interval<U> {
    final U begin;
    final U end;

    boolean endInclusive;
    boolean beginInclusive;
    
    /**
     * Gets the begin value of this interval.  A null value means negative infinity.
     * @return
     */
    public U getBegin() {
        return begin;
    }

    /**
     * Gets the end value of this interval.  A null value means positive infinity.
     * @return
     */
    public U getEnd() {
        return end;
    }

    /**
     * Gets whether the interval is inclusive of the beginning.
     * True means inclusive, false means exclusive.
     * @return
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
     * @return
     */
    public boolean getEndInclusive() {
        return this.endInclusive;
    }

    public Interval(U begin, boolean beginInclusive, U end, boolean endInclusive) {
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
    public boolean contains(U value, Comparator<U> comparator) {
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
        final Interval<U> other = (Interval<U>) obj;
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
