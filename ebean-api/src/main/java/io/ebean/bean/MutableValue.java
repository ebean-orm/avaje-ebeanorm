package io.ebean.bean;

public interface MutableValue {
  Object get();
  boolean isEqual(Object object, boolean update);
}
