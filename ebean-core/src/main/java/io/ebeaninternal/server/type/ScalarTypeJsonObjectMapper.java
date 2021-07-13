package io.ebeaninternal.server.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import io.ebean.core.type.DataBinder;
import io.ebean.core.type.DataReader;
import io.ebean.core.type.DocPropertyType;
import io.ebean.core.type.ScalarType;
import io.ebean.text.TextException;
import io.ebeaninternal.json.ModifyAwareList;
import io.ebeaninternal.json.ModifyAwareMap;
import io.ebeaninternal.json.ModifyAwareSet;

import javax.persistence.PersistenceException;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Supports @DbJson properties using Jackson ObjectMapper.
 */
class ScalarTypeJsonObjectMapper {

  /**
   * Create and return the appropriate ScalarType.
   */
  static ScalarType<?> createTypeFor(TypeJsonManager jsonManager, AnnotatedField field, int dbType, DocPropertyType docType) {
    Class<?> type = field.getRawType();
    if (Set.class.equals(type)) {
      return new OmSet(jsonManager, field, dbType, docType);
    }
    if (List.class.equals(type)) {
      return new OmList(jsonManager, field, dbType, docType);
    }
    if (Map.class.equals(type)) {
      return new OmMap(jsonManager, field, dbType);
    }
    return new GenericObject(jsonManager, field, dbType, type);
  }

  /**
   * Maps any type (Object) using Jackson ObjectMapper.
   */
  private static class GenericObject extends Base<Object> {

    GenericObject(TypeJsonManager jsonManager, AnnotatedField field, int dbType, Class<?> rawType) {
      super(Object.class, jsonManager, field, dbType, DocPropertyType.OBJECT, rawType);
    }
  }

  /**
   * Type for Sets wrapping the ObjectMapper Set as a ModifyAwareSet.
   */
  @SuppressWarnings("rawtypes")
  private static class OmSet extends Base<Set> {

    OmSet(TypeJsonManager jsonManager, AnnotatedField field, int dbType, DocPropertyType docType) {
      super(Set.class, jsonManager, field, dbType, docType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set read(DataReader reader) throws SQLException {
      Set value = super.read(reader);
      return value == null ? null : new ModifyAwareSet(value);
    }
  }

  /**
   * Type for Lists wrapping the ObjectMapper List as a ModifyAwareList.
   */
  @SuppressWarnings("rawtypes")
  private static class OmList extends Base<List> {

    OmList(TypeJsonManager jsonManager, AnnotatedField field, int dbType, DocPropertyType docType) {
      super(List.class, jsonManager, field, dbType, docType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List read(DataReader reader) throws SQLException {
      List value = super.read(reader);
      return value == null ? null : new ModifyAwareList(value);
    }
  }

  /**
   * Type for Map wrapping the ObjectMapper Map as a ModifyAwareMap.
   */
  @SuppressWarnings("rawtypes")
  private static class OmMap extends Base<Map> {

    OmMap(TypeJsonManager jsonManager, AnnotatedField field, int dbType) {
      super(Map.class, jsonManager, field, dbType, DocPropertyType.OBJECT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map read(DataReader reader) throws SQLException {
      Map value = super.read(reader);
      return value == null ? null : new ModifyAwareMap(value);
    }
  }

  /**
   * ScalarType that uses Jackson ObjectMapper to marshall/unmarshall to/from JSON
   * and storing them in one of JSON, JSONB, VARCHAR, CLOB or BLOB.
   */
  private static abstract class Base<T> extends ScalarTypeBase<T> {

    private final ObjectWriter objectWriter;
    private final ObjectMapper objectReader;
    private final JavaType deserType;
    private final String pgType;
    private final DocPropertyType docType;
    private final TypeJsonManager.DirtyHandler dirtyHandler;

    Base(Class<T> cls, TypeJsonManager jsonManager, AnnotatedField field, int dbType, DocPropertyType docType) {
      this(cls, jsonManager, field, dbType, docType, cls);
    }

    Base(Class<T> cls, TypeJsonManager jsonManager, AnnotatedField field, int dbType, DocPropertyType docType, Class<?> rawType) {
      super(cls, false, dbType);
      this.objectReader = jsonManager.objectMapper();
      this.pgType = jsonManager.postgresType(dbType);
      this.docType = docType;
      this.dirtyHandler = jsonManager.dirtyHandler(cls, rawType);
      final JacksonTypeHelper helper = new JacksonTypeHelper(field, objectReader);
      this.deserType = helper.type();
      this.objectWriter = helper.objectWriter();
    }

    /**
     * Consider as a mutable type. Use the isDirty() method to check for dirty state.
     */
    @Override
    public boolean isMutable() {
      return true;
    }

    /**
     * Return true if the value should be considered dirty (and included in an
     * update).
     */
    @Override
    public boolean isDirty(Object value) {
      return dirtyHandler.isDirty(value, objectWriter);
    }

    @Override
    public T read(DataReader reader) throws SQLException {
      String json = reader.getString();
      if (json == null || json.isEmpty()) {
        return null;
      }
      try {
        T ret = objectReader.readValue(json, deserType);
        dirtyHandler.track(ret,json);
        return ret;
      } catch (IOException e) {
        throw new TextException("Failed to parse JSON [{}] as " + deserType, json, e);
      }
    }
    
    @Override
    public void bind(DataBinder binder, T value) throws SQLException {
      if (pgType != null) {
        String rawJson = (value == null) ? null : formatValue(value);
        binder.setObject(PostgresHelper.asObject(pgType, rawJson));
      } else {
        if (value == null) {
          binder.setNull(Types.VARCHAR); // use varchar, otherwise SqlServer/db2 will fail with 'Invalid JDBC data type 5.001.'
        } else {
          binder.setString(formatValue(value));
        }
      }
    }

    @Override
    public Object toJdbcType(Object value) {
      // no type conversion supported
      return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T toBeanType(Object value) {
      // no type conversion supported
      return (T) value;
    }

    @Override
    public String formatValue(T value) {
      try {
        return objectWriter.writeValueAsString(value);
      } catch (JsonProcessingException e) {
        throw new PersistenceException("Unable to create JSON", e);
      }
    }

    @Override
    public T parse(String value) {
      try {
        return objectReader.readValue(value, deserType);
      } catch (IOException e) {
        throw new TextException("Failed to parse JSON [{}] as " + deserType, value, e);
      }
    }

    @Override
    public DocPropertyType getDocType() {
      return docType;
    }

    @Override
    public boolean isDateTimeCapable() {
      return false;
    }

    @Override
    public T convertFromMillis(long dateTime) {
      throw new IllegalStateException("Not supported");
    }

    @Override
    public T jsonRead(JsonParser parser) throws IOException {
      return objectReader.readValue(parser, deserType);
    }

    @Override
    public void jsonWrite(JsonGenerator writer, T value) throws IOException {
      objectWriter.writeValue(writer, value);
    }

    @Override
    public T readData(DataInput dataInput) throws IOException {
      if (!dataInput.readBoolean()) {
        return null;
      } else {
        return parse(dataInput.readUTF());
      }
    }

    @Override
    public void writeData(DataOutput dataOutput, T value) throws IOException {
      if (value == null) {
        dataOutput.writeBoolean(false);
      } else {
        ScalarHelp.writeUTF(dataOutput, format(value));
      }
    }
  }
}
