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
package org.xflatdb.xflat.engine;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A concurrent map used as a cache in the {@link CachedDocumentEngine} when
 * it is no longer active.
 * @author gordon
 */
class InactiveCache<T, U> implements ConcurrentMap<T, U> {
    
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
