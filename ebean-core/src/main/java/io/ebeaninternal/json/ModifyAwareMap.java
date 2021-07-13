package io.ebeaninternal.json;

import io.ebean.ModifyAwareType;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Map that is wraps an underlying map for the purpose of detecting changes.
 */
public class ModifyAwareMap<K, V> implements Map<K, V>, ModifyAwareWrapper, Serializable {

  private static final long serialVersionUID = 1;

  final ModifyAwareType owner;

  /**
   * The underlying map.
   */
  final Map<K, V> map;

  public ModifyAwareMap(Map<K, V> underlying) {
    this.map = underlying;
    this.owner = new ModifyAwareFlag();
  }

  public ModifyAwareMap(ModifyAwareType owner, Map<K, V> underlying) {
    this.owner = owner;
    this.map = underlying;
  }

  @Override
  public String toString() {
    return map.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof ModifyAwareMap) {
      ModifyAwareMap<?,?> that = (ModifyAwareMap<?,?>) o;
      return Objects.equals(map, that.map);
    }
    if (!(o instanceof Map)) return false;
    Map<?,?> that = (Map<?,?>) o;
    return Objects.equals(map, that);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public boolean isMarkedDirty() {
    return owner.isMarkedDirty();
  }

  @Override
  public void setMarkedDirty(boolean markedDirty) {
    owner.setMarkedDirty(markedDirty);
  }

  private void markAsDirty() {
    owner.setMarkedDirty(true);
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public V get(Object key) {
    return map.get(key);
  }

  @Override
  public V put(K key, V value) {
    markAsDirty();
    return map.put(key, value);
  }

  @Override
  public V remove(Object key) {
    V value = map.remove(key);
    if (value != null) {
      markAsDirty();
    }
    return value;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    markAsDirty();
    map.putAll(m);
  }


  @Override
  public void clear() {
    if (!map.isEmpty()) {
      markAsDirty();
    }
    map.clear();
  }

  @Override
  public Set<K> keySet() {
    return new ModifyAwareSet<>(this, map.keySet());
  }

  @Override
  public Collection<V> values() {
    return new ModifyAwareSet<>(this, new LinkedHashSet<>(map.values()));
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return new ModifyAwareSet<>(this, map.entrySet());
  }

  @Override
  public Object unwrap() {
    return map;
  }
}
