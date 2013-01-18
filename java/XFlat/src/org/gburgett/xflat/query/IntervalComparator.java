/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

import java.util.Comparator;

/**
 * A Comparator that enforces ordering on {@link Interval} objects when
 * given a comparator for the Interval items.
 * @author Gordon
 */
public class IntervalComparator<T> implements Comparator<Interval<T>> {

    private final Comparator<T> itemComparer;

    public IntervalComparator(Comparator<T> itemComparer) {
        this.itemComparer = itemComparer;
    }

    @Override
    public int compare(Interval<T> o1, Interval<T> o2) {
        return Interval.compareBegin(o1, o2, itemComparer);
    }
}
