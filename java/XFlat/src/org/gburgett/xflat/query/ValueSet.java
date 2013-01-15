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
public class ValueSet<T> {
    
    private List<Interval<T>> intervals = new ArrayList<>();
    
    
    public static <U> ValueSet<U> lt(U value){
        ValueSet<U> ret = new ValueSet<>();
        Interval i = new Interval<>((U)null, true, value, true);
        ret.intervals.add(i);
        return ret;
    }
    
    public static <U> ValueSet<U> lte(U value){
        ValueSet<U> ret = new ValueSet<>();
        Interval i = new Interval<>((U)null, true, value, false);
        ret.intervals.add(i);
        return ret;
    }
    
    public static <U> ValueSet<U> gt(U value){
        ValueSet<U> ret = new ValueSet<>();
        Interval i = new Interval<>(value, true, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    public static <U> ValueSet<U> gte(U value){
        ValueSet<U> ret = new ValueSet<>();
        Interval i = new Interval<>(value, false, null, true);
        ret.intervals.add(i);
        return ret;
    }
    
    public static <U> ValueSet<U> eq(U value){
        ValueSet<U> ret = new ValueSet<>();
        Interval i = new Interval<>(value, false, value, true);
        ret.intervals.add(i);
        return ret;
    }
    
    public static <U> ValueSet<U> all(U value){
        ValueSet<U> ret = new ValueSet<>();
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
    
    public ValueSet<T> union(ValueSet<T> other, final Comparator<T> comparer){
        ValueSet<T> ret = new ValueSet<>();
        
        List<Interval<T>> allIntervals = new ArrayList<>(this.intervals.size() + other.intervals.size());
        allIntervals.addAll(this.intervals);
        allIntervals.addAll(other.intervals);
        Collections.sort(allIntervals, this.sortingComparer(comparer));
        
        Iterator<Interval<T>> it = allIntervals.iterator();
        if(!it.hasNext()){
            //no intervals?
            return ret;
        }
        Interval<T> last = it.next();
        while(it.hasNext()){
            Interval<T> current = it.next();
            
            if(doesIntersect(last, current, comparer)){
                //current's lower is already > last,
                //for union we extend last's end to the greater end
                int endCompare = comparer.compare(last.end, current.end);
                //if last completely envelops current
                if(endCompare > 0 || (endCompare == 0 && (!last.endExclusive || current.endExclusive))){
                    //ignore current
                    continue;
                }
                //last must be extended
                last = new Interval<T>(last.begin, last.beginExclusive,
                        current.end, current.endExclusive);
                continue;
            }
            //does not intersect
            ret.intervals.add(last);
            last = current;
        }
        
        return ret;
    }
    
    private boolean doesIntersect(Interval<T> a, Interval<T> b, Comparator<T> comparer){
        //we can assume that a's begin is <= b's begin, so we need to see if b's begin is < a's end
        int compare = comparer.compare(a.end, b.begin);
        if(compare == 0){
            //if either is inclusive then they touch or intersect, in both cases return true
            return !a.endExclusive || !b.endExclusive;
        }
        return compare > 0;
        
    }
    
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
    
    public static class Interval<U>{
        final U begin;
        final U end;
        
        public U getBegin(){
            return begin;
        }
        
        public U getEnd(){
            return end;
        }
        
        private boolean beginExclusive;
        public boolean getBeginExclusive() {
            return this.beginExclusive;
        }
        
        private boolean endExclusive;
        public boolean getEndExclusive() {
            return this.endExclusive;
        }
        
        public Interval(U begin, boolean beginExclusive, U end, boolean endExclusive){
            this.begin = begin;
            this.end = end;
            this.beginExclusive = beginExclusive;
            this.endExclusive = endExclusive;
        }
        
        public void toString(StringBuilder sb){
            if(beginExclusive)
                sb.append('(');
            else
                sb.append('[');
            
            sb.append(begin)
                .append("...").append(end);
            
            if(beginExclusive)
                sb.append('(');
            else
                sb.append('[');
        }
    }
}
