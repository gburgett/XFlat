/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a set of values defined in terms of several ranges.
 * The set is the union of all the ranges.
 * @author gordon
 */
public class IntervalSet<T> {
    
    private List<Interval<T>> intervals = new ArrayList<>();
    
    /**
     * Creates an interval set representing all values less than the given value,
     * to negative infinity.
     * That is, the set (-∞, value)
     * @param <U>
     * @param value The exclusive end value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> lt(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>((U)null, true, value, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set representing all values less than or equal to the given value,
     * to negative infinity.
     * That is, the set (-∞, value]
     * @param <U>
     * @param value The inclusive end value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> lte(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>((U)null, true, value, false);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set representing all values greater than the given value,
     * to positive infinity.
     * That is, the set (value, ∞)
     * @param <U>
     * @param value The exclusive begin value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> gt(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(value, true, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set representing all values greater than or equal to the given value,
     * to positive infinity.
     * That is, the set [value, ∞)
     * @param <U>
     * @param value The inclusive begin value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> gte(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(value, false, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an IntervalSet representing all values between the two given values exclusive.
     * That is, the set (lower, upper)
     * <p/>
     * This is equivalent to {@link #gt(java.lang.Object) gt(lower)} {@link #intersection(org.gburgett.xflat.query.IntervalSet, java.util.Comparator) ∩}
     * {@link #lt(java.lang.Object) lt(upper)}.
     * @param <U>
     * @param lower The exclusive lower bound of the interval set.
     * @param upper The inclusive upper bound of the interval set.
     * @return 
     */
    public static <U> IntervalSet<U> between(U lower, U upper){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(lower, true, upper, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set containing exactly one value.  
     * That is, the interval [value, value]
     * @param <U>
     * @param value The one value that should be in the interval.
     * @return an IntervalSet containing one Interval
     */
    public static <U> IntervalSet<U> eq(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(value, false, value, false);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set containing all values except one.  
     * That is, the set of intervals { (-∞, value) U (value, ∞) }
     * @param <U>
     * @param value The one value that should be in the interval.
     * @return an IntervalSet containing two Intervals
     */
    public static <U> IntervalSet<U> ne(U value){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(null, true, value, true);
        ret.intervals.add(i);
        i = new Interval<>(value, true, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set containing the entire number range.
     * That is, the set (-∞, ∞)
     * @param <U>
     * @return 
     */
    public static <U> IntervalSet<U> all(){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(null, true, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    /**
     * Creates an interval set containing no intervals.
     * That is, the empty set.
     * @param <U>
     * @return 
     */
    public static <U> IntervalSet<U> none(){
        IntervalSet<U> ret = new IntervalSet<>();
        Interval i = new Interval<>(null, true, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    
    private Comparator<Interval<T>> sortingComparer(final Comparator<T> itemComparer){
        return new Comparator<Interval<T>>(){
            @Override
            public int compare(Interval<T> o1, Interval<T> o2) {
                if(o1.begin == null){
                    //o1 < o2 ? -1 : 0
                    return o2.begin == null ? 0 : -1;
                }
                if(o2.begin == null){
                    //o1 > o2
                    return 1;
                }
                
                int ret = itemComparer.compare(o1.begin, o2.begin);
                if(ret == 0){
                    //gotta take exclusivity into account
                    if(o1.beginExclusive){
                        //if o1 is exclusive, then o2 is before o1 if it is inclusive
                        return o2.beginExclusive ? 0 : 1;
                    }
                    //if o1 is inclusive, then o1 is before o2 if it is exclusive
                    return o2.beginExclusive ? -1 : 0;
                }
                return ret;
            }
        };
    }
    
    private int compareEnds(Interval<T> val1, Interval<T> val2, Comparator<T> comparer){
        
        if(val1.end == null){
            if(val2.end == null){
                //val1 == val2;
                return compareEndExclusivity(val1.endExclusive, val2.endExclusive);
            }
            //if val2 != null, then val1 > val2 cause we're comparing ends
            return 1;
        }
        
        if(val2.end == null){
            //val1 is not null, so val1 is < val2
            return -1;
        }
        
        int ret = comparer.compare(val1.end, val2.end);
        if(ret == 0){
            return compareEndExclusivity(val1.endExclusive, val2.endExclusive);
        }
        return ret;
    }
    
    private int compareEndExclusivity(boolean val1Exclusive, boolean val2Exclusive){
        if(val1Exclusive){
            //if val2 is inclusive of end, val 1 is smaller
            return val2Exclusive ? 0 : -1;
        }
        //val1 is inclusive of end, val2 is smaller if exclusive
        return val2Exclusive ? 1 : 0;
    }
    
    /**
     * Gets the union of this IntervalSet with the other IntervalSet, using the given comparer.
     * The returned interval set is a new instance.
     * @param other The other interval set with which to union this interval set.
     * @param comparer The comparer for values of the interval set.
     * @return A new interval set containing the union.
     */
    public IntervalSet<T> union(IntervalSet<T> other, final Comparator<T> comparer){
        IntervalSet<T> ret = new IntervalSet<>();
        
        List<Interval<T>> allIntervals = new ArrayList<>(this.intervals.size() + other.intervals.size());
        allIntervals.addAll(this.intervals);
        allIntervals.addAll(other.intervals);
        Collections.sort(allIntervals, this.sortingComparer(comparer));
        
        collapseIntervals(allIntervals, comparer, ret.intervals);
        
        return ret;
    }
    
    /**
     * Gets the intersection of this interval set with another interval set.
     * The returned interval set is a new instance.
     * @param other The other interval set which has intersecting intervals.
     * @param comparer The comparer for values of the interval set.
     * @return 
     */
    public IntervalSet<T> intersection(IntervalSet<T> other, final Comparator<T> comparer){
        IntervalSet<T> ret = new IntervalSet<>();
        
        Comparator<Interval<T>> sorter = sortingComparer(comparer);
        Collections.sort(this.intervals, sorter);
        Collections.sort(other.intervals, sorter);
        
        List<Interval<T>> temp = new ArrayList<>();
        getIntersections(this.intervals, other.intervals, sorter, comparer, temp);
        
        collapseIntervals(temp, comparer, ret.intervals);
        
        return ret;
    }
    
    private void getIntersections(Iterable<Interval<T>> myIntervals, Iterable<Interval<T>> theirIntervals, Comparator<Interval<T>> sorter, Comparator<T> comparer, List<Interval<T>> addTo){
        Iterator<Interval<T>> mineIterator = myIntervals.iterator();
        Iterator<Interval<T>> theirsIterator = theirIntervals.iterator();
        if(!mineIterator.hasNext() || !theirsIterator.hasNext()){
            //intersection with nothing is nothing
            return;
        }
        
        Interval<T> mine = mineIterator.next();
        Interval<T> theirs = theirsIterator.next();
        do{
            int compare = sorter.compare(mine, theirs);
            Interval<T> first, second;
            if(compare <= 0){
               first = mine;
               second = theirs;
            }
            else{
                //theirs is before mine
                first = theirs;
                second = mine;
            }

            int endCompare = compareEnds(mine, theirs, comparer);
            
            if(doesIntersect(first, second, comparer)){
                //intersecting interval is begin of second to end of the smaller end value
                if(endCompare <= 0){
                    addTo.add(new Interval<>(second.begin, second.beginExclusive,
                        mine.end, mine.endExclusive));
                }
                else{
                    addTo.add(new Interval<>(second.begin, second.beginExclusive,
                        theirs.end, theirs.endExclusive));
                }
            }
            
            
            
            //advance the one with the smaller endValue, or both if equal
            if(endCompare <= 0){
                if(!mineIterator.hasNext())
                    break;
                
                mine = mineIterator.next();
            }
            
            if(endCompare > 0){
                if(!theirsIterator.hasNext())
                    break;
                
                theirs = theirsIterator.next();
            }
            
        }while(true);
        
        //no more intersections once one is out
    }
    
    private void collapseIntervals(Iterable<Interval<T>> allIntervals, Comparator<T> comparer, List<Interval<T>> addTo){
        Iterator<Interval<T>> it = allIntervals.iterator();
        if(!it.hasNext()){
            //no intervals?
            return;
        }
        Interval<T> last = it.next();
        while(it.hasNext()){
            Interval<T> current = it.next();
            
            if(doesIntersect(last, current, comparer)){
                //current's lower is already > last,
                //for union we extend last's end to the greater end
                int endCompare = compareEnds(last, current, comparer);
                //if last completely envelops current
                if(endCompare > 0 || (endCompare == 0 && (!last.endExclusive || current.endExclusive))){
                    //ignore current
                    continue;
                }
                //last must be extended cause it's greater, or it is end inclusive and current is not.
                last = new Interval<>(last.begin, last.beginExclusive,
                        current.end, current.endExclusive);
                continue;
            }
            //does not intersect
            addTo.add(last);
            last = current;
        }
        
        addTo.add(last);
    }
    
    private boolean doesIntersect(Interval<T> a, Interval<T> b, Comparator<T> comparer){
        //we can assume that a's begin is <= b's begin, so we need to see if b's begin is < a's end
        
        
        if(a.end == null || b.begin == null){
            //either a extends to infinity or b starts from infinity past,
            //in either case they intersect.
            return true;
        }
        
        int compare = comparer.compare(a.end, b.begin);
        if(compare == 0){
            //if either is inclusive then they touch or intersect, in both cases return true
            return !a.endExclusive || !b.beginExclusive;
        }
        
        return compare > 0;
        
    }
    
    @Override
    public String toString(){
        StringBuilder ret = new StringBuilder();
        for(int i = 0; i < intervals.size(); i++){
            if( i > 0){
                ret.append(" U ");
            }
            intervals.get(i).toString(ret);
        }
        
        return ret.toString();
    }
    
    /**
     * Represents one interval within an interval set.  The interval
     * is represented by two values, the begin and the end,
     * which can be inclusive or exclusive.
     * @param <U> 
     */
    public static class Interval<U>{
        private final U begin;
        private final U end;
        
        /**
         * Gets the begin value of this interval.  A null value means negative infinity.
         * @return 
         */
        public U getBegin(){
            return begin;
        }
        
        /**
         * Gets the end value of this interval.  A null value means positive infinity.
         * @return 
         */
        public U getEnd(){
            return end;
        }
        
        private boolean beginExclusive;
        /**
         * Gets whether the interval is inclusive of the beginning.
         * True means inclusive, false means exclusive.
         * @return 
         */
        public boolean getBeginInclusive() {
            //Too late I realized it would have been better to do this having the booleans mean inclusive.
            //Unfortunately too much work to undo it now, fortunately I hadn't written any public facing
            //stuff based on that paradigm yet.
            return !this.beginExclusive;
        }
        
        private boolean endExclusive;
        /**
         * Gets whether the interval is inclusive of the end.
         * True means inclusive, false means exclusive.
         * @return 
         */
        public boolean getEndInclusive() {
            return !this.endExclusive;
        }
        
        public Interval(U begin, boolean beginExclusive, U end, boolean endExclusive){
            this.begin = begin;
            this.end = end;
            this.beginExclusive = beginExclusive;
            this.endExclusive = endExclusive;
        }
        
        /**
         * Returns true iff the given value is contained by this Interval, according
         * to the given comparator.
         * @param value The value to test.
         * @param comparator The comparator used to compare values.
         * @return true if the value is contained by the Interval, false otherwise.
         */
        public boolean contains(U value, Comparator<U> comparator){
            int lower = comparator.compare(value, begin);
            if(lower < 0){
                return false;
            }
            if(lower == 0){
                return !this.beginExclusive;
            }
            
            int upper = comparator.compare(value, end);
            if(upper > 0){
                return false;
            }
            if(upper == 0){
                return !this.endExclusive;
            }
            
            return true;
        }
        
        public void toString(StringBuilder sb){
            if(beginExclusive)
                sb.append('(');
            else
                sb.append('[');
            
            if(begin != null)
                sb.append(begin);
            else
                sb.append("-∞");
            
            sb.append(", ");
            
            if(end != null)
                sb.append(end);
            else
                sb.append("∞");
            
            if(endExclusive)
                sb.append(')');
            else
                sb.append(']');
        }
        
        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder();
            toString(sb);
            return sb.toString();
        }
    }
}
