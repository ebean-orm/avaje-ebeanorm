package io.ebeaninternal.json;

import io.ebean.ModifyAwareType;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/**
 * Modify aware wrapper of a list.
 */
public class ModifyAwareList<E> implements List<E>, ModifyAwareWrapper, Serializable {

  private static final long serialVersionUID = 1;

  final List<E> list;

  final ModifyAwareType owner;

  public ModifyAwareList(List<E> list) {
    this.list = list;
    this.owner = new ModifyAwareFlag();
  }

  public ModifyAwareList(ModifyAwareType owner, List<E> list) {
    this.list = list;
    this.owner = owner;
  }

  @Override
  public String toString() {
    return list.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof ModifyAwareList) {
      ModifyAwareList<?> that = (ModifyAwareList<?>) o;
      return Objects.equals(list, that.list);
    }
    if (!(o instanceof List)) return false;
    List<?> that = (List<?>) o;
    return Objects.equals(list, that);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
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
    return list.size();
  }

  @Override
  public boolean isEmpty() {
    return list.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return list.contains(o);
  }

  @Override
  public Iterator<E> iterator() {
    return new ModifyAwareIterator<>(owner, list.iterator());
  }

  @Override
  public Object[] toArray() {
    return list.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    //noinspection SuspiciousToArrayCall
    return list.toArray(a);
  }

  @Override
  public boolean add(E e) {
    markAsDirty();
    return list.add(e);
  }

  @Override
  public boolean remove(Object o) {
    markAsDirty();
    return list.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return list.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    markAsDirty();
    return list.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    markAsDirty();
    return list.addAll(index, c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    markAsDirty();
    return list.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    markAsDirty();
    return list.retainAll(c);
  }

  @Override
  public void clear() {
    markAsDirty();
    list.clear();
  }

  @Override
  public E get(int index) {
    return list.get(index);
  }

  @Override
  public E set(int index, E element) {
    markAsDirty();
    return list.set(index, element);
  }

  @Override
  public void add(int index, E element) {
    markAsDirty();
    list.add(index, element);
  }

  @Override
  public E remove(int index) {
    markAsDirty();
    return list.remove(index);
  }

  @Override
  public int indexOf(Object o) {
    return list.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return list.lastIndexOf(o);
  }

  @Override
  public ListIterator<E> listIterator() {
    return new ModifyAwareListIterator<>(owner, list.listIterator());
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    return new ModifyAwareListIterator<>(owner, list.listIterator(index));
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return new ModifyAwareList<>(owner, list.subList(fromIndex, toIndex));
  }

  /**
   * Create an return a modify aware Set.
   */
  public ModifyAwareSet<E> asSet() {
    return new ModifyAwareSet<>(owner, new LinkedHashSet<>(list));
  }
  
  @Override
  public Object unwrap() {
    return list;
  }
}
