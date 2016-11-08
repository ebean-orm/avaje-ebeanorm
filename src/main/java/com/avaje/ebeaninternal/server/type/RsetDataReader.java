package com.avaje.ebeaninternal.server.type;

import com.avaje.ebeaninternal.server.core.Message;
import com.avaje.ebeaninternal.server.core.timezone.DataTimeZone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Calendar;

public class RsetDataReader implements DataReader {

  private static final int bufferSize = 512;

  static final int clobBufferSize = 512;

  static final int stringInitialSize = 512;

  private final DataTimeZone dataTimeZone;

  private final ResultSet rset;

  protected int pos;

  public RsetDataReader(DataTimeZone dataTimeZone, ResultSet rset) {
    this.dataTimeZone = dataTimeZone;
    this.rset = rset;
  }

  public void close() throws SQLException {
    rset.close();
  }

  public boolean next() throws SQLException {
    return rset.next();
  }

  public void resetColumnPosition() {
    pos = 0;
  }

  public void incrementPos(int increment) {
    pos += increment;
  }

  protected int pos() {
    return ++pos;
  }

  public Array getArray() throws SQLException {
    return rset.getArray(pos());
  }

  public Object getObject() throws SQLException {
    return rset.getObject(pos());
  }

  public BigDecimal getBigDecimal() throws SQLException {
    return rset.getBigDecimal(pos());
  }


  public InputStream getBinaryStream() throws SQLException {
    return rset.getBinaryStream(pos());
  }

  public Boolean getBoolean() throws SQLException {
    boolean v = rset.getBoolean(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }

  public Byte getByte() throws SQLException {
    byte v = rset.getByte(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }

  public byte[] getBytes() throws SQLException {
    return rset.getBytes(pos());
  }

  public Date getDate() throws SQLException {
    return rset.getDate(pos());
  }

  public Double getDouble() throws SQLException {
    double v = rset.getDouble(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }

  public Float getFloat() throws SQLException {
    float v = rset.getFloat(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }

  public Integer getInt() throws SQLException {
    int v = rset.getInt(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }


  public Long getLong() throws SQLException {
    long v = rset.getLong(pos());
    if (rset.wasNull()) {
      return null;
    }
    return v;
  }


  public Ref getRef() throws SQLException {
    return rset.getRef(pos());
  }


  public Short getShort() throws SQLException {
    short s = rset.getShort(pos());
    if (rset.wasNull()) {
      return null;
    }
    return s;
  }


  public String getString() throws SQLException {
    return rset.getString(pos());
  }


  public Time getTime() throws SQLException {
    return rset.getTime(pos());
  }

  public Timestamp getTimestamp() throws SQLException {
    Calendar cal = dataTimeZone.getTimeZone();
    if (cal != null) {
      return rset.getTimestamp(pos(), cal);
    } else {
      return rset.getTimestamp(pos());
    }
  }

  public String getStringFromStream() throws SQLException {
    Reader reader = rset.getCharacterStream(pos());
    if (reader == null) {
      return null;
    }
    return readStringLob(reader);
  }

  protected String readStringLob(Reader reader) throws SQLException {

    char[] buffer = new char[clobBufferSize];
    int readLength;
    StringBuilder out = new StringBuilder(stringInitialSize);
    try {
      while ((readLength = reader.read(buffer)) != -1) {
        out.append(buffer, 0, readLength);
      }
      reader.close();
    } catch (IOException e) {
      throw new SQLException(Message.msg("persist.clob.io", e.getMessage()));
    }

    return out.toString();
  }

  public byte[] getBinaryBytes() throws SQLException {
    InputStream in = rset.getBinaryStream(pos());
    return getBinaryLob(in);
  }

  protected byte[] getBinaryLob(InputStream in) throws SQLException {

    try {
      if (in == null) {
        return null;
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      byte[] buf = new byte[bufferSize];
      int len;
      while ((len = in.read(buf, 0, buf.length)) != -1) {
        out.write(buf, 0, len);
      }
      byte[] data = out.toByteArray();

      if (data.length == 0) {
        data = null;
      }
      in.close();
      out.close();
      return data;

    } catch (IOException e) {
      throw new SQLException(e.getClass().getName() + ":" + e.getMessage());
    }
  }

}
