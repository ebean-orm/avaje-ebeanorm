package io.ebean.core.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import io.ebean.bean.MutableValue;
import io.ebean.text.StringFormatter;
import io.ebean.text.StringParser;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Describes a scalar type.
 * <p>
 * Scalar in the sense that the types are not compound types. Scalar types only
 * map to a single database column.
 * </p>
 * <p>
 * These types fall into two categories. Types that are mapped natively to JDBC
 * types and the rest. Types that map to native JDBC types do not require any
 * data type conversion to be persisted to the database. These are java types
 * that map via java.sql.Types.
 * </p>
 * <p>
 * Types that are not native to JDBC require some conversion. These include some
 * common java types such as java.util.Date, java.util.Calendar,
 * java.math.BigInteger.
 * </p>
 * <p>
 * Note that Booleans may be native for some databases and require conversion on
 * other databases.
 * </p>
 */
public interface ScalarType<T> extends StringParser, StringFormatter, ScalarDataReader<T> {

  /**
   * Return true if this is a binary type and can not support parse() and format() from/to string.
   * This allows Ebean to optimise marshalling types to string.
   */
  boolean isBinaryType();

  /**
   * Return true if this is a mutable scalar type (like hstore).
   */
  boolean isMutable();

  /**
   * For mutable scalarType's return true if the value is dirty.
   * Non-dirty properties may be excluded from updates.
   */
  boolean isDirty(Object value);

  /**
   * Return the default DB column length for this type.
   * <p>
   * If a BeanProperty has no explicit length defined then this length should
   * be assigned.
   * </p>
   * <p>
   * This is primarily to support defining a length on Enum types (to
   * supplement defining the length on the BeanProperty directly).
   * </p>
   */
  int getLength();

  /**
   * Return true if the type is native to JDBC.
   * <p>
   * If it is native to JDBC then its values/instances do not need to be
   * converted to and from an associated JDBC type.
   * </p>
   */
  boolean isJdbcNative();

  /**
   * Return the type as per java.sql.Types that this maps to.
   * <p>
   * This type should be consistent with the toJdbcType() method in converting
   * the type to the appropriate type for binding to preparedStatements.
   * </p>
   */
  int getJdbcType();

  /**
   * Return the type that matches the bean property type.
   * <p>
   * This represents the 'logical' type rather than the JDBC type this maps
   * to.
   * </p>
   */
  Class<T> getType();

  /**
   * Read the value from the resultSet and convert if necessary to the logical
   * bean property value.
   */
  @Override
  T read(DataReader reader) throws SQLException;
  
  /**
   * Reads the value as mutable value.
   */
  MutableValue readMutable(DataReader reader) throws SQLException;

  /**
   * Ignore the reading of this value. Typically this means moving the index
   * position in the ResultSet.
   */
  void loadIgnore(DataReader reader);

  /**
   * Convert (if necessary) and bind the value to the preparedStatement.
   * <p>
   * value may need to be converted from the logical bean property type to the
   * JDBC type.
   * </p>
   */
  void bind(DataBinder binder, T value) throws SQLException;

  /**
   * Convert the value as necessary to the JDBC type.
   * <p>
   * Note that this should also match the type as per the getJdbcType()
   * method.
   * </p>
   * <p>
   * This is typically used when the matching type is used in a where clause
   * and we use this to ensure it is an appropriate jdbc type.
   * </p>
   */
  Object toJdbcType(Object value);

  /**
   * Convert the value as necessary to the logical Bean type.
   * <p>
   * The type as per the bean property.
   * </p>
   * <p>
   * This is used to automatically convert id values (typically from a string
   * to a int, long or UUID).
   * </p>
   */
  T toBeanType(Object value);

  /**
   * Convert the type into a string representation.
   * <p>
   * Reciprocal of parse().
   * </p>
   */
  String formatValue(T value);

  /**
   * Convert the type into a string representation.
   * <p>
   * This assumes the value is of the correct type.
   * </p>
   * <p>
   * This is so that ScalarType also implements the StringFormatter interface.
   * </p>
   */
  @Override
  String format(Object value);

  /**
   * Convert the string value to the appropriate java object.
   * <p>
   * Mostly used to support CSV, JSON and XML parsing.
   * </p>
   * <p>
   * Reciprocal of formatValue().
   * </p>
   */
  @Override
  T parse(String value);

  /**
   * Return the type this maps to for JSON document stores.
   */
  DocPropertyType getDocType();

  /**
   * Return true if the type can accept long systemTimeMillis input.
   * <p>
   * This is used to determine if is is sensible to use the
   * {@link #convertFromMillis(long)} method.
   * </p>
   * <p>
   * This includes the Date, Calendar, sql Date, Time, Timestamp, JODA types
   * as well as Long, BigDecimal and String (although it generally is not
   * expected to parse systemTimeMillis to a String or BigDecimal).
   * </p>
   */
  boolean isDateTimeCapable();

  /**
   * Convert the value into a long version value.
   */
  long asVersion(T value);

  /**
   * Convert the systemTimeMillis into the appropriate java object.
   * <p>
   * For non dateTime types this will throw an exception.
   * </p>
   */
  T convertFromMillis(long dateTime);

  /**
   * Read the value from binary input.
   */
  T readData(DataInput dataInput) throws IOException;

  /**
   * Write the value to binary output.
   */
  void writeData(DataOutput dataOutput, T v) throws IOException;

  /**
   * Read the value from JsonParser.
   */
  T jsonRead(JsonParser parser) throws IOException;

  /**
   * Write the value to the JsonGenerator.
   */
  void jsonWrite(JsonGenerator writer, T value) throws IOException;

}
