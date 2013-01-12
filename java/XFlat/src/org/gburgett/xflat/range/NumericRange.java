/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.range;

import java.util.Objects;
import org.gburgett.xflat.Range;

/**
 *
 * @author Gordon
 */
class NumericRange<T extends Comparable<T>> implements Range<T> {
    private final T lower;
    private final T upper;
    
    private String name;

    public T getLower() {
        return lower;
    }

    public T getUpper() {
        return upper;
    }

    public NumericRange(T lower, T upper) {
        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public boolean contains(T value) {
        return value.compareTo(lower) >= 0 && value.compareTo(upper) < 0;
    }

    @Override
    public int compareTo(T value) {
        if (value.compareTo(lower) < 0) {
            return -1;
        }
        if (value.compareTo(upper) >= 0) {
            return 1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.lower);
        hash = 67 * hash + Objects.hashCode(this.upper);
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
        final NumericRange<T> other = (NumericRange<T>) obj;
        if (!Objects.equals(this.lower, other.lower)) {
            return false;
        }
        if (!Objects.equals(this.upper, other.upper)) {
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        if(this.name == null){
            this.name = this.lower.toString() + '_' + this.upper.toString();
        }
        
        return this.name;
    }
    
}
