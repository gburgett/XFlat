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

/**
 * A Comparator that enforces ordering on {@link Interval} objects when
 * given a comparator for the individual begin and end values.  The 
 * comparator enforces ordering by comparing the beginnings of intervals.
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
