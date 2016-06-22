package com.avaje.ebeaninternal.server.type;

import com.avaje.ebean.config.JsonConfig;
import com.avaje.ebeaninternal.server.core.BasicTypeConverter;
import org.joda.time.LocalDateTime;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * ScalarType for Joda LocalDateTime. This maps to a JDBC Timestamp.
 */
public class ScalarTypeJodaLocalDateTime extends ScalarTypeBaseDateTime<LocalDateTime> {

  public ScalarTypeJodaLocalDateTime(JsonConfig.DateTime mode) {
    super(mode, LocalDateTime.class, false, Types.TIMESTAMP);
  }

  @Override
  protected String toJsonNanos(LocalDateTime value) {
    return String.valueOf(value.toDateTime().getMillis());
  }

  @Override
  protected String toJsonISO8601(LocalDateTime value) {
    return value.toString();
  }

  @Override
  public long convertToMillis(LocalDateTime value) {
    return value.toDateTime().getMillis();
  }


  @Override
  public LocalDateTime convertFromMillis(long systemTimeMillis) {
    return new LocalDateTime(systemTimeMillis);
  }

  @Override
  public LocalDateTime convertFromTimestamp(Timestamp ts) {
    return new LocalDateTime(ts.getTime());
  }

  @Override
  public Timestamp convertToTimestamp(LocalDateTime t) {
    return new Timestamp(t.toDateTime().getMillis());
  }

  @Override
  public Object toJdbcType(Object value) {
    if (value instanceof LocalDateTime) {
      return new Timestamp(((LocalDateTime) value).toDateTime().getMillis());
    }
    return BasicTypeConverter.toTimestamp(value);
  }

  @Override
  public LocalDateTime toBeanType(Object value) {
    if (value instanceof Date) {
      return new LocalDateTime(((Date) value).getTime());
    }
    return (LocalDateTime) value;
  }

}
