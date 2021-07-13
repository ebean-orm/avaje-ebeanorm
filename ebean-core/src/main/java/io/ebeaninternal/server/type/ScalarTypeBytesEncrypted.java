package io.ebeaninternal.server.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import io.ebean.bean.MutableValue;
import io.ebean.core.type.DataBinder;
import io.ebean.core.type.DataReader;
import io.ebean.core.type.DocPropertyType;
import io.ebean.core.type.ScalarType;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Encrypted ScalarType that wraps a byte[] types.
 */
public class ScalarTypeBytesEncrypted implements ScalarType<byte[]> {

  private final ScalarTypeBytesBase baseType;

  private final DataEncryptSupport dataEncryptSupport;

  public ScalarTypeBytesEncrypted(ScalarTypeBytesBase baseType, DataEncryptSupport dataEncryptSupport) {
    this.baseType = baseType;
    this.dataEncryptSupport = dataEncryptSupport;
  }

  @Override
  public long asVersion(byte[] value) {
    throw new RuntimeException("not supported");
  }

  @Override
  public boolean isBinaryType() {
    return true;
  }

  @Override
  public boolean isMutable() {
    return false;
  }

  @Override
  public boolean isDirty(Object value) {
    return false;
  }

  @Override
  public void bind(DataBinder binder, byte[] value) throws SQLException {
    value = dataEncryptSupport.encrypt(value);
    baseType.bind(binder, value);
  }

  @Override
  public int getJdbcType() {
    return baseType.getJdbcType();
  }

  @Override
  public int getLength() {
    return baseType.getLength();
  }

  @Override
  public Class<byte[]> getType() {
    return byte[].class;
  }

  @Override
  public boolean isDateTimeCapable() {
    return baseType.isDateTimeCapable();
  }

  @Override
  public boolean isJdbcNative() {
    return baseType.isJdbcNative();
  }

  @Override
  public void loadIgnore(DataReader reader) {
    baseType.loadIgnore(reader);
  }

  @Override
  public void jsonWrite(JsonGenerator writer, byte[] value) throws IOException {
    writer.writeBinary(value);
  }

  @Override
  public byte[] jsonRead(JsonParser parser) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(500);
    parser.readBinaryValue(out);
    return out.toByteArray();
  }

  @Override
  public DocPropertyType getDocType() {
    return baseType.getDocType();
  }

  @Override
  public String format(Object v) {
    throw new RuntimeException("Not used");
  }

  @Override
  public String formatValue(byte[] v) {
    throw new RuntimeException("Not used");
  }

  @Override
  public byte[] parse(String value) {
    return baseType.parse(value);
  }

  @Override
  public byte[] convertFromMillis(long systemTimeMillis) {
    return baseType.convertFromMillis(systemTimeMillis);
  }

  @Override
  public byte[] read(DataReader reader) throws SQLException {
    byte[] data = baseType.read(reader);
    data = dataEncryptSupport.decrypt(data);
    return data;
  }
  
  @Override
  public MutableValue readMutable(DataReader reader) throws SQLException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] toBeanType(Object value) {
    return baseType.toBeanType(value);
  }

  @Override
  public Object toJdbcType(Object value) {
    return baseType.toJdbcType(value);
  }

  @Override
  public byte[] readData(DataInput dataInput) throws IOException {
    if (!dataInput.readBoolean()) {
      return null;
    } else {
      int len = dataInput.readInt();
      byte[] value = new byte[len];
      dataInput.readFully(value);
      return value;
    }
  }

  @Override
  public void writeData(DataOutput dataOutput, byte[] value) throws IOException {
    if (value == null) {
      dataOutput.writeBoolean(false);
    } else {
      dataOutput.writeBoolean(true);
      dataOutput.writeInt(value.length);
      dataOutput.write(value);
    }
  }

}
