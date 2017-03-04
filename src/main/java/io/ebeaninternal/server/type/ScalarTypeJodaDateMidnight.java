package io.ebeaninternal.server.type;

import io.ebeaninternal.server.core.BasicTypeConverter;
import org.joda.time.DateMidnight;

import java.sql.Date;
import java.sql.Types;

/**
 * ScalarType for Joda DateMidnight. This maps to a JDBC Date.
 */
@SuppressWarnings("deprecation")
public class ScalarTypeJodaDateMidnight extends ScalarTypeBaseDate<DateMidnight> {

  /**
   * Instantiates a new scalar type joda date midnight.
   */
  public ScalarTypeJodaDateMidnight() {
    super(DateMidnight.class, false, Types.DATE);
  }

  @Override
  public long convertToMillis(DateMidnight value) {
    return value.getMillis();
  }

  @Override
  public DateMidnight convertFromDate(Date ts) {
    return new DateMidnight(ts.getTime());
  }

  @Override
  public Date convertToDate(DateMidnight t) {
    return new Date(t.getMillis());
  }

  @Override
  public Object toJdbcType(Object value) {
    if (value instanceof DateMidnight) {
      return new Date(((DateMidnight) value).getMillis());
    }
    return BasicTypeConverter.toDate(value);
  }

  @Override
  public DateMidnight toBeanType(Object value) {
    if (value instanceof java.util.Date) {
      return new DateMidnight(((java.util.Date) value).getTime());
    }
    return (DateMidnight) value;
  }
}
