package io.ebeaninternal.json;

import io.ebean.ModifyAwareType;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps a Set for the purposes of detecting modifications.
 */
public class ModifyAwareSet<E> implements Set<E>, ModifyAwareWrapper, Serializable {

  private static final long serialVersionUID = 1;

  protected final ModifyAwareType owner;

  protected final Set<E> set;

  /**
   * Create as top level with it's own ModifyAwareOwner instance wrapping the given Set.
   */
  public ModifyAwareSet(Set<E> underlying) {
    this(new ModifyAwareFlag(), underlying);
  }

  /**
   * Create with an Owner that is notified of modifications.
   */
  public ModifyAwareSet(ModifyAwareType owner, Set<E> underlying) {
    this.owner = owner;
    this.set = underlying;
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
  public String toString() {
    return set.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof ModifyAwareSet) {
      ModifyAwareSet<?> that = (ModifyAwareSet<?>) o;
      return Objects.equals(set, that.set);
    }
    if (!(o instanceof Set)) return false;
    Set<?> that = (Set<?>) o;
    return Objects.equals(set, that);
  }

  @Override
  public int hashCode() {
    return set.hashCode();
  }

  @Override
  public boolean add(E o) {
    if (set.add(o)) {
      markAsDirty();
      return true;
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends E> collection) {
    boolean changed = false;
    for (E o : collection) {
      if (set.add(o)) {
        markAsDirty();
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public void clear() {
    if (!set.isEmpty()) {
      markAsDirty();
    }
    set.clear();
  }

  @Override
  public boolean contains(Object o) {
    return set.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return set.containsAll(collection);
  }

  @Override
  public boolean isEmpty() {
    return set.isEmpty();
  }

  @Override
  public Iterator<E> iterator() {
    return new ModifyAwareIterator<>(owner, set.iterator());
  }

  @Override
  public boolean remove(Object o) {
    if (set.remove(o)) {
      markAsDirty();
      return true;
    }
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    boolean changed = false;
    for (Object element : collection) {
      if (set.remove(element)) {
        markAsDirty();
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    boolean changed = false;
    Iterator<?> it = set.iterator();
    while (it.hasNext()) {
      Object o = it.next();
      if (!collection.contains(o)) {
        it.remove();
        markAsDirty();
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public Object[] toArray() {
    return set.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return set.toArray(a);
  }
  
  @Override
  public Object unwrap() {
    return set;
  }
}
