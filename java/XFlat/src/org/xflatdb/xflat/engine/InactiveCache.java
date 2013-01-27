/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.engine;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author gordon
 */
public class InactiveCache<T, U> implements ConcurrentMap<T, U> {
    
    @Override
    public int size() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public U get(Object key) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public U put(T key, U value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public U remove(Object key) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public void putAll(Map<? extends T, ? extends U> m) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Set<T> keySet() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Collection<U> values() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Set<Map.Entry<T, U>> entrySet() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public U putIfAbsent(T key, U value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean replace(T key, U oldValue, U newValue) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public U replace(T key, U value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

}
