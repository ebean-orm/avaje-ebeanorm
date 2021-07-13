package io.ebeaninternal.server.deploy;

import com.fasterxml.jackson.core.JsonToken;
import io.ebean.ValuePair;
import io.ebean.bean.EntityBean;
import io.ebean.bean.EntityBeanIntercept;
import io.ebean.bean.MutableValue;
import io.ebean.bean.PersistenceContext;
import io.ebean.config.EncryptKey;
import io.ebean.config.dbplatform.DbEncryptFunction;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebean.core.type.DataReader;
import io.ebean.core.type.DocPropertyType;
import io.ebean.core.type.ScalarType;
import io.ebean.plugin.Property;
import io.ebean.text.StringParser;
import io.ebean.util.SplitName;
import io.ebeaninternal.api.SpiExpressionRequest;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.json.SpiJsonReader;
import io.ebeaninternal.api.json.SpiJsonWriter;
import io.ebeaninternal.server.core.EncryptAlias;
import io.ebeaninternal.server.core.InternString;
import io.ebeaninternal.server.deploy.generatedproperty.GeneratedProperty;
import io.ebeaninternal.server.deploy.generatedproperty.GeneratedWhenCreated;
import io.ebeaninternal.server.deploy.generatedproperty.GeneratedWhenModified;
import io.ebeaninternal.server.deploy.meta.DeployBeanProperty;
import io.ebeaninternal.server.el.ElPropertyChainBuilder;
import io.ebeaninternal.server.el.ElPropertyValue;
import io.ebeaninternal.server.properties.BeanPropertyGetter;
import io.ebeaninternal.server.properties.BeanPropertySetter;
import io.ebeaninternal.server.query.STreeProperty;
import io.ebeaninternal.server.query.SqlBeanLoad;
import io.ebeaninternal.server.query.SqlJoinType;
import io.ebeaninternal.server.type.DataBind;
import io.ebeaninternal.server.type.LocalEncryptedType;
import io.ebeaninternal.server.type.ScalarTypeBoolean;
import io.ebeaninternal.server.type.ScalarTypeEnum;
import io.ebeaninternal.server.type.ScalarTypeLogicalType;
import io.ebeaninternal.util.ValueUtil;
import io.ebeanservice.docstore.api.mapping.DocMappingBuilder;
import io.ebeanservice.docstore.api.mapping.DocPropertyMapping;
import io.ebeanservice.docstore.api.mapping.DocPropertyOptions;
import io.ebeanservice.docstore.api.support.DocStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.persistence.PersistenceException;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Description of a property of a bean. Includes its deployment information such
 * as database column mapping information.
 */
public class BeanProperty implements ElPropertyValue, Property, STreeProperty {

  private static final Logger logger = LoggerFactory.getLogger(BeanProperty.class);

  private static final String ENC_PREFIX = " " + EncryptAlias.PREFIX;

  /**
   * Flag to mark this is the id property.
   */
  private final boolean id;

  private final boolean importedPrimaryKey;

  /**
   * Flag to make this as a dummy property for unidirecitonal relationships.
   */
  private final boolean unidirectionalShadow;

  /**
   * Flag set if this maps to the inheritance discriminator column
   */
  private final boolean discriminator;

  /**
   * Flag to mark the property as embedded. This could be on
   * BeanPropertyAssocOne rather than here. Put it here for checking Id type
   * (embedded or not).
   */
  final boolean embedded;

  /**
   * Flag indicating if this the version property.
   */
  private final boolean version;

  private final boolean naturalKey;

  /**
   * Set if this property is nullable.
   */
  private final boolean nullable;

  private final boolean unique;

  /**
   * Is this property include in database resultSet.
   */
  private final boolean dbRead;

  /**
   * Include in DB insert.
   */
  private final boolean dbInsertable;

  /**
   * Include in DB update.
   */
  private final boolean dbUpdatable;

  /**
   * True if the property is based on a SECONDARY table.
   */
  private final boolean secondaryTable;

  private final TableJoin secondaryTableJoin;
  private final String secondaryTableJoinPrefix;

  /**
   * The property is inherited from a super class.
   */
  private final boolean inherited;

  private final Class<?> owningType;

  private final boolean local;

  /**
   * True if the property is a Clob, Blob LongVarchar or LongVarbinary.
   */
  private final boolean lob;

  private final boolean fetchEager;

  final boolean isTransient;

  /**
   * The logical bean property name.
   */
  final String name;

  final int propertyIndex;

  /**
   * The reflected field.
   */
  private final Field field;

  /**
   * The bean type.
   */
  private final Class<?> propertyType;

  private final String dbBind;

  /**
   * The database column. This can include quoted identifiers.
   */
  final String dbColumn;

  private final String elPrefix;
  final String elPlaceHolder;
  final String elPlaceHolderEncrypted;

  /**
   * Select part of a SQL Formula used to populate this property.
   */
  private final String sqlFormulaSelect;

  /**
   * Join part of a SQL Formula.
   */
  final String sqlFormulaJoin;

  private final String aggregation;

  private final boolean formula;

  /**
   * Set to true if stored encrypted.
   */
  private final boolean dbEncrypted;

  private final boolean localEncrypted;

  private final int dbEncryptedType;

  /**
   * The jdbc data type this maps to.
   */
  private final int dbType;

  final boolean excludedFromHistory;

  /**
   * Generator for insert or update timestamp etc.
   */
  private final GeneratedProperty generatedProperty;

  private final BeanPropertyGetter getter;

  private final BeanPropertySetter setter;

  final BeanDescriptor<?> descriptor;

  /**
   * Used for non-jdbc native types (java.util.Date Enums etc). Converts from
   * logical to jdbc types.
   */
  @SuppressWarnings("rawtypes")
  final ScalarType scalarType;

  private final DocPropertyOptions docOptions;

  /**
   * The length or precision for DB column.
   */
  private final int dbLength;

  /**
   * The scale for DB column (decimal).
   */
  private final int dbScale;

  /**
   * Deployment defined DB column definition.
   */
  private final String dbColumnDefn;

  /**
   * DB Column default value for DDL definition (FALSE, NOW etc).
   */
  private final String dbColumnDefault;
  private final List<DbMigrationInfo> dbMigrationInfos;

  /**
   * Database DDL column comment.
   */
  private final String dbComment;

  private final DbEncryptFunction dbEncryptFunction;

  private int deployOrder;

  final boolean jsonSerialize;
  final boolean jsonDeserialize;
  private final boolean unmappedJson;
  private final boolean tenantId;

  private final boolean draft;

  private final boolean draftOnly;

  private final boolean draftDirty;

  private final boolean draftReset;

  private final boolean softDelete;

  private final String softDeleteDbSet;

  private final String softDeleteDbPredicate;

  public BeanProperty(DeployBeanProperty deploy) {
    this(null, deploy);
  }

  public BeanProperty(BeanDescriptor<?> descriptor, DeployBeanProperty deploy) {
    this.descriptor = descriptor;
    this.name = InternString.intern(deploy.getName());
    this.propertyIndex = deploy.getPropertyIndex();
    this.unidirectionalShadow = deploy.isUndirectionalShadow();
    this.importedPrimaryKey = deploy.isImportedPrimaryKey();
    this.discriminator = deploy.isDiscriminator();
    this.localEncrypted = deploy.isLocalEncrypted();
    this.dbEncrypted = deploy.isDbEncrypted();
    this.dbEncryptedType = deploy.getDbEncryptedType();
    this.dbEncryptFunction = deploy.getDbEncryptFunction();
    this.dbBind = deploy.getDbBind();
    this.dbRead = deploy.isDbRead();
    this.dbInsertable = deploy.isDbInsertable();
    this.dbUpdatable = deploy.isDbUpdateable();
    this.excludedFromHistory = deploy.isExcludedFromHistory();
    this.unmappedJson = deploy.isUnmappedJson();
    this.tenantId = deploy.isTenantId();
    this.draft = deploy.isDraft();
    this.draftDirty = deploy.isDraftDirty();
    this.draftOnly = deploy.isDraftOnly();
    this.draftReset = deploy.isDraftReset();
    this.secondaryTable = deploy.isSecondaryTable();
    if (secondaryTable) {
      this.secondaryTableJoin = new TableJoin(deploy.getSecondaryTableJoin());
      this.secondaryTableJoinPrefix = deploy.getSecondaryTableJoinPrefix();
    } else {
      this.secondaryTableJoin = null;
      this.secondaryTableJoinPrefix = null;
    }
    this.fetchEager = deploy.isFetchEager();
    this.isTransient = deploy.isTransient();
    this.nullable = deploy.isNullable();
    this.unique = deploy.isUnique();
    this.naturalKey = deploy.isNaturalKey();
    this.dbLength = deploy.getDbLength();
    this.dbScale = deploy.getDbScale();
    this.dbColumnDefn = InternString.intern(deploy.getDbColumnDefn());
    this.dbColumnDefault = deploy.getDbColumnDefaultSqlLiteral();
    this.dbMigrationInfos = deploy.getDbMigrationInfos();
    this.inherited = false;// deploy.isInherited();
    this.owningType = deploy.getOwningType();
    this.local = deploy.isLocal();
    this.version = deploy.isVersionColumn();
    this.embedded = deploy.isEmbedded();
    this.id = deploy.isId();
    this.generatedProperty = deploy.getGeneratedProperty();
    this.getter = deploy.getGetter();
    this.setter = deploy.getSetter();
    this.aggregation = deploy.parseAggregation();
    this.dbColumn = tableAliasIntern(descriptor, deploy.getDbColumn(), false, null);
    this.dbComment = deploy.getDbComment();
    this.sqlFormulaJoin = InternString.intern(deploy.getSqlFormulaJoin());
    this.sqlFormulaSelect = InternString.intern(deploy.getSqlFormulaSelect());
    this.formula = sqlFormulaSelect != null;
    this.dbType = deploy.getDbType();
    this.scalarType = deploy.getScalarType();
    this.lob = isLobType(dbType);
    this.propertyType = deploy.getPropertyType();
    this.field = deploy.getField();
    this.docOptions = deploy.getDocPropertyOptions();
    this.elPlaceHolder = tableAliasIntern(descriptor, deploy.getElPlaceHolder(), false, null);
    this.elPlaceHolderEncrypted = tableAliasIntern(descriptor, deploy.getElPlaceHolder(), dbEncrypted, dbColumn);
    this.elPrefix = deploy.getElPrefix();
    this.softDelete = deploy.isSoftDelete();
    if (softDelete) {
      ScalarTypeBoolean.BooleanBase boolType = (ScalarTypeBoolean.BooleanBase) scalarType;
      this.softDeleteDbSet = dbColumn + "=" + boolType.getDbTrueLiteral();
      this.softDeleteDbPredicate = "." + dbColumn + " = " + boolType.getDbFalseLiteral();
    } else {
      this.softDeleteDbSet = null;
      this.softDeleteDbPredicate = null;
    }
    this.jsonSerialize = deploy.isJsonSerialize();
    this.jsonDeserialize = deploy.isJsonDeserialize();
  }

  private String tableAliasIntern(BeanDescriptor<?> descriptor, String s, boolean dbEncrypted, String dbColumn) {
    if (s != null && descriptor != null) {
      s = s.replace("${ta}.", "${}");
      s = s.replace("${ta}", "${}");
      if (dbEncrypted) {
        s = dbEncryptFunction.getDecryptSql(s);
        String namedParam = ":encryptkey_" + descriptor.getBaseTable() + "___" + dbColumn;
        s = s.replace("?", namedParam);
      }
    }
    return InternString.intern(s);
  }

  /**
   * Create a Matching BeanProperty with some attributes overridden.
   * <p>
   * Primarily for supporting Embedded beans with overridden dbColumn
   * mappings.
   */
  public BeanProperty(BeanProperty source, BeanPropertyOverride override) {
    this.descriptor = source.descriptor;
    this.propertyIndex = source.propertyIndex;
    this.name = source.getName();
    this.dbColumn = override.getDbColumn();
    this.nullable = override.isDbNullable();
    this.dbLength = override.getDbLength();
    this.dbScale = override.getDbScale();
    this.dbColumnDefn = InternString.intern(override.getDbColumnDefn());
    // override with sqlFormula not currently supported
    this.sqlFormulaJoin = null;
    this.sqlFormulaSelect = null;
    this.formula = false;
    this.aggregation = null;
    this.excludedFromHistory = source.excludedFromHistory;
    this.tenantId = source.tenantId;
    this.draft = source.draft;
    this.draftDirty = source.draftDirty;
    this.draftOnly = source.draftOnly;
    this.draftReset = source.draftReset;
    this.softDelete = source.softDelete;
    this.softDeleteDbSet = source.softDeleteDbSet;
    this.softDeleteDbPredicate = source.softDeleteDbPredicate;
    this.fetchEager = source.fetchEager;
    this.importedPrimaryKey = source.importedPrimaryKey;
    this.unidirectionalShadow = source.unidirectionalShadow;
    this.discriminator = source.discriminator;
    this.localEncrypted = source.isLocalEncrypted();
    this.isTransient = source.isTransient();
    this.secondaryTable = source.isSecondaryTable();
    this.secondaryTableJoin = source.secondaryTableJoin;
    this.secondaryTableJoinPrefix = source.secondaryTableJoinPrefix;
    this.dbComment = source.dbComment;
    this.dbBind = source.getDbBind();
    this.dbEncrypted = source.isDbEncrypted();
    this.dbEncryptedType = source.getDbEncryptedType();
    this.dbEncryptFunction = source.dbEncryptFunction;
    this.dbRead = source.isDbRead();
    this.dbInsertable = source.isDbInsertable();
    this.dbUpdatable = source.isDbUpdatable();
    this.unique = source.isUnique();
    this.naturalKey = source.isNaturalKey();
    this.dbColumnDefault = source.dbColumnDefault;
    this.dbMigrationInfos = source.dbMigrationInfos;
    this.inherited = source.isInherited();
    this.owningType = source.owningType;
    this.local = owningType.equals(descriptor.getBeanType());
    this.version = source.isVersion();
    this.embedded = source.isEmbedded();
    this.id = source.isId();
    this.generatedProperty = source.getGeneratedProperty();
    this.getter = source.getter;
    this.setter = source.setter;
    this.dbType = source.getDbType(true);
    this.scalarType = source.scalarType;
    this.lob = isLobType(dbType);
    this.propertyType = source.getPropertyType();
    this.field = source.getField();
    this.docOptions = source.docOptions;
    this.unmappedJson = source.unmappedJson;
    this.elPrefix = override.replace(source.elPrefix, source.dbColumn);
    this.elPlaceHolder = override.replace(source.elPlaceHolder, source.dbColumn);
    this.elPlaceHolderEncrypted = override.replace(source.elPlaceHolderEncrypted, source.dbColumn);
    this.jsonSerialize = source.jsonSerialize;
    this.jsonDeserialize = source.jsonDeserialize;
  }

  /**
   * Initialise the property before returning to client code. Used to
   * initialise variables that can't be done in construction due to recursive
   * issues.
   */
  public void initialise(BeanDescriptorInitContext initContext) {
    // do nothing for normal BeanProperty
    if (!isTransient && scalarType == null) {
      throw new RuntimeException("No ScalarType assigned to " + descriptor.getFullName() + "." + getName());
    }
  }

  /**
   * Return the order this property appears in the bean.
   */
  public int getDeployOrder() {
    return deployOrder;
  }

  /**
   * Set the order this property appears in the bean.
   */
  public void setDeployOrder(int deployOrder) {
    this.deployOrder = deployOrder;
  }

  public ElPropertyValue buildElPropertyValue(String propName, String remainder, ElPropertyChainBuilder chain, boolean propertyDeploy) {
    return null;
  }

  /**
   * Return the BeanDescriptor that owns this property.
   */
  public BeanDescriptor<?> getBeanDescriptor() {
    return descriptor;
  }

  /**
   * Return true is this is a simple scalar property.
   */
  public boolean isScalar() {
    return true;
  }

  /**
   * Return true if this property should have a DB Column created in DDL.
   */
  public boolean isDDLColumn() {
    return !formula && !secondaryTable && (aggregation == null);
  }

  /**
   * Return true if this property is based on a formula.
   */
  @Override
  public boolean isFormula() {
    return formula;
  }

  /**
   * Return true if this property maps to the inheritance discriminator column.
   */
  public boolean isDiscriminator() {
    return discriminator;
  }

  /**
   * Return true if the underlying type is mutable.
   */
  public boolean isMutableScalarType() {
    return scalarType != null && scalarType.isMutable();
  }

  /**
   * Return the encrypt key for the column matching this property.
   */
  public EncryptKey getEncryptKey() {
    return descriptor.getEncryptKey(this);
  }

  @Override
  public String getEncryptKeyAsString() {
    return getEncryptKey().getStringValue();
  }

  public String getDecryptProperty(String propertyName) {
    return dbEncryptFunction.getDecryptSql(propertyName);
  }

  /**
   * Return the SQL for the column including decryption function and column alias.
   */
  private String getDecryptSqlWithColumnAlias(String tableAlias) {
    return dbEncryptFunction.getDecryptSql(tableAlias + "." + this.getDbColumn()) + ENC_PREFIX + tableAlias + "_" + this.getDbColumn();
  }

  @Override
  public int getFetchPreference() {
    // return some decently high value - override on ToMany property
    return 1000;
  }

  /**
   * Add any extra joins required to support this property. Generally a no
   * operation except for a OneToOne exported.
   */
  @Override
  public void appendFrom(DbSqlContext ctx, SqlJoinType joinType) {
    if (formula && sqlFormulaJoin != null) {
      ctx.appendFormulaJoin(sqlFormulaJoin, joinType);
    } else if (secondaryTableJoin != null) {
      String relativePrefix = ctx.getRelativePrefix(secondaryTableJoinPrefix);
      secondaryTableJoin.addJoin(joinType, relativePrefix, ctx);
    }
  }

  /**
   * Returns null unless this property is using a secondary table. In that
   * case this returns the logical property prefix.
   */
  public String getSecondaryTableJoinPrefix() {
    return secondaryTableJoinPrefix;
  }

  @Override
  public boolean isAggregation() {
    return aggregation != null;
  }

  @Override
  public void appendSelect(DbSqlContext ctx, boolean subQuery) {
    if (aggregation != null) {
      ctx.appendFormulaSelect(aggregation);
    } else if (formula) {
      ctx.appendFormulaSelect(sqlFormulaSelect);

    } else if (!isTransient && !ignoreDraftOnlyProperty(ctx.isDraftQuery())) {

      if (secondaryTableJoin != null) {
        ctx.pushTableAlias(ctx.getRelativePrefix(secondaryTableJoinPrefix));
      }
      if (dbEncrypted) {
        ctx.appendRawColumn(getDecryptSqlWithColumnAlias(ctx.peekTableAlias()));
        ctx.addEncryptedProp(this);
      } else {
        ctx.appendColumn(dbColumn);
      }
      if (secondaryTableJoin != null) {
        ctx.popTableAlias();
      }
    }
  }

  @Override
  public boolean isMany() {
    return false;
  }

  public boolean isAssignableFrom(Class<?> type) {
    return owningType.isAssignableFrom(type);
  }

  @Override
  public void loadIgnore(DbReadContext ctx) {
    scalarType.loadIgnore(ctx.getDataReader());
  }

  @Override
  public void load(SqlBeanLoad sqlBeanLoad) {
    sqlBeanLoad.load(this);
  }

  @Override
  public void buildRawSqlSelectChain(String prefix, List<String> selectChain) {
    if (prefix == null) {
      selectChain.add(name);
    } else {
      selectChain.add(prefix + "." + name);
    }
  }

  @Override
  public Object read(DataReader reader) throws SQLException {
    return scalarType.read(reader);
  }

  public Object readSet(DataReader reader, EntityBean bean) throws SQLException {
    try {
      Object value = scalarType.read(reader);
      if (bean != null) {
        setValue(bean, value);
      }
      return value;
    } catch (Exception e) {
      throw new PersistenceException("Error readSet on " + descriptor + "." + name, e);
    }
  }

  public Object read(DbReadContext ctx) throws SQLException {
    return scalarType.read(ctx.getDataReader());
  }
  
  public MutableValue readMutable(DbReadContext ctx) throws SQLException {
    return scalarType.readMutable(ctx.getDataReader());
  }

  public Object readSet(DbReadContext ctx, EntityBean bean) throws SQLException {
    try {
      Object value = scalarType.read(ctx.getDataReader());
      if (bean != null) {
        setValue(bean, value);
      }
      return value;
    } catch (Exception e) {
      throw new PersistenceException("Error readSet on " + descriptor + "." + name, e);
    }
  }

  @SuppressWarnings("unchecked")
  public void bind(DataBind b, Object value) throws SQLException {
    scalarType.bind(b, value);
  }

  @SuppressWarnings(value = "unchecked")
  public void writeData(DataOutput dataOutput, Object value) throws IOException {
    scalarType.writeData(dataOutput, value);
  }

  public Object readData(DataInput dataInput) throws IOException {
    return scalarType.readData(dataInput);
  }

  @Override
  public BeanProperty getBeanProperty() {
    return this;
  }

  @Override
  public Property getProperty() {
    return this;
  }

  /**
   * Return true if this object is part of an inheritance hierarchy.
   */
  private boolean isInherited() {
    return inherited;
  }

  /**
   * Return true is this type is not from a super type.
   */
  public boolean isLocal() {
    return local;
  }

  /**
   * Copy/set the property value from the draft bean to the live bean.
   */
  public void publish(EntityBean draftBean, EntityBean liveBean) {
    if (!version && !draftOnly) {
      // set property value from draft to live
      Object value = getValueIntercept(draftBean);
      setValueIntercept(liveBean, value);
    }
  }

  /**
   * Return the DB literal expression to set the deleted state to true.
   */
  String getSoftDeleteDbSet() {
    return softDeleteDbSet;
  }

  /**
   * Return the DB literal predicate used to filter out soft deleted rows from a query.
   */
  String getSoftDeleteDbPredicate(String tableAlias) {
    return tableAlias + softDeleteDbPredicate;
  }

  /**
   * Set the soft delete property value on the bean without invoking lazy loading.
   */
  void setSoftDeleteValue(EntityBean bean) {
    // assumes boolean deleted true being set which is ok limitation for now
    setValue(bean, true);
    bean._ebean_getIntercept().setChangedProperty(propertyIndex);
  }

  /**
   * Set the changed value without invoking interception (lazy loading etc).
   * Typically used to set generated values on update.
   */
  public void setValueChanged(EntityBean bean, Object value) {
    setValue(bean, value);
    bean._ebean_getIntercept().setChangedProperty(propertyIndex);
  }

  /**
   * Add the tenantId predicate to the query.
   */
  public void addTenant(SpiQuery<?> query, Object tenantId) {
    query.where().eq(name, tenantId);
  }

  /**
   * Set the tenantId onto the bean.
   */
  public void setTenantValue(EntityBean entityBean, Object tenantId) {
    setValue(entityBean, tenantId);
  }

  /**
   * Set the value of the property without interception or
   * PropertyChangeSupport.
   */
  public void setValue(EntityBean bean, Object value) {
    try {
      setter.set(bean, value);
    } catch (Exception ex) {
      throw new RuntimeException(setterErrorMsg(bean, value, "set "), ex);
    }
  }
  
  /**
   * Set the value of the property.
   */
  public void setValueIntercept(EntityBean bean, Object value) {
    try {
      setter.setIntercept(bean, value);
    } catch (Exception ex) {
      throw new RuntimeException(setterErrorMsg(bean, value, "setIntercept "), ex);
    }
  }

  public void setMutableOrigValue(EntityBean bean, MutableValue mutableValue) {
    bean._ebean_getIntercept().setOriginalValue(propertyIndex, mutableValue);
  }
  
  /**
   * Return an error message when calling a setter.
   */
  private String setterErrorMsg(EntityBean bean, Object value, String prefix) {
    String beanType = bean == null ? "null" : bean.getClass().getName();
    return prefix + name + " on [" + descriptor + "] arg[" + value + "] type[" + beanType + "] threw error";
  }

  /**
   * Return true if this property should be included in the cache bean data.
   */
  public boolean isCacheDataInclude() {
    return true;
  }

  /**
   * Return the value for this property which we hold in the L2 cache entry.
   * <p>
   * This uses format() where possible to store the value as a string and this
   * is done to make any resulting Java object serialisation content smaller as
   * strings get special treatment.
   * </p>
   */
  public Object getCacheDataValue(EntityBean bean) {
    return cacheDataConvert(getValue(bean));
  }

  /**
   * Return the bean cache value for this property using original values.
   */
  public Object getCacheDataValueOrig(EntityBeanIntercept ebi) {
    return cacheDataConvert(ebi.getOrigValue(propertyIndex));
  }

  private Object cacheDataConvert(Object value) {
    if (value == null || scalarType.isBinaryType()) {
      return value;
    } else {
      // convert to string as an optimisation for java object serialisation
      return scalarType.format(value);
    }
  }

  /**
   * Return the value in String format (for bean cache key).
   */
  public String format(Object value) {
    return scalarType.format(value);
  }

  /**
   * Return the value from String format into Object value.
   */
  public Object parse(String value) {
    return scalarType.parse(value);
  }

  /**
   * Read the value for this property from L2 cache entry and set it to the bean.
   * <p>
   * This uses parse() as per the comment in getCacheDataValue().
   * </p>
   */
  public void setCacheDataValue(EntityBean bean, Object cacheData, PersistenceContext context) {
    if (cacheData instanceof String) {
      // parse back from string to support optimisation of java object serialisation
      cacheData = scalarType.parse((String) cacheData);
    }
    setValue(bean, cacheData);
  }

  /**
   * Return the cache key value for this property.
   */
  Object naturalKeyVal(Map<String, Object> values) {
    return values.get(name);
  }

  @Override
  public Object getVal(Object bean) {
    return getValue((EntityBean) bean);
  }

  /**
   * Return the value of the property method.
   */
  public Object getValue(EntityBean bean) {
    try {
      return getter.get(bean);
    } catch (Exception ex) {
      String beanType = bean == null ? "null" : bean.getClass().getName();
      String msg = "get " + name + " on [" + descriptor + "] type[" + beanType + "] threw error.";
      throw new RuntimeException(msg, ex);
    }
  }

  public Object getValueIntercept(EntityBean bean) {
    try {
      return getter.getIntercept(bean);
    } catch (Exception ex) {
      String beanType = bean == null ? "null" : bean.getClass().getName();
      String msg = "getIntercept " + name + " on [" + descriptor + "] type[" + beanType + "] threw error.";
      throw new RuntimeException(msg, ex);
    }
  }

  @Override
  public Object convert(Object value) {
    if (value == null) {
      return null;
    }
    return convertToLogicalType(value);
  }

  @Override
  public void pathSet(Object bean, Object value) {
    if (bean != null) {
      Object logicalVal = convertToLogicalType(value);
      setValueIntercept((EntityBean) bean, logicalVal);
    }
  }

  @Override
  public Object pathGet(Object bean) {
    if (bean == null) {
      return null;
    }
    return getValueIntercept((EntityBean) bean);
  }

  @Override
  public Object pathGetNested(Object bean) {
    throw new RuntimeException("Not expected to call this");
  }

  /**
   * Return the name of the property.
   */
  @Override @Nonnull
  public String getName() {
    return name;
  }

  /**
   * Return the position of this property in the enhanced bean.
   */
  public int getPropertyIndex() {
    return propertyIndex;
  }

  @Override
  public String getElName() {
    return name;
  }

  @Override
  public boolean containsFormulaWithJoin() {
    return formula && sqlFormulaJoin != null;
  }

  @Override
  public boolean containsManySince(String sinceProperty) {
    return containsMany();
  }

  @Override
  public boolean containsMany() {
    return aggregation != null;
  }

  @Override
  public String getAssocIsEmpty(SpiExpressionRequest request, String path) {
    // overridden in BanePropertyAssocMany
    throw new RuntimeException("Not Supported or Expected");
  }

  @Override
  public Object[] getAssocIdValues(EntityBean bean) {
    // Returns null as not an AssocOne.
    return null;
  }

  @Override
  public String getAssocIdExpression(String prefix, String operator) {
    // Returns null as not an AssocOne.
    return null;
  }

  @Override
  public String getAssocIdInExpr(String prefix) {
    // Returns null as not an AssocOne.
    return null;
  }

  @Override
  public String getAssocIdInValueExpr(boolean not, int size) {
    // Returns null as not an AssocOne.
    return null;
  }

  /**
   * If true this bean maps to the primary key.
   */
  public boolean isImportedPrimaryKey() {
    return importedPrimaryKey;
  }

  @Override
  public boolean isAssocMany() {
    // Returns false - override in BeanPropertyAssocMany.
    return false;
  }

  @Override
  public boolean isAssocId() {
    // Returns false - override in BeanPropertyAssocOne.
    return false;
  }

  @Override
  public boolean isAssocProperty() {
    // Returns false - override in BeanPropertyAssocOne.
    return false;
  }

  @Override
  public String getElPlaceholder(boolean encrypted) {
    return encrypted ? elPlaceHolderEncrypted : elPlaceHolder;
  }

  @Override
  public String getElPrefix() {
    return elPrefix;
  }

  /**
   * Return the full name of this property.
   */
  @Override
  public String getFullBeanName() {
    return descriptor.getFullName() + "." + name;
  }

  /**
   * Return true if the mutable value is considered dirty.
   * This is only used for 'mutable' scalar types like hstore or json etc.
   */
  boolean isDirtyValue(EntityBeanIntercept ebi, Object value) {
    return ebi.isDirtyValue(propertyIndex, value);
  }

  /**
   * Return the scalarType.
   */
  @Override
  @SuppressWarnings(value = "unchecked")
  public ScalarType<Object> getScalarType() {
    return scalarType;
  }

  @Override
  public StringParser getStringParser() {
    return scalarType;
  }

  @Override
  public boolean isDateTimeCapable() {
    return scalarType != null && scalarType.isDateTimeCapable();
  }

  @Override
  public int getJdbcType() {
    return scalarType == null ? 0 : scalarType.getJdbcType();
  }

  @Override
  public Object parseDateTime(long systemTimeMillis) {
    return scalarType.convertFromMillis(systemTimeMillis);
  }

  /**
   * Return the DB max length (varchar) or precision (decimal).
   */
  public int getDbLength() {
    return dbLength;
  }

  /**
   * Return the DB scale for numeric columns.
   */
  public int getDbScale() {
    return dbScale;
  }

  /**
   * Return a specific column DDL definition if specified (otherwise null).
   */
  public String getDbColumnDefn() {
    return dbColumnDefn;
  }

  /**
   * Return the DB constraint expression (can be null).
   * <p>
   * For an Enum returns IN expression for the set of Enum values.
   * </p>
   */
  public Set<String> getDbCheckConstraintValues() {
    if (scalarType instanceof ScalarTypeEnum) {
      return ((ScalarTypeEnum<?>) scalarType).getDbCheckConstraintValues();
    }
    return null;
  }

  /**
   * Return the DB column type definition.
   */
  public String renderDbType(DbPlatformType dbType, boolean strict) {
    if (dbColumnDefn != null) {
      return dbColumnDefn;
    }
    return dbType.renderType(dbLength, dbScale, strict);
  }

  /**
   * Return the DB column default to use for DDL.
   */
  public String getDbColumnDefault() {
    return dbColumnDefn != null ? null : dbColumnDefault;
  }

  /**
   * Return the DDL-Migration Infos
   */
  public List<DbMigrationInfo> getDbMigrationInfos() {
    return dbMigrationInfos;
  }

  /**
   * Return the bean Field associated with this property.
   */
  private Field getField() {
    return field;
  }

  /**
   * Return the GeneratedValue. Used to generate update timestamp etc.
   */
  public GeneratedProperty getGeneratedProperty() {
    return generatedProperty;
  }

  /**
   * Return true if this is the natural key property.
   */
  public boolean isNaturalKey() {
    return naturalKey;
  }

  /**
   * Return true if this property is mandatory.
   */
  public boolean isNullable() {
    return nullable;
  }

  /**
   * Return true if DDL Not NULL constraint should be defined for this column
   * based on it being a version column or having a generated property.
   */
  public boolean isDDLNotNull() {
    return isVersion() || (generatedProperty != null && generatedProperty.isDDLNotNullable());
  }

  boolean isGeneratedOnInsert() {
    return generatedProperty != null && generatedProperty.includeInInsert();
  }

  /**
   * Return true if this is a generated property mapping to @WhenCreated or @CreatedTimestamp.
   */
  boolean isGeneratedWhenCreated() {
    return generatedProperty instanceof GeneratedWhenCreated;
  }

  /**
   * Return true if this is a generated property mapping to @WhenModified or @UpdatedTimestamp.
   */
  boolean isGeneratedWhenModified() {
    return generatedProperty instanceof GeneratedWhenModified;
  }

  /**
   * Return true if this is a generated or Id property.
   */
  public boolean isGenerated() {
    return id || generatedProperty != null;
  }

  /**
   * Return true if the DB column should be unique.
   */
  public boolean isUnique() {
    return unique;
  }

  /**
   * Return true if the property is transient.
   */
  public boolean isTransient() {
    return isTransient;
  }

  /**
   * Return true if this property is loadable from a resultSet.
   */
  public boolean isLoadProperty(boolean draftQuery) {
    return !ignoreDraftOnlyProperty(draftQuery) && (!isTransient || formula);
  }

  /**
   * Return true if this is a draftOnly property on a non-asDraft query and as such this
   * property should not be included in a sql query.
   */
  private boolean ignoreDraftOnlyProperty(boolean draftQuery) {
    return draftOnly && !draftQuery;
  }

  /**
   * Return true if this is a version column used for concurrency checking.
   */
  public boolean isVersion() {
    return version;
  }

  /**
   * The database column name this is mapped to.
   */
  @Override
  public String getDbColumn() {
    return dbColumn;
  }

  /**
   * Return the comment for the associated DB column.
   */
  public String getDbComment() {
    return dbComment;
  }

  /**
   * Return the database jdbc data type this is mapped to.
   *
   * @param platformTypes Set as false when we want logical platform agnostic types.
   */
  public int getDbType(boolean platformTypes) {
    if (platformTypes || !(scalarType instanceof ScalarTypeLogicalType)) {
      return dbType;
    }
    return ((ScalarTypeLogicalType) scalarType).getLogicalType();
  }

  /**
   * Perform DB to Logical type conversion (if necessary).
   */
  private Object convertToLogicalType(Object value) {
    if (scalarType != null) {
      return scalarType.toBeanType(value);
    }
    return value;
  }

  /**
   * Return true if by default this property is set to fetch eager.
   * Lob's usually default to fetch lazy.
   */
  public boolean isFetchEager() {
    return fetchEager;
  }

  /**
   * Return true if this is mapped to a Clob Blob LongVarchar or
   * LongVarbinary.
   */
  public boolean isLob() {
    return lob;
  }

  public static boolean isLobType(int type) {
    switch (type) {
      case Types.CLOB:
      case Types.BLOB:
      case Types.LONGVARCHAR:
      case Types.LONGVARBINARY:
        return true;
      default:
        return false;
    }
  }

  /**
   * Return the DB bind parameter. Typically is "?" but different for
   * encrypted bind.
   */
  public String getDbBind() {
    return dbBind;
  }

  @Override
  public Object localEncrypt(Object value) {
    return ((LocalEncryptedType)scalarType).localEncrypt(value);
  }

  /**
   * Returns true if DB encrypted.
   */
  @Override
  public boolean isLocalEncrypted() {
    return localEncrypted;
  }

  /**
   * Return true if this property is stored encrypted.
   */
  @Override
  public boolean isDbEncrypted() {
    return dbEncrypted;
  }

  public int getDbEncryptedType() {
    return dbEncryptedType;
  }

  /**
   * Return true if this property is excluded from history.
   */
  public boolean isExcludedFromHistory() {
    return excludedFromHistory;
  }

  /**
   * Return true if this property hold unmapped JSON.
   */
  public boolean isUnmappedJson() {
    return unmappedJson;
  }

  /**
   * Return true if this is the tenantId property (for multi-tenant partitioning).
   */
  public boolean isTenantId() {
    return tenantId;
  }

  /**
   * Return true if this property only exists on the draft table.
   */
  public boolean isDraftOnly() {
    return draftOnly;
  }

  /**
   * Return true if this property is a boolean flag on a draftable bean
   * indicating if the instance is a draft or live bean.
   */
  public boolean isDraft() {
    return draft;
  }

  /**
   * Return true if this property is a boolean flag only on the draft table
   * indicating that when the draft is different from the published row.
   */
  public boolean isDraftDirty() {
    return draftDirty;
  }

  /**
   * Return true if this property is reset/cleared on publish (on the draft bean).
   */
  boolean isDraftReset() {
    return draftReset;
  }

  /**
   * Return true if this property is the soft delete property.
   */
  public boolean isSoftDelete() {
    return softDelete;
  }

  /**
   * Return true if this property should be included in an Insert.
   */
  public boolean isDbInsertable() {
    return dbInsertable;
  }

  /**
   * Return true if this property should be included in an Update.
   */
  public boolean isDbUpdatable() {
    return dbUpdatable;
  }

  /**
   * Return true if this property is included in database queries.
   */
  private boolean isDbRead() {
    return dbRead;
  }

  /**
   * Return true if this property is based on a secondary table (not the base
   * table).
   */
  public boolean isSecondaryTable() {
    return secondaryTable;
  }

  /**
   * Return the property type.
   */
  @Override @Nonnull
  public Class<?> getPropertyType() {
    return propertyType;
  }

  /**
   * Return true if this is included in the unique id.
   */
  @Override
  public boolean isId() {
    return id;
  }

  /**
   * Return true if this is an Embedded property. In this case it shares the
   * table and primary key of its owner object.
   */
  @Override
  public boolean isEmbedded() {
    return embedded;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Append this property to the document store based on includeByDefault setting.
   */
  public void docStoreInclude(boolean includeByDefault, DocStructure docStructure) {
    if (includeByDefault) {
      docStructure.addProperty(name);
    }
  }

  public boolean isJsonSerialize() {
    return jsonSerialize;
  }

  /**
   * JSON write the property for 'insert only depth'.
   */
  public void jsonWriteForInsert(SpiJsonWriter writeJson, EntityBean bean) throws IOException {
    if (!jsonSerialize) {
      return;
    }
    Object value = getValue(bean);
    if (value != null) {
      jsonWriteScalar(writeJson, value);
    }
  }

  /**
   * JSON write the property value.
   */
  public void jsonWriteValue(SpiJsonWriter writeJson, Object value) throws IOException {
    if (!jsonSerialize) {
      return;
    }
    jsonWriteVal(writeJson, value);
  }

  /**
   * JSON write the bean property.
   */
  public void jsonWrite(SpiJsonWriter writeJson, EntityBean bean) throws IOException {
    if (!jsonSerialize) {
      return;
    }
    jsonWriteVal(writeJson, getValueIntercept(bean));
  }

  private void jsonWriteVal(SpiJsonWriter writeJson, Object value) throws IOException {
    if (value == null) {
      writeJson.writeNullField(name);
    } else {
      jsonWriteScalar(writeJson, value);
    }
  }

  @SuppressWarnings("unchecked")
  private void jsonWriteScalar(SpiJsonWriter writeJson, Object value) throws IOException {
    if (scalarType != null) {
      writeJson.writeFieldName(name);
      scalarType.jsonWrite(writeJson.gen(), value);
    } else {
      writeJson.writeValueUsingObjectMapper(name, value);
    }
  }

  public void jsonRead(SpiJsonReader ctx, EntityBean bean) throws IOException {
    JsonToken event = ctx.nextToken();
    if (JsonToken.VALUE_NULL == event) {
      if (jsonDeserialize) {
        setValue(bean, null);
      }
    } else {
      // expect to read non-null json value
      Object objValue;
      if (scalarType != null) {
        objValue = scalarType.jsonRead(ctx.getParser());
      } else {
        try {
          objValue = ctx.readValueUsingObjectMapper(propertyType);
        } catch (IOException e) {
          // change in behavior for #318
          objValue = null;
          String msg = "Error trying to use Jackson ObjectMapper to read transient property "
            + getFullBeanName() + " - consider marking this property with @JsonIgnore";
          logger.error(msg, e);
        }
      }
      if (jsonDeserialize) {
        setValue(bean, objValue);
      }
    }
  }

  /**
   * Populate diff map comparing the property values between the beans.
   */
  public void diff(String prefix, Map<String, ValuePair> map, EntityBean newBean, EntityBean oldBean) {
    Object newVal = (newBean == null) ? null : getValue(newBean);
    Object oldVal = (oldBean == null) ? null : getValue(oldBean);
    diffVal(prefix, map, newVal, oldVal);
  }

  /**
   * Populate diff map comparing the property values.
   */
  void diffVal(String prefix, Map<String, ValuePair> map, Object newVal, Object oldVal) {
    if (!ValueUtil.areEqual(newVal, oldVal)) {
      String propName = (prefix == null) ? name : prefix + "." + name;
      map.put(propName, new ValuePair(newVal, oldVal));
    }
  }

  /**
   * Add to the document mapping if this property is included for this index.
   */
  public void docStoreMapping(DocMappingBuilder mapping, String prefix) {
    if (mapping.includesProperty(prefix, name)) {
      DocPropertyType type = scalarType.getDocType();
      DocPropertyOptions options = docOptions.copy();
      if (isKeywordType(type, options)) {
        type = DocPropertyType.KEYWORD;
      }
      mapping.add(new DocPropertyMapping(name, type, options));
    }
  }

  private boolean isKeywordType(DocPropertyType type, DocPropertyOptions docOptions) {
    return type == DocPropertyType.TEXT && (docOptions.isCode() || id || discriminator);
  }

  public void merge(EntityBean bean, EntityBean existing) {
    // do nothing unless Many property
  }

  public void registerColumn(BeanDescriptor<?> desc, String prefix) {
    String path = SplitName.add(prefix, name);
    if (formula && dbColumn != null) {
      // trim off table alias placeholder if found
      String[] split = dbColumn.split("}");
      if (split.length == 2) {
        desc.registerColumn(split[1], path);
      } else {
        desc.registerColumn(dbColumn, path);
      }
    } else if (dbColumn != null) {
      desc.registerColumn(dbColumn, path);
    }
  }
}
