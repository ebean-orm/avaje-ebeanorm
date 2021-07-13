package io.ebeaninternal.server.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import io.ebean.bean.MutableValue;
import io.ebean.core.type.DataBinder;
import io.ebean.core.type.DataReader;
import io.ebean.core.type.DocPropertyType;
import io.ebean.core.type.ScalarType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.SQLException;

public class ScalarTypeEncryptedWrapper<T> implements ScalarType<T>, LocalEncryptedType {

  private final ScalarType<T> wrapped;

  private final DataEncryptSupport dataEncryptSupport;

  private final ScalarTypeBytesBase byteArrayType;

  public ScalarTypeEncryptedWrapper(ScalarType<T> wrapped, ScalarTypeBytesBase byteArrayType, DataEncryptSupport dataEncryptSupport) {
    this.wrapped = wrapped;
    this.byteArrayType = byteArrayType;
    this.dataEncryptSupport = dataEncryptSupport;
  }

  @Override
  public Object localEncrypt(Object value) {
    String formatValue = wrapped.format(value);
    return dataEncryptSupport.encryptObject(formatValue);
  }

  @Override
  public long asVersion(T value) {
    throw new RuntimeException("not supported");
  }

  @Override
  public boolean isBinaryType() {
    return wrapped.isBinaryType();
  }

  @Override
  public boolean isMutable() {
    return wrapped.isMutable();
  }

  @Override
  public boolean isDirty(Object value) {
    return wrapped.isDirty(value);
  }

  @Override
  public T readData(DataInput dataInput) throws IOException {
    return wrapped.readData(dataInput);
  }

  @Override
  public void writeData(DataOutput dataOutput, T v) throws IOException {
    wrapped.writeData(dataOutput, v);
  }

  @Override
  public T read(DataReader reader) throws SQLException {
    byte[] data = reader.getBytes();
    String formattedValue = dataEncryptSupport.decryptObject(data);
    if (formattedValue == null) {
      return null;
    }
    return wrapped.parse(formattedValue);
  }
  
  @Override
  public MutableValue readMutable(DataReader reader) throws SQLException {
    byte[] data = reader.getBytes();
    String formattedValue = dataEncryptSupport.decryptObject(data);
    if (formattedValue == null) {
      return null;
    }
    return new MutableValue() {
      
      @Override
      public boolean isEqual(Object object, boolean update) {
        return formattedValue.equals(wrapped.format(object));
      }
      
      @Override
      public Object get() {
        return wrapped.parse(formattedValue);
      }
    };
  }

  private byte[] encrypt(T value) {
    if (value == null) {
      return null;
    }
    String formatValue = wrapped.formatValue(value);
    return dataEncryptSupport.encryptObject(formatValue);
  }

  @Override
  public void bind(DataBinder binder, T value) throws SQLException {
    byte[] encryptedValue = encrypt(value);
    byteArrayType.bind(binder, encryptedValue);
  }

  @Override
  public int getJdbcType() {
    return byteArrayType.getJdbcType();
  }

  @Override
  public int getLength() {
    return byteArrayType.getLength();
  }

  @Override
  public Class<T> getType() {
    return wrapped.getType();
  }

  @Override
  public boolean isDateTimeCapable() {
    return wrapped.isDateTimeCapable();
  }

  @Override
  public boolean isJdbcNative() {
    return false;
  }

  @Override
  public void loadIgnore(DataReader dataReader) {
    wrapped.loadIgnore(dataReader);
  }

  @Override
  @SuppressWarnings("unchecked")
  public String format(Object v) {
    return formatValue((T) v);
  }

  @Override
  public String formatValue(T v) {
    return wrapped.formatValue(v);
  }

  @Override
  public T parse(String value) {
    return wrapped.parse(value);
  }

  @Override
  public T convertFromMillis(long systemTimeMillis) {
    return wrapped.convertFromMillis(systemTimeMillis);
  }

  @Override
  public T toBeanType(Object value) {
    return wrapped.toBeanType(value);
  }

  @Override
  public Object toJdbcType(Object value) {
    return wrapped.toJdbcType(value);
  }

  @Override
  public T jsonRead(JsonParser parser) throws IOException {
    return wrapped.jsonRead(parser);
  }

  @Override
  public void jsonWrite(JsonGenerator writer, T value) throws IOException {
    wrapped.jsonWrite(writer, value);
  }

  @Override
  public DocPropertyType getDocType() {
    return wrapped.getDocType();
  }

}
