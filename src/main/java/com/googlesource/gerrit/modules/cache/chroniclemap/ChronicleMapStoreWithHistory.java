// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.modules.cache.chroniclemap;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.util.SerializableFunction;
import net.openhft.chronicle.hash.Data;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ExternalMapQueryContext;
import net.openhft.chronicle.map.MapEntry;
import net.openhft.chronicle.map.MapSegmentContext;

class ChronicleMapStoreWithHistory<K, V> implements ChronicleMap<K, V> {

  private final ChronicleMap<K, V> store;
  private final CacheKeysHistory<K> keysHistory;

  ChronicleMapStoreWithHistory(ChronicleMap<K, V> store) {
    this.store = store;
    this.keysHistory = new CacheKeysHistory<>();
  }

  CacheKeysHistory<K> keysHistory() {
    return keysHistory;
  }

  @Override
  public boolean isClosing() {
    return store.isClosing();
  }

  @Override
  public boolean isClosed() {
    return store.isClosed();
  }

  @Override
  public void throwExceptionIfClosed() throws IllegalStateException {
    store.throwExceptionIfClosed();
  }

  @Override
  public File file() {
    return store.file();
  }

  @Override
  public void notifyClosing() {
    store.notifyClosing();
  }

  @Override
  public String name() {
    return store.name();
  }

  @Override
  public String toIdentityString() {
    return store.toIdentityString();
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return store.getOrDefault(key, defaultValue);
  }

  @Override
  public V get(Object key) {
    return store.get(key);
  }

  @Override
  public long longSize() {
    return store.longSize();
  }

  @Override
  public long offHeapMemoryUsed() {
    return store.offHeapMemoryUsed();
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    store.forEach(action);
  }

  @Override
  public Class<K> keyClass() {
    return store.keyClass();
  }

  @Override
  public V getUsing(K key, V usingValue) {
    return store.getUsing(key, usingValue);
  }

  @Override
  public Type keyType() {
    return store.keyType();
  }

  @Override
  public ExternalMapQueryContext<K, V, ?> queryContext(K key) {
    return store.queryContext(key);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return store.putIfAbsent(key, value);
  }

  @Override
  public ExternalMapQueryContext<K, V, ?> queryContext(Data<K> key) {
    return store.queryContext(key);
  }

  @Override
  public V acquireUsing(K key, V usingValue) {
    return store.acquireUsing(key, usingValue);
  }

  @Override
  public ExternalMapQueryContext<K, V, ?> queryContext(
      BytesStore keyBytes, long offset, long size) {
    return store.queryContext(keyBytes, offset, size);
  }

  @Override
  public boolean remove(Object key, Object value) {
    return store.remove(key, value);
  }

  @Override
  public MapSegmentContext<K, V, ?> segmentContext(int segmentIndex) {
    return store.segmentContext(segmentIndex);
  }

  @Override
  public int segments() {
    return store.segments();
  }

  @Override
  public boolean forEachEntryWhile(Predicate<? super MapEntry<K, V>> predicate) {
    return store.forEachEntryWhile(predicate);
  }

  @Override
  public Closeable acquireContext(K key, V usingValue) {
    return store.acquireContext(key, usingValue);
  }

  @Override
  public <R> R getMapped(K key, SerializableFunction<? super V, R> function) {
    return store.getMapped(key, function);
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return store.replace(key, oldValue, newValue);
  }

  @Override
  public void forEachEntry(Consumer<? super MapEntry<K, V>> action) {
    store.forEachEntry(action);
  }

  @Override
  public void close() {
    store.close();
  }

  @Override
  public void getAll(File toFile) throws IOException {
    store.getAll(toFile);
  }

  @Override
  public int size() {
    return store.size();
  }

  @Override
  public boolean isEmpty() {
    return store.isEmpty();
  }

  @Override
  public V replace(K key, V value) {
    return store.replace(key, value);
  }

  @Override
  public boolean containsKey(Object key) {
    return store.containsKey(key);
  }

  @Override
  public void putAll(File fromFile) throws IOException {
    store.putAll(fromFile);
  }

  @Override
  public boolean isOpen() {
    return store.isOpen();
  }

  @Override
  public boolean containsValue(Object value) {
    return store.containsValue(value);
  }

  @Override
  public Class<V> valueClass() {
    return store.valueClass();
  }

  @Override
  public Type valueType() {
    return store.valueType();
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    store.replaceAll(function);
  }

  @Override
  public short percentageFreeSpace() {
    return store.percentageFreeSpace();
  }

  @Override
  public int remainingAutoResizes() {
    return store.remainingAutoResizes();
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return store.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V put(K key, V value) {
    V ret = store.put(key, value);
    keysHistory.add(key);
    return ret;
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return store.computeIfPresent(key, remappingFunction);
  }

  @Override
  public V remove(Object key) {
    return store.remove(key);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return store.compute(key, remappingFunction);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    store.putAll(m);
    keysHistory.addAll(m.keySet());
  }

  @Override
  public void clear() {
    store.clear();
    keysHistory.clear();
  }

  @Override
  public Set<K> keySet() {
    return store.keySet();
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    V ret = store.merge(key, value, remappingFunction);
    keysHistory.add(key);
    return ret;
  }

  @Override
  public Collection<V> values() {
    return store.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return store.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    return store.equals(o);
  }

  @Override
  public int hashCode() {
    return store.hashCode();
  }
}
