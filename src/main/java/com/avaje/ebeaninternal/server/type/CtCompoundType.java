package com.avaje.ebeaninternal.server.type;

import com.avaje.ebean.config.CompoundType;
import com.avaje.ebean.config.CompoundTypeProperty;
import com.avaje.ebeaninternal.server.text.json.WriteJson;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * The internal representation of a Compound Type (Immutable Compound Value Object).
 *
 * @param <V> The Type of the "Immutable Compound Value Object".
 */
public final class CtCompoundType<V> implements ScalarDataReader<V> {

  private final Class<V> cvoClass;

  private final CompoundType<V> cvoType;

  private final ScalarDataReader<Object>[] propReaders;

  private final CompoundTypeProperty<V, ?>[] properties;

  public CtCompoundType(Class<V> cvoClass, CompoundType<V> cvoType, ScalarDataReader<Object>[] propReaders) {

    this.cvoClass = cvoClass;
    this.cvoType = cvoType;
    this.properties = cvoType.getProperties();
    this.propReaders = propReaders;
  }

  public String toString() {
    return cvoClass.toString();
  }

  public Class<V> getCompoundTypeClass() {
    return cvoClass;
  }

  public V create(Object[] propertyValues) {
    return cvoType.create(propertyValues);
  }


  public V create(Map<String, Object> valueMap) {

    if (valueMap.size() != properties.length) {
      // not enough elements in the map
      return null;
    }

    // we expect the map to contain a value for
    // each property and that the values are the
    // correct type
    Object[] propertyValues = new Object[properties.length];
    for (int i = 0; i < properties.length; i++) {
      propertyValues[i] = valueMap.get(properties[i].getName());
      if (propertyValues[i] == null) {
        String m = "Null value for " + properties[i].getName() + " in map " + valueMap;
        throw new RuntimeException(m);
      }
    }

    return create(propertyValues);
  }

  public V read(DataReader source) throws SQLException {

    boolean nullValue = false;
    Object[] values = new Object[propReaders.length];

    for (int i = 0; i < propReaders.length; i++) {
      Object o = propReaders[i].read(source);
      values[i] = o;
      if (o == null) {
        nullValue = true;
      }
    }

    if (nullValue) {
      return null;
    }

    return create(values);
  }

  public void loadIgnore(DataReader dataReader) {
    for (ScalarDataReader<Object> propReader : propReaders) {
      propReader.loadIgnore(dataReader);
    }
  }

  public void bind(DataBind b, V value) throws SQLException {

    CompoundTypeProperty<V, ?>[] props = cvoType.getProperties();

    for (int i = 0; i < props.length; i++) {
      Object o = props[i].getValue(value);
      propReaders[i].bind(b, o);
    }
  }

  /**
   * Recursively accumulate all the scalar types (in depth first order).
   * <p>
   * This creates a flat list of scalars even when compound types are embedded
   * inside compound types.
   * </p>
   */
  public void accumulateScalarTypes(String parent, CtCompoundTypeScalarList list) {

    CompoundTypeProperty<V, ?>[] props = cvoType.getProperties();

    for (int i = 0; i < propReaders.length; i++) {
      String propName = getFullPropName(parent, props[i].getName());

      list.addCompoundProperty(propName, this, props[i]);

      propReaders[i].accumulateScalarTypes(propName, list);
    }

  }

  /**
   * Return the full property name (for compound types embedded in other
   * compound types).
   *
   * @param parent   the parent property name
   * @param propName the local property name
   */
  private String getFullPropName(String parent, String propName) {
    if (parent == null) {
      return propName;
    } else {
      return parent + "." + propName;
    }
  }

  public Object jsonConvert(Map<String, Object> map) {
    return readJsonElementObject(map);
  }

  @SuppressWarnings("unchecked")
  private Object readJsonElementObject(Map<String, Object> jsonObject) {

    boolean nullValue = false;
    Object[] values = new Object[propReaders.length];

    for (int i = 0; i < propReaders.length; i++) {
      String propName = properties[i].getName();
      Object jsonElement = jsonObject.get(propName);

      if (propReaders[i] instanceof CtCompoundType<?>) {
        values[i] = ((CtCompoundType<?>) propReaders[i]).readJsonElementObject((Map<String, Object>) jsonElement);
      } else {
        values[i] = ((ScalarType<?>) propReaders[i]).parse(jsonElement.toString());
      }
      if (values[i] == null) {
        nullValue = true;
      }
    }

    if (nullValue) {
      return null;
    }

    return create(values);
  }


  public void jsonWrite(WriteJson ctx, Object valueObject, String propertyName) throws IOException {

    ctx.beginAssocOne(propertyName, valueObject);
    jsonWriteProps(ctx, valueObject);
    ctx.endAssocOne();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void jsonWriteProps(WriteJson ctx, Object valueObject) throws IOException {

    ctx.gen().writeStartObject();

    for (int i = 0; i < properties.length; i++) {
      String propName = properties[i].getName();
      Object value = properties[i].getValue((V) valueObject);
      if (propReaders[i] instanceof CtCompoundType<?>) {
        ctx.writeFieldName(propName);
        ((CtCompoundType) propReaders[i]).jsonWrite(ctx, value, propName);

      } else {
        ctx.writeFieldName(propName);
        ((ScalarType) propReaders[i]).jsonWrite(ctx.gen(), value);
      }
    }

    ctx.gen().writeEndObject();
  }

  public String[] getPropertyNames() {
    String[] ret = new String[properties.length];
    for(int i = 0; i < properties.length; i++) {
      ret[i] = properties[i].getName();
    }
    return ret;
  }

}
