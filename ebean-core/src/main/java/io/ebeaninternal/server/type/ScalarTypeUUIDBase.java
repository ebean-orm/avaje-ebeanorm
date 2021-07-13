package io.ebeaninternal.server.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebean.core.type.DocPropertyType;
import io.ebeaninternal.server.core.BasicTypeConverter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/**
 * Base UUID type for string formatting, json handling etc.
 */
public abstract class ScalarTypeUUIDBase extends ScalarTypeBase<UUID> implements ScalarTypeLogicalType {

  public ScalarTypeUUIDBase(boolean jdbcNative, int jdbcType) {
    super(UUID.class, jdbcNative, jdbcType);
  }

  @Override
  public int getLogicalType() {
    return DbPlatformType.UUID;
  }

  @Override
  public String formatValue(UUID value) {
    return value.toString();
  }

  @Override
  public UUID parse(String value) {
    return UUID.fromString(value);
  }

  @Override
  public UUID convertFromMillis(long dateTime) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public boolean isDateTimeCapable() {
    return false;
  }

  @Override
  public UUID toBeanType(Object value) {
    return BasicTypeConverter.toUUID(value, false);
  }

  @Override
  public Object toJdbcType(Object value) {
    return BasicTypeConverter.convert(value, jdbcType);
  }

  @Override
  public UUID readData(DataInput dataInput) throws IOException {
    if (!dataInput.readBoolean()) {
      return null;
    } else {
      return parse(dataInput.readUTF());
    }
  }

  @Override
  public void writeData(DataOutput dataOutput, UUID value) throws IOException {
    if (value == null) {
      dataOutput.writeBoolean(false);
    } else {
      ScalarHelp.writeUTF(dataOutput, format(value));
    }
  }

  @Override
  public void jsonWrite(JsonGenerator writer, UUID value) throws IOException {
    writer.writeString(formatValue(value));
  }

  @Override
  public UUID jsonRead(JsonParser parser) throws IOException {
    return parse(parser.getValueAsString());
  }

  @Override
  public DocPropertyType getDocType() {
    return DocPropertyType.UUID;
  }
}
