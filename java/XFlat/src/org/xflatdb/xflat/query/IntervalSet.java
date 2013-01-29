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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Represents an immutable set of values defined in terms of several intervals.
 * The set is the union of all the intervals.
 * @author gordon
 */
public class IntervalSet<T> {
    
    private final List<Interval<T>> intervals;
    
    /**
     * Gets all the intervals in this IntervalSet in ascending order.
     * @return 
     */
    public List<Interval<T>> getIntervals(){
        return intervals;
    }
    
    private IntervalSet(Interval<T>... intervals){
        this.intervals = Arrays.asList(intervals);
    }
    
    private IntervalSet(List<Interval<T>> intervals){
        this.intervals = Collections.unmodifiableList(intervals);
    }
    
    /**
     * Creates an interval set representing all values less than the given value,
     * to negative infinity.
     * That is, the interval (-∞, value)
     * @param <U>
     * @param value The exclusive end value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> lt(U value){
        Interval<U> i = new Interval<>((U)null, false, value, false);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set representing all values less than or equal to the given value,
     * to negative infinity.
     * That is, the interval (-∞, value]
     * @param <U>
     * @param value The inclusive end value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> lte(U value){
        Interval i = new Interval<>((U)null, false, value, true);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set representing all values greater than the given value,
     * to positive infinity.
     * That is, the interval (value, ∞)
     * @param <U>
     * @param value The exclusive begin value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> gt(U value){
        Interval i = new Interval<>(value, false, null, false);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set representing all values greater than or equal to the given value,
     * to positive infinity.
     * That is, the interval [value, ∞)
     * @param <U>
     * @param value The inclusive begin value of the interval to create.
     * @return An interval set containing one interval
     */
    public static <U> IntervalSet<U> gte(U value){
        Interval i = new Interval<>(value, true, null, false);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an IntervalSet representing all values between the two given values exclusive.
     * That is, the interval (lower, upper)
     * <p/>
     * This is equivalent to {@link #gt(java.lang.Object) gt(lower)} {@link #intersection(org.xflatdb.xflat.query.IntervalSet, java.util.Comparator) ∩}
     * {@link #lt(java.lang.Object) lt(upper)}.
     * @param <U>
     * @param lower The exclusive lower bound of the interval set.
     * @param upper The inclusive upper bound of the interval set.
     * @return 
     */
    public static <U> IntervalSet<U> between(U lower, U upper){
        Interval i = new Interval<>(lower, false, upper, false);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set containing exactly one value.  
     * That is, the interval [value, value]
     * @param <U>
     * @param value The one value that should be in the interval.
     * @return an IntervalSet containing one Interval
     */
    public static <U> IntervalSet<U> eq(U value){
        Interval i = new Interval<>(value, true, value, true);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set containing all values except one.  
     * That is, the interval { (-∞, value) U (value, ∞) }
     * @param <U>
     * @param value The one value that should be in the interval.
     * @return an IntervalSet containing two Intervals
     */
    public static <U> IntervalSet<U> ne(U value){
        
        return new IntervalSet<>(
                new Interval<>(null, false, value, false),
                new Interval<>(value, false, null, false));
    }
    
    /**
     * Creates an interval set containing the entire number range.
     * That is, the interval (-∞, ∞)
     * @param <U>
     * @return 
     */
    public static <U> IntervalSet<U> all(){
        Interval i = new Interval<>(null, false, null, false);
        return new IntervalSet<>(i);
    }
    
    /**
     * Creates an interval set containing no intervals.
     * That is, the empty set.
     * @param <U>
     * @return 
     */
    public static <U> IntervalSet<U> none(){
        Interval i = new Interval<>(null, false, null, false);
        return new IntervalSet<>(i);
    }
    
    //gets a Comparator that compares based on the beginnings of intervals
    private Comparator<Interval<T>> sortingComparer(final Comparator<T> itemComparer){
        return new IntervalComparator<>(itemComparer);
    }
    
    /**
     * Gets the union of this IntervalSet with the other IntervalSet, using the given comparator.
     * The returned interval set will contain the minimum number of {@link Interval} objects
     * necessary to represent the union of this interval set with the other.
     * <p/>
     * The returned interval set is a new instance.
     * @param other The other interval set with which to union this interval set.
     * @param comparer The comparator for begin and end values of the interval set.
     * @return A new interval set containing the union.
     */
    public IntervalSet<T> union(IntervalSet<T> other, final Comparator<T> comparer){
        Comparator<Interval<T>> sorter = sortingComparer(comparer);
        
        List<Interval<T>> allIntervals = new ArrayList<>(this.intervals.size() + other.intervals.size());
        allIntervals.addAll(this.intervals);
        allIntervals.addAll(other.intervals);
        Collections.sort(allIntervals, sorter);
        
        List<Interval<T>> ret = new ArrayList<>();
        collapseIntervals(allIntervals, comparer, ret);
        
        return new IntervalSet<>(ret);
    }
    
    /**
     * Gets the intersection of this interval set with another interval set.
     * The returned interval set will contain the minimum number of {@link Interval} objects
     * necessary to represent the intersection of this interval set with the other.
     * <p/>
     * The returned interval set is a new instance.
     * @param other The other interval set which has intersecting intervals.
     * @param comparer The comparer for values of the interval set.
     * @return 
     */
    public IntervalSet<T> intersection(IntervalSet<T> other, final Comparator<T> comparer){
        Comparator<Interval<T>> sorter = sortingComparer(comparer);
        
        List<Interval<T>> temp = new ArrayList<>();
        getIntersections(this.intervals, other.intervals, sorter, comparer, temp);
        
        List<Interval<T>> ret = new ArrayList<>();
        collapseIntervals(temp, comparer, ret);
        
        return new IntervalSet<>(ret);
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

            int endCompare = Interval.compareEnd(mine, theirs, comparer);
            
            if(intersectOrTouching(first, second, comparer)){
                //intersecting interval is begin of second to end of the smaller end value
                if(endCompare <= 0){
                    addTo.add(new Interval<>(second.begin, second.beginInclusive,
                        mine.end, mine.endInclusive));
                }
                else{
                    addTo.add(new Interval<>(second.begin, second.beginInclusive,
                        theirs.end, theirs.endInclusive));
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
            
            if(intersectOrTouching(last, current, comparer)){
                //current's lower is already > last,
                //for union we extend last's end to the greater end
                int endCompare = Interval.compareEnd(last, current, comparer);
                //if last completely envelops current (equal to or greater than, comparing inclusivity)
                if(endCompare > 0 || (endCompare == 0 && (last.endInclusive || !current.endInclusive))){
                    //ignore current
                    continue;
                }
                //last must be extended cause it's greater, or it is end inclusive and current is not.
                last = new Interval<>(last.begin, last.beginInclusive,
                        current.end, current.endInclusive);
                continue;
            }
            //does not intersect
            addTo.add(last);
            last = current;
        }
        
        addTo.add(last);
    }
    
    private boolean intersectOrTouching(Interval<T> a, Interval<T> b, Comparator<T> comparer){
        //we can assume that a's begin is <= b's begin, so we need to see if b's begin is < a's end
        
        
        if(a.end == null || b.begin == null){
            //either a extends to infinity or b starts from infinity past,
            //in either case they intersect.
            return true;
        }
        
        int compare = comparer.compare(a.end, b.begin);
        if(compare == 0){
            //if either is inclusive then they touch or intersect, in both cases return true
            return a.endInclusive || b.beginInclusive;
        }
        
        return compare > 0;
        
    }

    /**
     * Tests whether this IntervalSet intersects another IntervalSet.
     * @param other The other interval set to test against
     * @param comparer The comparer comparing values.
     * @return true if any interval in this interval set intersects any interval in the other.
     */
    public boolean intersects(IntervalSet<T> other, Comparator<T> comparer){
        // O(n^2), but theres not gonna be very many in any interval set.
        // Any other algorithms would be too much overhead.
        for(int i = 0; i < this.intervals.size(); i++){
            for(int j = 0; j < other.intervals.size(); j++){
                if(this.intervals.get(i).intersects(other.intervals.get(j), comparer)){
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Tests whether this IntervalSet contains any intervals that intersect the given interval.
     * @param other The interval to test.
     * @param comparer The comparer comparing begin and end values.
     * @return true if any interval in this interval set intersects the given interval.
     */
    public boolean intersects(Interval<T> other, Comparator<T> comparer){
        for(int i = 0; i < this.intervals.size(); i++){
            if(this.intervals.get(i).intersects(other, comparer)){
                return true;
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        int size = this.intervals.size();
        hash = 59 * hash + size;
        for(int i = 0; i < size; i++){
            hash = 59 * hash + Objects.hashCode(this.intervals.get(i));
        }        
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
        final IntervalSet<T> other = (IntervalSet<T>) obj;
        
        int size = this.intervals.size();
        if(size != other.intervals.size()){
            return false;
        }
        for(int i = 0; i < size; i++){
            if(!this.intervals.get(i).equals(other.intervals.get(i))){
                return false;
            }
        }
        
        return true;
    }
    
    
    private String stringValue = null;    
    /**
     * Returns the string representation of this interval set, which is the 
     * string representation of each interval in this set concatenated with the 
     * union operator "U".
     * @return The string representation of this interval set.
     */
    @Override
    public String toString(){
        if(stringValue == null){
            StringBuilder ret = new StringBuilder();
            for(int i = 0; i < intervals.size(); i++){
                if( i > 0){
                    ret.append(" U ");
                }
                intervals.get(i).toString(ret);
            }
            stringValue = ret.toString();
        }
        
        return stringValue;
    }

    
}
