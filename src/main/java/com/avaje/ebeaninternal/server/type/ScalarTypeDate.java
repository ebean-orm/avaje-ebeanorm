package com.avaje.ebeaninternal.server.type;

import com.avaje.ebeaninternal.server.core.BasicTypeConverter;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;

/**
 * ScalarType for java.sql.Date.
 */
public class ScalarTypeDate extends ScalarTypeBaseDate<Date> {

  public ScalarTypeDate() {
    super(Date.class, true, Types.DATE);
  }

  @Override
  public long convertToMillis(Date value) {
    return value.getTime();
  }

  @Override
  public Date convertFromDate(Date date) {
    return date;
  }

  @Override
  public Date convertToDate(Date t) {
    return t;
  }

  public void bind(DataBind b, Date value) throws SQLException {
    if (value == null) {
      b.setNull(Types.DATE);
    } else {
      b.setDate(value);
    }
  }

  public Date read(DataReader dataReader) throws SQLException {
    return dataReader.getDate();
  }

  public Object toJdbcType(Object value) {
    return BasicTypeConverter.toDate(value);
  }

  public Date toBeanType(Object value) {
    return BasicTypeConverter.toDate(value);
  }

}
