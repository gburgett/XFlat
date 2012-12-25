/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.engine;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.jdom2.Element;

/**
 *
 * @author gordon
 */
public class InactiveCache implements ConcurrentMap<String, Element> {
    
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
    public Element get(Object key) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Element put(String key, Element value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Element remove(Object key) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public void putAll(Map<? extends String, ? extends Element> m) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Set<String> keySet() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Collection<Element> values() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Set<Map.Entry<String, Element>> entrySet() {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Element putIfAbsent(String key, Element value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public boolean replace(String key, Element oldValue, Element newValue) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

    @Override
    public Element replace(String key, Element value) {
        throw new UnsupportedOperationException("Engine is no longer active");
    }

}
