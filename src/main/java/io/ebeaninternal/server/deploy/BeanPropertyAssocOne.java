package io.ebeaninternal.server.deploy;

import io.ebean.EbeanServer;
import io.ebean.Query;
import io.ebean.SqlUpdate;
import io.ebean.Transaction;
import io.ebean.ValuePair;
import io.ebean.bean.EntityBean;
import io.ebean.bean.PersistenceContext;
import io.ebeaninternal.server.cache.CacheChangeSet;
import io.ebeaninternal.server.cache.CachedBeanData;
import io.ebeaninternal.server.core.DefaultSqlUpdate;
import io.ebeaninternal.server.deploy.id.ImportedId;
import io.ebeaninternal.server.deploy.meta.DeployBeanPropertyAssocOne;
import io.ebeaninternal.server.el.ElPropertyChainBuilder;
import io.ebeaninternal.server.el.ElPropertyValue;
import io.ebeaninternal.server.query.SplitName;
import io.ebeaninternal.server.query.SqlBeanLoad;
import io.ebeaninternal.server.query.SqlJoinType;
import io.ebeaninternal.server.text.json.ReadJson;
import io.ebeaninternal.server.text.json.WriteJson;

import javax.persistence.PersistenceException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Property mapped to a joined bean.
 */
public class BeanPropertyAssocOne<T> extends BeanPropertyAssoc<T> {

  private final boolean oneToOne;

  private final boolean oneToOneExported;

  private final boolean importedPrimaryKey;

  private AssocOneHelp localHelp;

  protected final BeanProperty[] embeddedProps;

  private final HashMap<String, BeanProperty> embeddedPropsMap;

  /**
   * The information for Imported foreign Keys.
   */
  protected ImportedId importedId;

  private String deleteByParentIdSql;
  private String deleteByParentIdInSql;

  private BeanPropertyAssocMany<?> relationshipProperty;

  /**
   * Create based on deploy information of an EmbeddedId.
   */
  public BeanPropertyAssocOne(BeanDescriptorMap owner, DeployBeanPropertyAssocOne<T> deploy) {
    this(owner, null, deploy);
  }

  /**
   * Create the property.
   */
  public BeanPropertyAssocOne(BeanDescriptorMap owner, BeanDescriptor<?> descriptor,
                              DeployBeanPropertyAssocOne<T> deploy) {

    super(descriptor, deploy);

    importedPrimaryKey = deploy.isImportedPrimaryKey();
    oneToOne = deploy.isOneToOne();
    oneToOneExported = deploy.isOneToOneExported();

    if (embedded) {
      // Overriding of the columns and use table alias of owning BeanDescriptor
      BeanEmbeddedMeta overrideMeta = BeanEmbeddedMetaFactory.create(owner, deploy);
      embeddedProps = overrideMeta.getProperties();
      embeddedPropsMap = new HashMap<>();
      for (BeanProperty embeddedProp : embeddedProps) {
        embeddedPropsMap.put(embeddedProp.getName(), embeddedProp);
      }

    } else {
      embeddedProps = null;
      embeddedPropsMap = null;
    }
  }

  @Override
  public void initialise() {
    super.initialise();
    localHelp = createHelp(embedded, oneToOneExported);

    if (!isTransient) {
      //noinspection StatementWithEmptyBody
      if (embedded) {
        // no imported or exported information
      } else if (!oneToOneExported) {
        importedId = createImportedId(this, targetDescriptor, tableJoin);
        if (importedId.isScalar()) {
          // limit JoinColumn mapping to the @Id / primary key
          TableJoinColumn[] columns = tableJoin.columns();
          String foreignJoinColumn = columns[0].getForeignDbColumn();
          String foreignIdColumn = targetDescriptor.getIdProperty().getDbColumn();
          if (!foreignJoinColumn.equalsIgnoreCase(foreignIdColumn)) {
            throw new PersistenceException("Mapping limitation - @JoinColumn on " + getFullBeanName() + " needs to map to a primary key as per Issue #529 "
              + " - joining to " + foreignJoinColumn + " and not " + foreignIdColumn);
          }
        }

      } else {
        exportedProperties = createExported();

        String delStmt = "delete from " + targetDescriptor.getBaseTable() + " where ";
        deleteByParentIdSql = delStmt + deriveWhereParentIdSql(false);
        deleteByParentIdInSql = delStmt + deriveWhereParentIdSql(true);
      }
    }
  }

  /**
   * Return the property value as an entity bean.
   */
  public EntityBean getValueAsEntityBean(EntityBean owner) {
    return (EntityBean) getValue(owner);
  }

  void setRelationshipProperty(BeanPropertyAssocMany<?> relationshipProperty) {
    this.relationshipProperty = relationshipProperty;
  }

  /**
   * Return true if this relationship needs to maintain/update L2 cache.
   */
  boolean isCacheNotify() {
    return targetDescriptor.isBeanCaching() && relationshipProperty != null;
  }

  /**
   * Clear the L2 relationship cache for this property.
   */
  void cacheClear() {
    if (isCacheNotify()) {
      targetDescriptor.cacheManyPropClear(relationshipProperty.getName());
    }
  }

  /**
   * Clear part of the L2 relationship cache for this property.
   */
  void cacheDelete(boolean clear, EntityBean bean, CacheChangeSet changeSet) {

    if (isCacheNotify()) {
      if (clear) {
        changeSet.addManyClear(targetDescriptor, relationshipProperty.getName());
      } else {
        Object assocBean = getValue(bean);
        if (assocBean != null) {
          Object parentId = targetDescriptor.getId((EntityBean) assocBean);
          if (parentId != null) {
            changeSet.addManyRemove(targetDescriptor, relationshipProperty.getName(), parentId);
          }
        }
      }
    }
  }

  @Override
  public ElPropertyValue buildElPropertyValue(String propName, String remainder, ElPropertyChainBuilder chain, boolean propertyDeploy) {

    if (embedded) {
      BeanProperty embProp = embeddedPropsMap.get(remainder);
      if (embProp == null) {
        String msg = "Embedded Property " + remainder + " not found in " + getFullBeanName();
        throw new PersistenceException(msg);
      }
      if (chain == null) {
        chain = new ElPropertyChainBuilder(true, propName);
      }
      chain.add(this);
      chain.setEmbedded(true);

      return chain.add(embProp).build();
    }

    return createElPropertyValue(propName, remainder, chain, propertyDeploy);
  }

  @Override
  public String getElPlaceholder(boolean encrypted) {
    return encrypted ? elPlaceHolderEncrypted : elPlaceHolder;
  }

  public SqlUpdate deleteByParentId(Object parentId, List<Object> parentIdist) {
    if (parentId != null) {
      return deleteByParentId(parentId);
    } else {
      return deleteByParentIdList(parentIdist);
    }
  }

  private SqlUpdate deleteByParentIdList(List<Object> parentIdist) {

    StringBuilder sb = new StringBuilder(100);
    sb.append(deleteByParentIdInSql);

    String inClause = targetIdBinder.getIdInValueExpr(parentIdist.size());
    sb.append(inClause);

    DefaultSqlUpdate delete = new DefaultSqlUpdate(sb.toString());
    for (Object aParentIdist : parentIdist) {
      targetIdBinder.bindId(delete, aParentIdist);
    }

    return delete;
  }

  private SqlUpdate deleteByParentId(Object parentId) {

    DefaultSqlUpdate delete = new DefaultSqlUpdate(deleteByParentIdSql);
    if (exportedProperties.length == 1) {
      delete.addParameter(parentId);
    } else {
      targetDescriptor.getIdBinder().bindId(delete, parentId);
    }
    return delete;
  }

  public List<Object> findIdsByParentId(Object parentId, List<Object> parentIdist, Transaction t) {
    if (parentId != null) {
      return findIdsByParentId(parentId, t);
    } else {
      return findIdsByParentIdList(parentIdist, t);
    }
  }

  private List<Object> findIdsByParentId(Object parentId, Transaction t) {

    String rawWhere = deriveWhereParentIdSql(false);

    List<Object> bindValues = new ArrayList<>();
    bindWhereParentId(bindValues, parentId);

    EbeanServer server = getBeanDescriptor().getEbeanServer();
    Query<?> q = server.find(getPropertyType())
      .where()
      .raw(rawWhere, bindValues.toArray())
      .query();

    return server.findIds(q, t);
  }

  private List<Object> findIdsByParentIdList(List<Object> parentIdList, Transaction t) {

    String rawWhere = deriveWhereParentIdSql(true);
    String inClause = targetIdBinder.getIdInValueExpr(parentIdList.size());

    String expr = rawWhere + inClause;

    List<Object> bindValues = new ArrayList<>();
    for (Object aParentIdList : parentIdList) {
      bindWhereParentId(bindValues, aParentIdList);
    }

    EbeanServer server = getBeanDescriptor().getEbeanServer();
    Query<?> q = server.find(getPropertyType())
      .where().raw(expr, bindValues.toArray()).query();

    return server.findIds(q, t);
  }

  void addFkey() {
    if (importedId != null) {
      importedId.addFkeys(name);
    }
  }

  @Override
  public void registerColumn(BeanDescriptor<?> desc, String prefix) {
    if (embedded) {
      for (BeanProperty prop : embeddedProps) {
        prop.registerColumn(desc, SplitName.add(prefix, name));
      }
    } else {
      if (targetIdProperty != null) {
        BeanDescriptor<T> target = getTargetDescriptor();
        String basePath = SplitName.add(prefix, name);
        if (dbColumn != null) {
          BeanProperty idProperty = target.getIdProperty();
          desc.registerColumn(dbColumn, SplitName.add(basePath, idProperty.getName()));
        }

        desc.registerTable(target.getBaseTable(), this);
      }
    }
  }

  /**
   * Return meta data for the deployment of the embedded bean specific to this
   * property.
   */
  public BeanProperty[] getProperties() {
    return embeddedProps;
  }

  @Override
  public void buildRawSqlSelectChain(String prefix, List<String> selectChain) {

    prefix = SplitName.add(prefix, name);

    if (!embedded) {
      InheritInfo inheritInfo = targetDescriptor.getInheritInfo();
      if (inheritInfo != null) {
        // expect the discriminator column to be included in order
        // to determine the inheritance type so we add it to the
        // selectChain (so that it takes a position in the resultSet)
        String discriminatorColumn = inheritInfo.getDiscriminatorColumn();
        String discProperty = prefix + "." + discriminatorColumn;
        selectChain.add(discProperty);
      }
      targetIdBinder.buildRawSqlSelectChain(prefix, selectChain);

    } else {
      for (BeanProperty embeddedProp : embeddedProps) {
        embeddedProp.buildRawSqlSelectChain(prefix, selectChain);
      }
    }
  }

  /**
   * Return true if this a OneToOne property. Otherwise assumed ManyToOne.
   */
  public boolean isOneToOne() {
    return oneToOne;
  }

  /**
   * Return true if this is the exported side of a OneToOne.
   */
  public boolean isOneToOneExported() {
    return oneToOneExported;
  }

  /**
   * If true this bean maps to the primary key.
   */
  public boolean isImportedPrimaryKey() {
    return importedPrimaryKey;
  }

  @Override
  public void diffForInsert(String prefix, Map<String, ValuePair> map, EntityBean newBean) {
    Object newEmb = (newBean == null) ? null : getValue(newBean);
    if (newEmb != null) {
      prefix = (prefix == null) ? name : prefix + "." + name;
      if (embedded) {
        getTargetDescriptor().diffForInsert(prefix, map, (EntityBean) newEmb);
      } else {
        // we are only interested in the Id value
        BeanDescriptor<T> targetDescriptor = getTargetDescriptor();
        BeanProperty idProperty = targetDescriptor.getIdProperty();
        idProperty.diffForInsert(prefix, map, (EntityBean) newEmb);
      }
    }
  }

  @Override
  public void diff(String prefix, Map<String, ValuePair> map, EntityBean newBean, EntityBean oldBean) {

    Object newEmb = (newBean == null) ? null : getValue(newBean);
    Object oldEmb = (oldBean == null) ? null : getValue(oldBean);
    if (newEmb == null && oldEmb == null) {
      return;
    }

    if (embedded) {
      prefix = (prefix == null) ? name : prefix + "." + name;
      BeanDescriptor<T> targetDescriptor = getTargetDescriptor();
      targetDescriptor.diff(prefix, map, (EntityBean) newEmb, (EntityBean) oldEmb);

    } else {
      // we are only interested in the Id value
      newBean = (EntityBean) newEmb;
      oldBean = (EntityBean) oldEmb;

      BeanDescriptor<T> targetDescriptor = getTargetDescriptor();
      BeanProperty idProperty = targetDescriptor.getIdProperty();

      Object newId = (newBean == null) ? null : idProperty.getValue(newBean);
      Object oldId = (oldBean == null) ? null : idProperty.getValue(oldBean);
      if (newId != null || oldId != null) {
        prefix = (prefix == null) ? name : prefix + "." + name;
        idProperty.diffVal(prefix, map, newId, oldId);
      }
    }
  }

  /**
   * Same as getPropertyType(). Return the type of the bean this property
   * represents.
   */
  @Override
  public Class<?> getTargetType() {
    return getPropertyType();
  }

  @Override
  public Object getCacheDataValue(EntityBean bean) {
    Object ap = getValue(bean);
    if (ap == null) {
      return null;
    }
    if (embedded) {
      return targetDescriptor.cacheEmbeddedBeanExtract((EntityBean) ap);
    } else {
      return targetDescriptor.getIdProperty().getCacheDataValue((EntityBean) ap);
    }
  }

  @Override
  public void setCacheDataValue(EntityBean bean, Object cacheData, PersistenceContext context) {
    if (cacheData == null) {
      setValue(bean, null);
    } else {
      if (embedded) {
        setValue(bean, targetDescriptor.cacheEmbeddedBeanLoad((CachedBeanData) cacheData, context));
      } else {
        if (cacheData instanceof String) {
          cacheData = targetDescriptor.getIdProperty().scalarType.parse((String) cacheData);
        }
        // cacheData is the id value, maybe already in persistence context
        Object assocBean = targetDescriptor.contextGet(context, cacheData);
        if (assocBean == null) {
          assocBean = targetDescriptor.createReference(Boolean.FALSE, false, cacheData, context);
        }
        setValue(bean, assocBean);
      }
    }
  }

  /**
   * Return the Id values from the given bean.
   */
  @Override
  public Object[] getAssocIdValues(EntityBean bean) {
    return targetDescriptor.getIdBinder().getIdValues(bean);
  }

  /**
   * Return the Id expression to add to where clause etc.
   */
  @Override
  public String getAssocIdExpression(String prefix, String operator) {
    return targetDescriptor.getIdBinder().getAssocOneIdExpr(prefix, operator);
  }

  /**
   * Return the logical id value expression taking into account embedded id's.
   */
  @Override
  public String getAssocIdInValueExpr(int size) {
    return targetDescriptor.getIdBinder().getIdInValueExpr(size);
  }

  /**
   * Return the logical id in expression taking into account embedded id's.
   */
  @Override
  public String getAssocIdInExpr(String prefix) {
    return targetDescriptor.getIdBinder().getAssocIdInExpr(prefix);
  }

  @Override
  public boolean isAssocId() {
    return !embedded;
  }

  @Override
  public boolean isAssocProperty() {
    return !embedded;
  }


  /**
   * Create a bean of the target type to be used as an embeddedId
   * value.
   */
  public Object createEmbeddedId() {
    return getTargetDescriptor().createEntityBean();
  }

  @Override
  public Object pathGetNested(Object bean) {
    Object value = getValueIntercept((EntityBean) bean);
    if (value == null) {
      value = targetDescriptor.createEntityBean();
      setValueIntercept((EntityBean) bean, value);
    }
    return value;
  }

  public ImportedId getImportedId() {
    return importedId;
  }

  private String deriveWhereParentIdSql(boolean inClause) {

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < exportedProperties.length; i++) {
      String fkColumn = exportedProperties[i].getForeignDbColumn();
      if (i > 0) {
        String s = inClause ? "," : " and ";
        sb.append(s);
      }
      sb.append(fkColumn);
      if (!inClause) {
        sb.append("=? ");
      }
    }
    return sb.toString();
  }

  /**
   * Create the array of ExportedProperty used to build reference objects.
   */
  private ExportedProperty[] createExported() {

    BeanProperty idProp = descriptor.getIdProperty();

    ArrayList<ExportedProperty> list = new ArrayList<>();

    if (idProp != null && idProp.isEmbedded()) {

      BeanPropertyAssocOne<?> one = (BeanPropertyAssocOne<?>) idProp;
      BeanDescriptor<?> targetDesc = one.getTargetDescriptor();
      BeanProperty[] emIds = targetDesc.propertiesBaseScalar();
      try {
        for (BeanProperty emId : emIds) {
          ExportedProperty expProp = findMatch(true, emId);
          list.add(expProp);
        }
      } catch (PersistenceException e) {
        // not found as individual scalar properties
        e.printStackTrace();
      }

    } else {
      if (idProp != null) {
        ExportedProperty expProp = findMatch(false, idProp);
        list.add(expProp);
      }
    }

    return list.toArray(new ExportedProperty[list.size()]);
  }

  /**
   * Find the matching foreignDbColumn for a given local property.
   */
  private ExportedProperty findMatch(boolean embeddedProp, BeanProperty prop) {

    String matchColumn = prop.getDbColumn();

    String searchTable = tableJoin.getTable();
    TableJoinColumn[] columns = tableJoin.columns();

    for (TableJoinColumn column : columns) {
      String matchTo = column.getLocalDbColumn();

      if (matchColumn.equalsIgnoreCase(matchTo)) {
        String foreignCol = column.getForeignDbColumn();
        return new ExportedProperty(embeddedProp, foreignCol, prop);
      }
    }

    String msg = "Error with the Join on [" + getFullBeanName()
      + "]. Could not find the matching foreign key for [" + matchColumn + "] in table[" + searchTable + "]?"
      + " Perhaps using a @JoinColumn with the name/referencedColumnName attributes swapped?";
    throw new PersistenceException(msg);
  }


  @Override
  public void appendSelect(DbSqlContext ctx, boolean subQuery) {
    if (!isTransient) {
      localHelp.appendSelect(ctx, subQuery);
    }
  }

  @Override
  public void appendFrom(DbSqlContext ctx, SqlJoinType joinType) {
    if (!isTransient) {
      localHelp.appendFrom(ctx, joinType);
      if (sqlFormulaJoin != null) {
        ctx.appendFormulaJoin(sqlFormulaJoin, joinType);
      }
    }
  }

  @Override
  public Object readSet(DbReadContext ctx, EntityBean bean) throws SQLException {
    return localHelp.readSet(ctx, bean);
  }

  /**
   * Read the data from the resultSet effectively ignoring it and returning null.
   */
  @Override
  public Object read(DbReadContext ctx) throws SQLException {
    // just read the resultSet incrementing the column index
    // pass in null for the bean so any data read is ignored
    return localHelp.read(ctx);
  }

  @Override
  public void setValue(EntityBean bean, Object value) {
    super.setValue(bean, value);
    if (embedded && value instanceof EntityBean) {
      setEmbeddedOwner(bean, value);
    }
  }

  /**
   * Set the owner on the embedded bean property.
   */
  void setEmbeddedOwner(EntityBean owner) {

    Object emb = getValue(owner);
    if (emb != null) {
      setEmbeddedOwner(owner, emb);
    }
  }

  private void setEmbeddedOwner(EntityBean bean, Object value) {
    ((EntityBean) value)._ebean_getIntercept().setEmbeddedOwner(bean, propertyIndex);
  }

  @Override
  public void setValueIntercept(EntityBean bean, Object value) {
    super.setValueIntercept(bean, value);
    if (embedded && value instanceof EntityBean) {
      setEmbeddedOwner(bean, value);
    }
  }

  @Override
  public void loadIgnore(DbReadContext ctx) {
    localHelp.loadIgnore(ctx);
  }

  @Override
  public void load(SqlBeanLoad sqlBeanLoad) {
    Object dbVal = sqlBeanLoad.load(this);
    if (embedded && sqlBeanLoad.isLazyLoad()) {
      if (dbVal instanceof EntityBean) {
        ((EntityBean) dbVal)._ebean_getIntercept().setLoaded();
      }
    }
  }

  private AssocOneHelp createHelp(boolean embedded, boolean oneToOneExported) {
    if (embedded) {
      return new AssocOneHelpEmbedded(this);
    } else if (oneToOneExported) {
      return new AssocOneHelpRefExported(this);
    } else {
      if (targetInheritInfo != null) {
        return new AssocOneHelpRefInherit(this);
      } else {
        return new AssocOneHelpRefSimple(this);
      }
    }
  }

  @Override
  public void jsonWrite(WriteJson writeJson, EntityBean bean) throws IOException {

    if (!jsonSerialize) {
      return;
    }

    Object value = getValueIntercept(bean);
    if (value == null) {
      writeJson.writeNullField(name);

    } else {
      //noinspection StatementWithEmptyBody
      if (writeJson.isParentBean(value)) {
        // bi-directional and already rendered parent

      } else {
        // Hmmm, not writing complex non-entity bean
        if (value instanceof EntityBean) {
          writeJson.beginAssocOne(name, bean);
          BeanDescriptor<?> refDesc = descriptor.getBeanDescriptor(value.getClass());
          refDesc.jsonWrite(writeJson, (EntityBean) value, name);
          writeJson.endAssocOne();
        }
      }
    }
  }

  @Override
  public void jsonRead(ReadJson readJson, EntityBean bean) throws IOException {
    if (jsonDeserialize && targetDescriptor != null) {
      T assocBean = targetDescriptor.jsonRead(readJson, name);
      setValue(bean, assocBean);
    }
  }

  public boolean isReference(Object detailBean) {
    EntityBean eb = (EntityBean) detailBean;
    return targetDescriptor.isReference(eb._ebean_getIntercept());
  }

  /**
   * Set the parent bean to the child bean if it has not already been set.
   */
  public void setParentBeanToChild(EntityBean parent, EntityBean child) {

    if (mappedBy != null) {
      BeanProperty beanProperty = targetDescriptor.getBeanProperty(mappedBy);
      if (beanProperty != null && beanProperty.getValue(child) == null) {
        // set the 'parent' bean to the 'child' bean
        beanProperty.setValue(child, parent);
      }
    }
  }
}
