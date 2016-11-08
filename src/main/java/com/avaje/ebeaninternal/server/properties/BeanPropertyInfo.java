package com.avaje.ebeaninternal.server.properties;

/**
 * Provides getter setter and construction methods for beans.
 * <p>
 * This enables the implementation to use standard reflection or
 * code generation.
 * </p>
 */
public interface BeanPropertyInfo {

  /**
   * Create an EntityBean for this type.
   */
  Object createEntityBean();

  /**
   * Return the getter for a given bean property.
   */
  BeanPropertyGetter getGetter(int position);

  /**
   * Return the setter for a given bean property.
   */
  BeanPropertySetter getSetter(int position);
}
