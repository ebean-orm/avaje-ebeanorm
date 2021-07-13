package io.ebeaninternal.server.deploy;

import io.ebean.PersistenceContextScope;
import io.ebean.Query;
import io.ebean.SqlUpdate;
import io.ebean.Transaction;
import io.ebean.ValuePair;
import io.ebean.annotation.DocStoreMode;
import io.ebean.bean.BeanCollection;
import io.ebean.bean.EntityBean;
import io.ebean.bean.EntityBeanIntercept;
import io.ebean.bean.PersistenceContext;
import io.ebean.bean.SingleBeanLoader;
import io.ebean.cache.QueryCacheEntry;
import io.ebean.config.DatabaseConfig;
import io.ebean.config.EncryptKey;
import io.ebean.config.dbplatform.IdType;
import io.ebean.config.dbplatform.PlatformIdGenerator;
import io.ebean.core.type.DocPropertyType;
import io.ebean.core.type.ScalarType;
import io.ebean.event.BeanFindController;
import io.ebean.event.BeanPersistController;
import io.ebean.event.BeanPersistListener;
import io.ebean.event.BeanPostConstructListener;
import io.ebean.event.BeanPostLoad;
import io.ebean.event.BeanQueryAdapter;
import io.ebean.event.changelog.BeanChange;
import io.ebean.event.changelog.ChangeLogFilter;
import io.ebean.event.changelog.ChangeType;
import io.ebean.event.readaudit.ReadAuditLogger;
import io.ebean.event.readaudit.ReadAuditPrepare;
import io.ebean.event.readaudit.ReadEvent;
import io.ebean.meta.MetaQueryPlan;
import io.ebean.meta.MetricVisitor;
import io.ebean.meta.QueryPlanInit;
import io.ebean.plugin.BeanDocType;
import io.ebean.plugin.BeanType;
import io.ebean.plugin.ExpressionPath;
import io.ebean.plugin.Property;
import io.ebean.util.SplitName;
import io.ebeaninternal.api.*;
import io.ebeaninternal.api.TransactionEventTable.TableIUD;
import io.ebeaninternal.api.json.SpiJsonReader;
import io.ebeaninternal.api.json.SpiJsonWriter;
import io.ebeaninternal.server.cache.CacheChangeSet;
import io.ebeaninternal.server.cache.CachedBeanData;
import io.ebeaninternal.server.cache.CachedManyIds;
import io.ebeaninternal.server.core.CacheOptions;
import io.ebeaninternal.server.core.DefaultSqlUpdate;
import io.ebeaninternal.server.core.InternString;
import io.ebeaninternal.server.core.PersistRequest;
import io.ebeaninternal.server.core.PersistRequestBean;
import io.ebeaninternal.server.deploy.id.IdBinder;
import io.ebeaninternal.server.deploy.id.IdBinderSimple;
import io.ebeaninternal.server.deploy.id.ImportedId;
import io.ebeaninternal.server.deploy.meta.DeployBeanDescriptor;
import io.ebeaninternal.server.deploy.meta.DeployBeanPropertyLists;
import io.ebeaninternal.server.el.ElComparator;
import io.ebeaninternal.server.el.ElComparatorCompound;
import io.ebeaninternal.server.el.ElComparatorNoop;
import io.ebeaninternal.server.el.ElComparatorProperty;
import io.ebeaninternal.server.el.ElPropertyChainBuilder;
import io.ebeaninternal.server.el.ElPropertyDeploy;
import io.ebeaninternal.server.el.ElPropertyValue;
import io.ebeaninternal.server.persist.DeleteMode;
import io.ebeaninternal.server.query.CQueryPlan;
import io.ebeaninternal.server.query.ExtraJoin;
import io.ebeaninternal.server.query.STreeProperty;
import io.ebeaninternal.server.query.STreePropertyAssoc;
import io.ebeaninternal.server.query.STreePropertyAssocMany;
import io.ebeaninternal.server.query.STreePropertyAssocOne;
import io.ebeaninternal.server.query.STreeType;
import io.ebeaninternal.server.query.SqlBeanLoad;
import io.ebeaninternal.server.querydefn.DefaultOrmQuery;
import io.ebeaninternal.server.querydefn.OrmQueryDetail;
import io.ebeaninternal.server.querydefn.OrmQueryProperties;
import io.ebeaninternal.server.rawsql.SpiRawSql;
import io.ebeaninternal.server.type.DataBind;
import io.ebeaninternal.util.SortByClause;
import io.ebeaninternal.util.SortByClauseParser;
import io.ebeanservice.docstore.api.DocStoreBeanAdapter;
import io.ebeanservice.docstore.api.DocStoreUpdateContext;
import io.ebeanservice.docstore.api.DocStoreUpdates;
import io.ebeanservice.docstore.api.mapping.DocMappingBuilder;
import io.ebeanservice.docstore.api.mapping.DocPropertyMapping;
import io.ebeanservice.docstore.api.mapping.DocumentMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.persistence.PersistenceException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.ebeaninternal.server.persist.DmlUtil.isNullOrZero;

/**
 * Describes Beans including their deployment information.
 */
public class BeanDescriptor<T> implements BeanType<T>, STreeType, SpiBeanType {

  private static final Logger logger = LoggerFactory.getLogger(BeanDescriptor.class);

  private final ConcurrentHashMap<String, SpiUpdatePlan> updatePlanCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<CQueryPlanKey, CQueryPlan> queryPlanCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, ElPropertyValue> elCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, ElPropertyDeploy> elDeployCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, ElComparator<T>> comparatorCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, STreeProperty> dynamicProperty = new ConcurrentHashMap<>();

  private final Map<String, SpiRawSql> namedRawSql;

  private final Map<String, String> namedQuery;

  private final boolean multiValueSupported;
  private boolean batchEscalateOnCascadeInsert;
  private boolean batchEscalateOnCascadeDelete;

  private final BeanIudMetrics iudMetrics;

  public enum EntityType {
    ORM, EMBEDDED, VIEW, SQL, DOC
  }

  /**
   * The nature/type of this bean.
   */
  private final EntityType entityType;

  /**
   * Set when Id property is marked with GeneratedValue annotation.
   */
  private final boolean idGeneratedValue;
  private final PlatformIdGenerator idGenerator;
  private final IdentityMode identityMode;
  private final IdType idType;

  /**
   * SQL used to return last inserted id. Used for Identity columns where
   * getGeneratedKeys is not supported.
   */
  private final String selectLastInsertedId;
  private final String selectLastInsertedIdDraft;

  private final boolean autoTunable;

  /**
   * The concurrency mode for beans of this type.
   */
  private final ConcurrencyMode concurrencyMode;

  private final IndexDefinition[] indexDefinitions;

  private final String[] dependentTables;

  /**
   * The base database table.
   */
  private final String baseTable;
  private final String baseTableAsOf;
  private final String baseTableVersionsBetween;
  private final boolean historySupport;
  private final TableJoin primaryKeyJoin;

  private final BeanProperty softDeleteProperty;
  private final boolean softDelete;

  private final String draftTable;

  private final PartitionMeta partitionMeta;
  private final String storageEngine;

  /**
   * DB table comment.
   */
  private final String dbComment;

  /**
   * Set to true if read auditing is on for this bean type.
   */
  private final boolean readAuditing;

  private final boolean draftable;

  private final boolean draftableElement;

  private final BeanProperty unmappedJson;

  private final BeanProperty tenant;

  private final BeanProperty draft;

  private final BeanProperty draftDirty;

  private final LinkedHashMap<String, BeanProperty> propMap;

  /**
   * Map of DB column to property path (for nativeSql mapping).
   */
  private final Map<String, String> columnPath = new HashMap<>();

  /**
   * Map of related table to assoc property (for nativeSql mapping).
   */
  private final Map<String, BeanPropertyAssoc<?>> tablePath = new HashMap<>();

  final Class<T> beanType;
  final Class<?> rootBeanType;
  private final BeanDescriptorMap owner;
  final String[] properties;

  private final BeanPostLoad beanPostLoad;
  private final BeanPostConstructListener beanPostConstructListener;
  private volatile BeanPersistController persistController;
  private volatile BeanPersistListener persistListener;
  private final BeanQueryAdapter queryAdapter;
  private final BeanFindController beanFinder;
  private final ChangeLogFilter changeLogFilter;

  /**
   * Inheritance information. Server side only.
   */
  final InheritInfo inheritInfo;

  private final boolean abstractType;

  private final BeanProperty idProperty;
  private final int idPropertyIndex;

  private final BeanProperty versionProperty;
  private final int versionPropertyIndex;
  private final BeanProperty whenModifiedProperty;
  private final BeanProperty whenCreatedProperty;

  /**
   * Properties that are initialised in the constructor need to be 'unloaded' to support partial object queries.
   */
  private final int[] unloadProperties;

  /**
   * Properties local to this type (not from a super type).
   */
  private final BeanProperty[] propertiesLocal;

  /**
   * Scalar mutable properties (need to dirty check on update).
   */
  private final BeanProperty[] propertiesMutable;
  private final BeanPropertyAssocOne<?> unidirectional;
  private final BeanProperty orderColumn;

  private final BeanProperty[] propertiesNonMany;
  private final BeanProperty[] propertiesAggregate;
  private final BeanPropertyAssocMany<?>[] propertiesMany;
  private final BeanPropertyAssocMany<?>[] propertiesManySave;
  private final BeanPropertyAssocMany<?>[] propertiesManyDelete;
  private final BeanPropertyAssocMany<?>[] propertiesManyToMany;

  /**
   * list of properties that are associated beans and not embedded (Derived).
   */
  private final BeanPropertyAssocOne<?>[] propertiesOne;
  private final BeanPropertyAssocOne<?>[] propertiesOneImported;
  private final BeanPropertyAssocOne<?>[] propertiesOneImportedSave;
  private final BeanPropertyAssocOne<?>[] propertiesOneImportedDelete;
  private final BeanPropertyAssocOne<?>[] propertiesOneExportedSave;
  private final BeanPropertyAssocOne<?>[] propertiesOneExportedDelete;
  private final BeanPropertyAssocOne<?>[] propertiesEmbedded;

  private final BeanProperty[] propertiesBaseScalar;
  private final BeanProperty[] propertiesTransient;

  /**
   * All non transient properties excluding the id properties.
   */
  private final BeanProperty[] propertiesNonTransient;
  final BeanProperty[] propertiesIndex;
  private final BeanProperty[] propertiesGenInsert;
  private final BeanProperty[] propertiesGenUpdate;
  private final List<BeanProperty[]> propertiesUnique = new ArrayList<>();
  private final boolean idOnlyReference;
  private BeanNaturalKey beanNaturalKey;

  /**
   * The bean class name or the table name for MapBeans.
   */
  private final String fullName;

  /**
   * Flag used to determine if saves can be skipped.
   */
  private boolean saveRecurseSkippable;

  /**
   * Flag used to determine if deletes can be skipped.
   */
  private boolean deleteRecurseSkippable;

  private final EntityBean prototypeEntityBean;

  private final IdBinder idBinder;
  private String idBinderInLHSSql;
  private String idBinderIdSql;
  private String deleteByIdSql;
  private String deleteByIdInSql;
  private String whereIdInSql;
  private String softDeleteByIdSql;
  private String softDeleteByIdInSql;

  private final String name;
  private final String baseTableAlias;
  private final boolean cacheSharableBeans;
  private final String docStoreQueueId;
  private final BeanDescriptorDraftHelp<T> draftHelp;
  private final BeanDescriptorCacheHelp<T> cacheHelp;
  private final BeanDescriptorJsonHelp<T> jsonHelp;
  private DocStoreBeanAdapter<T> docStoreAdapter;
  private DocumentMapping docMapping;
  private boolean docStoreEmbeddedInvalidation;
  private final String defaultSelectClause;
  private SpiEbeanServer ebeanServer;

  public BeanDescriptor(BeanDescriptorMap owner, DeployBeanDescriptor<T> deploy) {
    this.owner = owner;
    this.multiValueSupported = owner.isMultiValueSupported();
    this.entityType = deploy.getEntityType();
    this.properties = deploy.getProperties();
    this.name = InternString.intern(deploy.getName());
    this.baseTableAlias = "t0";
    this.fullName = InternString.intern(deploy.getFullName());
    this.beanType = deploy.getBeanType();
    this.rootBeanType = PersistenceContextUtil.root(beanType);
    this.prototypeEntityBean = createPrototypeEntityBean(beanType);
    this.iudMetrics = new BeanIudMetrics(name);
    this.namedQuery = deploy.getNamedQuery();
    this.namedRawSql = deploy.getNamedRawSql();
    this.inheritInfo = deploy.getInheritInfo();
    this.beanFinder = deploy.getBeanFinder();
    this.persistController = deploy.getPersistController();
    this.persistListener = deploy.getPersistListener();
    this.beanPostConstructListener = deploy.getPostConstructListener();
    this.beanPostLoad = deploy.getPostLoad();
    this.queryAdapter = deploy.getQueryAdapter();
    this.changeLogFilter = deploy.getChangeLogFilter();
    this.defaultSelectClause = deploy.getDefaultSelectClause();
    this.identityMode = deploy.buildIdentityMode();
    this.idType = identityMode.getIdType();
    this.idGeneratedValue = deploy.isIdGeneratedValue();
    this.idGenerator = deploy.getIdGenerator();
    this.selectLastInsertedId = deploy.getSelectLastInsertedId();
    this.selectLastInsertedIdDraft = deploy.getSelectLastInsertedIdDraft();
    this.concurrencyMode = deploy.getConcurrencyMode();
    this.indexDefinitions = deploy.getIndexDefinitions();
    this.readAuditing = deploy.isReadAuditing();
    this.draftable = deploy.isDraftable();
    this.draftableElement = deploy.isDraftableElement();
    this.historySupport = deploy.isHistorySupport();
    this.draftTable = deploy.getDraftTable();
    this.baseTable = InternString.intern(deploy.getBaseTable());
    this.baseTableAsOf = deploy.getBaseTableAsOf();
    this.primaryKeyJoin = deploy.getPrimaryKeyJoin();
    this.baseTableVersionsBetween = deploy.getBaseTableVersionsBetween();
    this.dependentTables = deploy.getDependentTables();
    this.dbComment = deploy.getDbComment();
    this.partitionMeta = deploy.getPartitionMeta();
    this.storageEngine = deploy.getStorageEngine();
    this.autoTunable = beanFinder == null && (entityType == EntityType.ORM || entityType == EntityType.VIEW);
    // helper object used to derive lists of properties
    DeployBeanPropertyLists listHelper = new DeployBeanPropertyLists(owner, this, deploy);
    this.softDeleteProperty = listHelper.getSoftDeleteProperty();
    // if formula is set, the property is virtual only (there is no column in db) the formula must evaluate to true,
    // if there is a join to a deleted bean. Example: '@Formula(select = "${ta}.user_id is null")'
    // this is required to support markAsDelete on beans that may have no FK constraint.
    this.softDelete = (softDeleteProperty != null && !softDeleteProperty.isFormula());
    this.idProperty = listHelper.getId();
    this.versionProperty = listHelper.getVersionProperty();
    this.unmappedJson = listHelper.getUnmappedJson();
    this.tenant = listHelper.getTenant();
    this.draft = listHelper.getDraft();
    this.draftDirty = listHelper.getDraftDirty();
    this.propMap = listHelper.getPropertyMap();
    this.propertiesTransient = listHelper.getTransients();
    this.propertiesNonTransient = listHelper.getNonTransients();
    this.propertiesBaseScalar = listHelper.getBaseScalar();
    this.propertiesEmbedded = listHelper.getEmbedded();
    this.propertiesLocal = listHelper.getLocal();
    this.propertiesMutable = listHelper.getMutable();
    this.unidirectional = listHelper.getUnidirectional();
    this.orderColumn = listHelper.getOrderColumn();
    this.propertiesOne = listHelper.getOnes();
    this.propertiesOneExportedSave = listHelper.getOneExportedSave();
    this.propertiesOneExportedDelete = listHelper.getOneExportedDelete();
    this.propertiesOneImported = listHelper.getOneImported();
    this.propertiesOneImportedSave = listHelper.getOneImportedSave();
    this.propertiesOneImportedDelete = listHelper.getOneImportedDelete();
    this.propertiesMany = listHelper.getMany();
    this.propertiesNonMany = listHelper.getNonMany();
    this.propertiesAggregate = listHelper.getAggregates();
    this.propertiesManySave = listHelper.getManySave();
    this.propertiesManyDelete = listHelper.getManyDelete();
    this.propertiesManyToMany = listHelper.getManyToMany();
    this.propertiesGenInsert = listHelper.getGeneratedInsert();
    this.propertiesGenUpdate = listHelper.getGeneratedUpdate();
    this.idOnlyReference = isIdOnlyReference(propertiesBaseScalar);
    boolean noRelationships = propertiesOne.length + propertiesMany.length == 0;
    this.cacheSharableBeans = noRelationships && deploy.getCacheOptions().isReadOnly();
    this.cacheHelp = new BeanDescriptorCacheHelp<>(this, owner.getCacheManager(), deploy.getCacheOptions(), cacheSharableBeans, propertiesOneImported);
    this.jsonHelp = initJsonHelp();
    this.draftHelp = new BeanDescriptorDraftHelp<>(this);
    this.docStoreAdapter = owner.createDocStoreBeanAdapter(this, deploy);
    this.docStoreQueueId = docStoreAdapter.getQueueId();
    // Check if there are no cascade save associated beans ( subject to change
    // in initialiseOther()). Note that if we are in an inheritance hierarchy
    // then we also need to check every BeanDescriptors in the InheritInfo as
    // well. We do that later in initialiseOther().
    saveRecurseSkippable = (0 == (propertiesOneExportedSave.length + propertiesOneImportedSave.length + propertiesManySave.length));

    // Check if there are no cascade delete associated beans (also subject to
    // change in initialiseOther()).
    deleteRecurseSkippable = (0 == (propertiesOneExportedDelete.length + propertiesOneImportedDelete.length + propertiesManyDelete.length));

    // object used to handle Id values
    this.idBinder = owner.createIdBinder(idProperty);
    this.whenModifiedProperty = findWhenModifiedProperty();
    this.whenCreatedProperty = findWhenCreatedProperty();
    // derive the index position of the Id and Version properties
    this.abstractType = Modifier.isAbstract(beanType.getModifiers());
    if (abstractType) {
      this.idPropertyIndex = -1;
      this.versionPropertyIndex = -1;
      this.unloadProperties = new int[0];
      this.propertiesIndex = new BeanProperty[0];
    } else {
      EntityBeanIntercept ebi = prototypeEntityBean._ebean_getIntercept();
      this.idPropertyIndex = (idProperty == null) ? -1 : ebi.findProperty(idProperty.getName());
      this.versionPropertyIndex = (versionProperty == null) ? -1 : ebi.findProperty(versionProperty.getName());
      this.unloadProperties = derivePropertiesToUnload(prototypeEntityBean);
      this.propertiesIndex = new BeanProperty[ebi.getPropertyLength()];
      for (int i = 0; i < propertiesIndex.length; i++) {
        propertiesIndex[i] = propMap.get(ebi.getProperty(i));
      }
    }
  }

  public boolean isJacksonCorePresent() {
    return owner.isJacksonCorePresent();
  }

  private BeanDescriptorJsonHelp<T> initJsonHelp() {
    return isJacksonCorePresent() ? new BeanDescriptorJsonHelp<>(this) : null;
  }

  /**
   * Return true if the bean should be treated as a reference bean when it only has its id populated.
   * To be true it has other scalar properties that are not generated on insert.
   */
  private boolean isIdOnlyReference(BeanProperty[] baseScalar) {
    for (BeanProperty beanProperty : baseScalar) {
      if (!beanProperty.isGeneratedOnInsert()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Derive an array of property positions for properties that are initialised in the constructor.
   * These properties need to be unloaded when populating beans for queries.
   */
  private int[] derivePropertiesToUnload(EntityBean prototypeEntityBean) {
    boolean[] loaded = prototypeEntityBean._ebean_getIntercept().getLoaded();
    int[] props = new int[loaded.length];
    int pos = 0;
    // collect the positions of the properties initialised in the default constructor.
    for (int i = 0; i < loaded.length; i++) {
      if (loaded[i]) {
        props[pos++] = i;
      }
    }
    if (pos == 0) {
      // nothing set in the constructor
      return new int[0];
    }
    // populate a smaller/minimal array
    int[] unload = new int[pos];
    System.arraycopy(props, 0, unload, 0, pos);
    return unload;
  }

  /**
   * Create an entity bean that is used as a prototype/factory to create new instances.
   */
  EntityBean createPrototypeEntityBean(Class<T> beanType) {
    if (Modifier.isAbstract(beanType.getModifiers())) {
      return null;
    }
    try {
      return (EntityBean) beanType.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Error trying to create the prototypeEntityBean for " + beanType, e);
    }
  }

  /**
   * Return the DatabaseConfig.
   */
  public DatabaseConfig getConfig() {
    return owner.getConfig();
  }

  /**
   * Set the server. Primarily so that the Many's can lazy load.
   */
  public void setEbeanServer(SpiEbeanServer ebeanServer) {
    this.ebeanServer = ebeanServer;
    for (BeanPropertyAssocMany<?> assocMany : propertiesMany) {
      // used for creating lazy loading lists etc
      assocMany.setEbeanServer(ebeanServer);
    }
  }

  /**
   * Return the EbeanServer instance that owns this BeanDescriptor.
   */
  public SpiEbeanServer getEbeanServer() {
    return ebeanServer;
  }

  /**
   * Return true if this is an abstract type.
   */
  boolean isAbstractType() {
    return abstractType;
  }

  /**
   * Return true if this is a "Doc Store only" entity bean.
   */
  @Override
  public boolean isDocStoreOnly() {
    return EntityType.DOC == entityType;
  }

  /**
   * Return the type of this domain object.
   */
  public EntityType getEntityType() {
    return entityType;
  }

  private String[] getProperties() {
    return properties;
  }

  public BeanProperty propertyByIndex(int pos) {
    return propertiesIndex[pos];
  }

  /**
   * Initialise the Id properties first.
   * <p>
   * These properties need to be initialised prior to the association properties
   * as they are used to get the imported and exported properties.
   */
  void initialiseId(BeanDescriptorInitContext initContext) {
    if (logger.isTraceEnabled()) {
      logger.trace("BeanDescriptor initialise " + fullName);
    }
    if (draftable) {
      initContext.addDraft(baseTable, draftTable);
    }
    if (historySupport) {
      // add mapping (used to swap out baseTable for asOf queries)
      initContext.addHistory(baseTable, baseTableAsOf);
    }
    if (inheritInfo != null) {
      inheritInfo.setDescriptor(this);
    }

    // initialise just the Id property only
    if (idProperty != null) {
      idProperty.initialise(initContext);
    }
  }

  /**
   * Initialise the exported and imported parts for associated properties.
   */
  public void initialiseOther(BeanDescriptorInitContext initContext) {
    for (BeanPropertyAssocMany<?> many : propertiesManyToMany) {
      // register associated draft table for M2M intersection
      many.registerDraftIntersectionTable(initContext);
    }
    if (historySupport) {
      // history support on this bean so check all associated intersection tables
      // and if they are not excluded register the associated 'with history' table
      for (BeanPropertyAssocMany<?> manyToMany : propertiesManyToMany) {
        // register associated history table for M2M intersection
        if (!manyToMany.isExcludedFromHistory()) {
          TableJoin intersectionTableJoin = manyToMany.getIntersectionTableJoin();
          initContext.addHistoryIntersection(intersectionTableJoin.getTable());
        }
      }
    }

    // initialise all the non-id properties
    for (BeanProperty prop : propertiesAll()) {
      if (!prop.isId()) {
        prop.initialise(initContext);
      }
      prop.registerColumn(this, null);
    }

    if (unidirectional != null) {
      unidirectional.initialise(initContext);
    }

    idBinder.initialise();
    idBinderInLHSSql = idBinder.getBindIdInSql(baseTableAlias);
    idBinderIdSql = idBinder.getBindIdSql(baseTableAlias);
    String idBinderInLHSSqlNoAlias = idBinder.getBindIdInSql(null);
    String idEqualsSql = idBinder.getBindIdSql(null);
    deleteByIdSql = "delete from " + baseTable + " where " + idEqualsSql;
    whereIdInSql = " where " + idBinderInLHSSqlNoAlias + " ";
    deleteByIdInSql = "delete from " + baseTable + whereIdInSql;
    if (softDelete) {
      softDeleteByIdSql = "update " + baseTable + " set " + getSoftDeleteDbSet() + " where " + idEqualsSql;
      softDeleteByIdInSql = "update " + baseTable + " set " + getSoftDeleteDbSet() + " where " + idBinderInLHSSqlNoAlias + " ";
    } else {
      softDeleteByIdSql = null;
      softDeleteByIdInSql = null;
    }
    initNaturalKey();
  }

  private void initNaturalKey() {
    final String[] naturalKey = cacheHelp.getNaturalKey();
    if (naturalKey != null && naturalKey.length != 0) {
      BeanProperty[] props = new BeanProperty[naturalKey.length];
      for (int i = 0; i < naturalKey.length; i++) {
        props[i] = getBeanProperty(naturalKey[i]);
      }
      this.beanNaturalKey = new BeanNaturalKey(naturalKey, props);
    }
  }

  private boolean hasCircularImportedId() {
    for (BeanPropertyAssocOne<?> assocOne : propertiesOneImportedSave) {
      if (assocOne.hasCircularImportedId(this)) {
        return true;
      }
    }
    return false;
  }

  boolean hasCircularImportedIdTo(BeanDescriptor<?> sourceDesc) {
    for (BeanPropertyAssocOne<?> assocOne : propertiesOneImportedSave) {
      if (assocOne.getTargetDescriptor() == sourceDesc) {
        return true;
      }
    }
    return false;
  }

  void registerColumn(String dbColumn, String path) {
    String key = dbColumn.toLowerCase();
    // check for clash with imported OneToOne PK
    if (!columnPath.containsKey(key)) {
      columnPath.put(key, path);
    }
  }

  void registerTable(String baseTable, BeanPropertyAssoc<?> assocProperty) {
    if (baseTable != null) {
      tablePath.put(baseTable.toLowerCase(), assocProperty);
    }
  }

  /**
   * Perform last initialisation for the descriptor.
   */
  void initLast() {
    for (BeanProperty prop : propertiesNonTransient) {
      if (prop.isUnique()) {
        propertiesUnique.add(new BeanProperty[]{prop});
      }
    }
    // convert unique columns to properties
    if (indexDefinitions != null) {
      for (IndexDefinition indexDef : indexDefinitions) {
        if (indexDef.isUnique()) {
          addUniqueColumns(indexDef);
        }
      }
    }
    docStoreEmbeddedInvalidation = docStoreAdapter.hasEmbeddedInvalidation();
  }

  private void addUniqueColumns(IndexDefinition indexDef) {
    String[] cols = indexDef.getColumns();
    BeanProperty[] props = new BeanProperty[cols.length];
    for (int i = 0; i < cols.length; i++) {
      String propName = findBeanPath("", "", cols[i]);
      if (propName == null) {
        return;
      }
      props[i] = findProperty(propName);
    }
    if (props.length == 1) {
      for (BeanProperty[] inserted : propertiesUnique) {
        if (inserted.length == 1 && inserted[0].equals(props[0])) {
          return; // do not insert duplicates
        }
      }
    }
    propertiesUnique.add(props);
  }

  /**
   * Initialise the document mapping.
   */
  @SuppressWarnings("unchecked")
  void initialiseDocMapping() {
    batchEscalateOnCascadeInsert = supportBatchEscalateOnInsert();
    batchEscalateOnCascadeDelete = supportBatchEscalateOnDelete();
    for (BeanPropertyAssocMany<?> many : propertiesMany) {
      many.initialisePostTarget();
    }
    for (BeanPropertyAssocOne<?> one : propertiesOne) {
      one.initialisePostTarget();
    }
    if (inheritInfo != null && !inheritInfo.isRoot()) {
      docStoreAdapter = (DocStoreBeanAdapter<T>) inheritInfo.getRoot().desc().docStoreAdapter();
    }
    docMapping = docStoreAdapter.createDocMapping();
    docStoreAdapter.registerPaths();
    cacheHelp.deriveNotifyFlags();
  }

  private boolean supportBatchEscalateOnDelete() {
    if (softDelete) {
      return false;
    }
    for (BeanPropertyAssocMany<?> assocMany : propertiesManyDelete) {
      if (assocMany.isCascadeDeleteEscalate()) {
        return true;
      }
    }
    return false;
  }

  private boolean supportBatchEscalateOnInsert() {
    return idType == IdType.IDENTITY || !hasCircularImportedId();
  }

  /**
   * Return false if JDBC batch can't be implicitly escalated to.
   * This happens when we have circular import id situation (need to defer setting identity value).
   */
  public boolean isBatchEscalateOnCascade(PersistRequest.Type type) {
    return type == PersistRequest.Type.INSERT ? batchEscalateOnCascadeInsert : batchEscalateOnCascadeDelete;
  }

  void initInheritInfo() {
    if (inheritInfo != null) {
      // need to check every BeanDescriptor in the inheritance hierarchy
      if (saveRecurseSkippable) {
        saveRecurseSkippable = inheritInfo.isSaveRecurseSkippable();
      }
      if (deleteRecurseSkippable) {
        deleteRecurseSkippable = inheritInfo.isDeleteRecurseSkippable();
      }
    }
  }

  public void metricPersistBatch(PersistRequest.Type type, long startNanos, int size) {
    iudMetrics.addBatch(type, startNanos, size);
  }

  public void metricPersistNoBatch(PersistRequest.Type type, long startNanos) {
    iudMetrics.addNoBatch(type, startNanos);
  }

  public void merge(EntityBean bean, EntityBean existing) {
    EntityBeanIntercept fromEbi = bean._ebean_getIntercept();
    EntityBeanIntercept toEbi = existing._ebean_getIntercept();
    int propertyLength = toEbi.getPropertyLength();
    String[] names = getProperties();
    for (int i = 0; i < propertyLength; i++) {
      if (fromEbi.isLoadedProperty(i)) {
        BeanProperty property = getBeanProperty(names[i]);
        if (!toEbi.isLoadedProperty(i)) {
          Object val = property.getValue(bean);
          property.setValue(existing, val);
        } else if (property.isMany()) {
          property.merge(bean, existing);
        }
      }
    }
  }

  /**
   * Bind all the property values to the SqlUpdate.
   */
  public void bindElementValue(SqlUpdate insert, Object value) {
    EntityBean bean = (EntityBean) value;
    for (BeanProperty property : propertiesBaseScalar) {
      insert.setParameter(property.getValue(bean));
    }
  }

  /**
   * Return the ReadAuditLogger for logging read audit events.
   */
  public ReadAuditLogger getReadAuditLogger() {
    return ebeanServer.getReadAuditLogger();
  }

  /**
   * Return the ReadAuditPrepare for preparing read audit events prior to logging.
   */
  private ReadAuditPrepare getReadAuditPrepare() {
    return ebeanServer.getReadAuditPrepare();
  }

  public boolean isChangeLog() {
    return changeLogFilter != null;
  }

  /**
   * Return true if this request should be included in the change log.
   */
  public BeanChange getChangeLogBean(PersistRequestBean<T> request) {
    switch (request.getType()) {
      case INSERT:
        return changeLogFilter.includeInsert(request) ? insertBeanChange(request) : null;
      case UPDATE:
      case DELETE_SOFT:
        return changeLogFilter.includeUpdate(request) ? updateBeanChange(request) : null;
      case DELETE:
        return changeLogFilter.includeDelete(request) ? deleteBeanChange(request) : null;
      default:
        throw new IllegalStateException("Unhandled request type " + request.getType());
    }
  }

  private BeanChange beanChange(ChangeType type, Object id, String data, String oldData) {
    Object tenantId = ebeanServer.currentTenantId();
    return new BeanChange(name, tenantId, id, type, data, oldData);
  }

  /**
   * Return the bean change for a delete.
   */
  private BeanChange deleteBeanChange(PersistRequestBean<T> request) {
    return beanChange(ChangeType.DELETE, request.getBeanId(), null, null);
  }

  /**
   * Return the bean change for an update generating 'new values' and 'old values' in JSON form.
   */
  private BeanChange updateBeanChange(PersistRequestBean<T> request) {
    try {
      BeanChangeJson changeJson = new BeanChangeJson(this, request.isStatelessUpdate());
      request.getEntityBeanIntercept().addDirtyPropertyValues(changeJson);
      changeJson.flush();
      return beanChange(ChangeType.UPDATE, request.getBeanId(), changeJson.newJson(), changeJson.oldJson());
    } catch (RuntimeException e) {
      logger.error("Failed to write ChangeLog entry for update", e);
      return null;
    }
  }

  /**
   * Return the bean change for an insert.
   */
  private BeanChange insertBeanChange(PersistRequestBean<T> request) {
    try {
      StringWriter writer = new StringWriter(200);
      SpiJsonWriter jsonWriter = createJsonWriter(writer);
      jsonWriteForInsert(jsonWriter, request.getEntityBean());
      jsonWriter.flush();
      return beanChange(ChangeType.INSERT, request.getBeanId(), writer.toString(), null);
    } catch (IOException e) {
      logger.error("Failed to write ChangeLog entry for insert", e);
      return null;
    }
  }

  SpiJsonWriter createJsonWriter(StringWriter writer) {
    return ebeanServer.jsonExtended().createJsonWriter(writer);
  }

  SpiJsonReader createJsonReader(String json) {
    return ebeanServer.jsonExtended().createJsonRead(this, json);
  }

  /**
   * Populate the diff for inserts with flattened non-null property values.
   */
  void jsonWriteForInsert(SpiJsonWriter jsonWriter, EntityBean newBean) throws IOException {
    jsonWriter.writeStartObject();
    for (BeanProperty prop : propertiesBaseScalar) {
      prop.jsonWriteForInsert(jsonWriter, newBean);
    }
    for (BeanPropertyAssocOne<?> prop : propertiesOne) {
      prop.jsonWriteForInsert(jsonWriter, newBean);
    }
    for (BeanPropertyAssocOne<?> prop : propertiesEmbedded) {
      prop.jsonWriteForInsert(jsonWriter, newBean);
    }
    jsonWriter.writeEndObject();
  }

  public SqlUpdate deleteById(Object id, List<Object> idList, DeleteMode mode) {
    if (id != null) {
      return deleteById(id, mode);
    } else {
      return deleteByIdList(idList, mode);
    }
  }

  /**
   * Return the "where id in" sql (for use with UpdateQuery).
   */
  public String getWhereIdInSql() {
    return whereIdInSql;
  }

  /**
   * Return the "delete by id" sql.
   */
  public String getDeleteByIdInSql() {
    return deleteByIdInSql;
  }

  /**
   * Return SQL that can be used to delete a list of Id's without any optimistic
   * concurrency checking.
   */
  private SqlUpdate deleteByIdList(List<Object> idList, DeleteMode mode) {
    String baseSql = mode.isHard() ? deleteByIdInSql : softDeleteByIdInSql;
    StringBuilder sb = new StringBuilder(baseSql);
    String inClause = idBinder.getIdInValueExprDelete(idList.size());
    sb.append(inClause);
    DefaultSqlUpdate delete = new DefaultSqlUpdate(sb.toString());
    idBinder.addIdInBindValues(delete, idList);
    return delete;
  }

  /**
   * Return SQL that can be used to delete by Id without any optimistic
   * concurrency checking.
   */
  private SqlUpdate deleteById(Object id, DeleteMode mode) {
    String baseSql = mode.isHard() ? deleteByIdSql : softDeleteByIdSql;
    DefaultSqlUpdate sqlDelete = new DefaultSqlUpdate(baseSql);
    Object[] bindValues = idBinder.getBindValues(id);
    for (Object bindValue : bindValues) {
      sqlDelete.setParameter(bindValue);
    }
    return sqlDelete;
  }

  /**
   * Add objects to ElPropertyDeploy etc. These are used so that expressions on
   * foreign keys don't require an extra join.
   */
  public void add(BeanFkeyProperty fkey) {
    elDeployCache.put(fkey.getName(), fkey);
  }

  void initialiseFkeys() {
    for (BeanPropertyAssocOne<?> oneImported : propertiesOneImported) {
      if (!oneImported.isFormula()) {
        oneImported.addFkey();
      }
    }
  }

  /**
   * Return the cache options.
   */
  public CacheOptions getCacheOptions() {
    return cacheHelp.getCacheOptions();
  }

  /**
   * Return the Encrypt key given the BeanProperty.
   */
  public EncryptKey getEncryptKey(BeanProperty p) {
    return owner.getEncryptKey(baseTable, p.getDbColumn());
  }

  /**
   * Return the Encrypt key given the table and column name.
   */
  public EncryptKey getEncryptKey(String tableName, String columnName) {
    return owner.getEncryptKey(tableName, columnName);
  }

  /**
   * Return the Scalar type for the given JDBC type.
   */
  public ScalarType<?> getScalarType(int jdbcType) {
    return owner.getScalarType(jdbcType);
  }

  public ScalarType<?> getScalarType(String cast) {
    return owner.getScalarType(cast);
  }

  /**
   * Return true if this bean type has a default select clause that is not
   * simply select all properties.
   */
  public boolean hasDefaultSelectClause() {
    return defaultSelectClause != null;
  }

  /**
   * Return the default select clause.
   */
  public String getDefaultSelectClause() {
    return defaultSelectClause;
  }

  /**
   * Return true if this object is the root level object in its entity
   * inheritance.
   */
  @Override
  public boolean isInheritanceRoot() {
    return inheritInfo == null || inheritInfo.isRoot();
  }

  /**
   * Return true if this type maps to a root type of a doc store document (not embedded or ignored).
   */
  @Override
  public boolean isDocStoreMapped() {
    return docStoreAdapter.isMapped();
  }

  /**
   * Return true if this bean type has embedded doc store invalidation.
   */
  public boolean isDocStoreEmbeddedInvalidation() {
    return docStoreEmbeddedInvalidation;
  }

  /**
   * Return the queueId used to uniquely identify this type when queuing an index updateAdd.
   */
  @Override
  public String getDocStoreQueueId() {
    return docStoreQueueId;
  }

  @Override
  public DocumentMapping getDocMapping() {
    return docMapping;
  }

  /**
   * Return the doc store helper for this bean type.
   */
  @Override
  public BeanDocType<T> docStore() {
    return docStoreAdapter;
  }

  /**
   * Return doc store adapter for internal use for processing persist requests.
   */
  public DocStoreBeanAdapter<T> docStoreAdapter() {
    return docStoreAdapter;
  }

  /**
   * Build the Document mapping recursively with the given prefix relative to the root of the document.
   */
  public void docStoreMapping(final DocMappingBuilder mapping, final String prefix) {
    if (prefix != null && idProperty != null) {
      // id property not included in the
      idProperty.docStoreMapping(mapping, prefix);
    }
    if (inheritInfo != null) {
      String discCol = inheritInfo.getDiscriminatorColumn();
      if (Types.VARCHAR == inheritInfo.getDiscriminatorType()) {
        mapping.add(new DocPropertyMapping(discCol, DocPropertyType.ENUM));
      } else {
        mapping.add(new DocPropertyMapping(discCol, DocPropertyType.INTEGER));
      }
    }
    for (BeanProperty prop : propertiesNonTransient) {
      prop.docStoreMapping(mapping, prefix);
    }
    if (inheritInfo != null) {
      inheritInfo.visitChildren(inheritInfo1 -> {
        for (BeanProperty localProperty : inheritInfo1.localProperties()) {
          localProperty.docStoreMapping(mapping, prefix);
        }
      });
    }
  }

  /**
   * Return the root bean type if part of inheritance hierarchy.
   */
  @Override
  public BeanType<?> root() {
    if (inheritInfo != null && !inheritInfo.isRoot()) {
      return inheritInfo.getRoot().desc();
    }
    return this;
  }

  /**
   * Return the full name taking into account inheritance.
   */
  public String rootName() {
    if (inheritInfo != null && !inheritInfo.isRoot()) {
      return inheritInfo.getRoot().desc().getName();
    }
    return name;
  }

  /**
   * Return the named ORM query.
   */
  public String getNamedQuery(String name) {
    return namedQuery.get(name);
  }

  /**
   * Return the named RawSql query.
   */
  public SpiRawSql getNamedRawSql(String named) {
    return namedRawSql.get(named);
  }

  /**
   * Return the type of DocStoreMode that should occur for this type of persist request
   * given the transactions requested mode.
   */
  public DocStoreMode getDocStoreMode(PersistRequest.Type persistType, DocStoreMode txnMode) {
    return docStoreAdapter.getMode(persistType, txnMode);
  }

  public void docStoreInsert(Object idValue, PersistRequestBean<T> persistRequest, DocStoreUpdateContext bulkUpdate) throws IOException {
    docStoreAdapter.insert(idValue, persistRequest, bulkUpdate);
  }

  public void docStoreUpdate(Object idValue, PersistRequestBean<T> persistRequest, DocStoreUpdateContext bulkUpdate) throws IOException {
    docStoreAdapter.update(idValue, persistRequest, bulkUpdate);
  }

  /**
   * Check if this update invalidates an embedded part of a doc store document.
   */
  public void docStoreUpdateEmbedded(PersistRequestBean<T> request, DocStoreUpdates docStoreUpdates) {
    docStoreAdapter.updateEmbedded(request, docStoreUpdates);
  }

  public void docStoreDeleteById(Object idValue, DocStoreUpdateContext txn) throws IOException {
    docStoreAdapter.deleteById(idValue, txn);
  }

  public T publish(T draftBean, T liveBean) {
    return draftHelp.publish(draftBean, liveBean);
  }

  /**
   * Reset properties on the draft bean based on @DraftDirty and @DraftReset.
   */
  public boolean draftReset(T draftBean) {
    return draftHelp.draftReset(draftBean);
  }

  /**
   * Return the draft dirty boolean property or null if there is not one assigned to this bean type.
   */
  BeanProperty getDraftDirty() {
    return draftDirty;
  }

  /**
   * Prepare the query for multi-tenancy check for document store only use.
   */
  public void prepareQuery(SpiQuery<T> query) {
    if (tenant != null && !query.isNativeSql()) {
      Object tenantId = ebeanServer.currentTenantId();
      if (tenantId != null) {
        tenant.addTenant(query, tenantId);
      }
    }
    if (isDocStoreOnly()) {
      query.setUseDocStore(true);
    }
  }

  /**
   * Return the natural key.
   */
  public BeanNaturalKey getNaturalKey() {
    return beanNaturalKey;
  }

  /**
   * Return true if there is currently bean caching for this type of bean.
   */
  @Override
  public boolean isBeanCaching() {
    return cacheHelp.isBeanCaching();
  }

  /**
   * Return true if there is a natural key defined for this bean type.
   */
  public boolean isNaturalKeyCaching() {
    return cacheHelp.isNaturalKeyCaching();
  }

  /**
   * Return true if there is query caching for this type of bean.
   */
  @Override
  public boolean isQueryCaching() {
    return cacheHelp.isQueryCaching();
  }

  public boolean isManyPropCaching() {
    return isBeanCaching();
  }

  /**
   * Return true if the persist request needs to notify the cache.
   */
  public boolean isCacheNotify(PersistRequest.Type type, boolean publish) {
    if (draftable && !publish) {
      // no caching when editing draft beans
      return false;
    }
    return cacheHelp.isCacheNotify(type);
  }

  @Override
  public void clearBeanCache() {
    cacheHelp.beanCacheClear();
  }

  /**
   * Clear the query cache.
   */
  @Override
  public void clearQueryCache() {
    cacheHelp.queryCacheClear();
  }

  /**
   * Get a query result from the query cache.
   */
  public Object queryCacheGet(Object id) {
    return cacheHelp.queryCacheGet(id);
  }

  /**
   * Put a query result into the query cache.
   */
  public void queryCachePut(Object id, QueryCacheEntry entry) {
    cacheHelp.queryCachePut(id, entry);
  }

  /**
   * Try to load the beanCollection from cache return true if successful.
   */
  public boolean cacheManyPropLoad(BeanPropertyAssocMany<?> many, BeanCollection<?> bc, Object parentId, Boolean readOnly) {
    return cacheHelp.manyPropLoad(many, bc, parentId, readOnly);
  }

  /**
   * Put the beanCollection into the cache.
   */
  public void cacheManyPropPut(BeanPropertyAssocMany<?> many, BeanCollection<?> bc, Object parentId) {
    cacheHelp.manyPropPut(many, bc, parentId);
  }

  /**
   * Update the bean collection entry in the cache.
   */
  public void cacheManyPropPut(String name, Object parentId, CachedManyIds entry) {
    cacheHelp.cachePutManyIds(parentId, name, entry);
  }

  public void cacheManyPropRemove(String propertyName, Object parentId) {
    cacheHelp.manyPropRemove(propertyName, parentId);
  }

  public void cacheManyPropClear(String propertyName) {
    cacheHelp.manyPropClear(propertyName);
  }

  /**
   * Extract the raw cache data from the embedded bean.
   */
  public CachedBeanData cacheEmbeddedBeanExtract(EntityBean bean) {
    return cacheHelp.beanExtractData(this, bean);
  }

  /**
   * Load the embedded bean (taking into account inheritance).
   */
  public EntityBean cacheEmbeddedBeanLoad(CachedBeanData data, PersistenceContext context) {
    return cacheHelp.embeddedBeanLoad(data, context);
  }

  /**
   * Load the embedded bean as the root type.
   */
  EntityBean cacheEmbeddedBeanLoadDirect(CachedBeanData data, PersistenceContext context) {
    return cacheHelp.embeddedBeanLoadDirect(data, context);
  }

  /**
   * Load the entity bean as the correct bean type.
   */
  EntityBean cacheBeanLoadDirect(Object id, Boolean readOnly, CachedBeanData data, PersistenceContext context) {
    return cacheHelp.loadBeanDirect(id, readOnly, data, context);
  }

  /**
   * Put the bean into the cache.
   */
  public void cacheBeanPut(T bean) {
    cacheBeanPut((EntityBean) bean);
  }

  /**
   * Put a bean into the bean cache (taking into account inheritance).
   */
  public void cacheBeanPut(EntityBean bean) {
    cacheHelp.beanCachePut(bean);
  }

  @SuppressWarnings("unchecked")
  public void cacheBeanPutAll(Collection<?> beans) {
    if (!beans.isEmpty()) {
      cacheHelp.beanPutAll((Collection<EntityBean>) beans);
    }
  }

  void cacheBeanPutAllDirect(Collection<EntityBean> beans) {
    cacheHelp.beanCachePutAllDirect(beans);
  }

  /**
   * Put a bean into the cache as the correct type.
   */
  void cacheBeanPutDirect(EntityBean bean) {
    cacheHelp.beanCachePutDirect(bean);
  }

  /**
   * Return a bean from the bean cache (or null).
   */
  public T cacheBeanGet(Object id, Boolean readOnly, PersistenceContext context) {
    return cacheHelp.beanCacheGet(cacheKey(id), readOnly, context);
  }

  /**
   * Remove a collection of beans from the cache given the ids.
   */
  public void cacheApplyInvalidate(Collection<Object> ids) {
    List<String> keys = new ArrayList<>(ids.size());
    for (Object id : ids) {
      keys.add(cacheKey(id));
    }
    cacheHelp.beanCacheApplyInvalidate(keys);
  }

  /**
   * Hit the bean cache trying to load a list/batch of entities.
   * Return the set of entities that were successfully loaded from L2 cache.
   */
  public Set<EntityBeanIntercept> cacheBeanLoadAll(List<EntityBeanIntercept> list, PersistenceContext persistenceContext, int lazyLoadProperty, String propertyName) {
    return cacheHelp.beanCacheLoadAll(list, persistenceContext, lazyLoadProperty, propertyName);
  }

  /**
   * Returns true if it managed to populate/load the bean from the cache.
   */
  public boolean cacheBeanLoad(EntityBean bean, EntityBeanIntercept ebi, Object id, PersistenceContext context) {
    return cacheHelp.beanCacheLoad(bean, ebi, cacheKey(id), context);
  }

  public BeanCacheResult<T> cacheIdLookup(PersistenceContext context, Collection<?> ids) {
    return cacheHelp.cacheIdLookup(context, ids);
  }

  /**
   * Use natural key lookup to hit the bean cache.
   */
  public BeanCacheResult<T> naturalKeyLookup(PersistenceContext context, Set<Object> keys) {
    return cacheHelp.naturalKeyLookup(context, keys);
  }

  public void cacheNaturalKeyPut(String key, String newKey) {
    cacheHelp.cacheNaturalKeyPut(key, newKey);
  }

  /**
   * Check if bulk update or delete query has a cache impact.
   */
  public void cacheUpdateQuery(boolean update, SpiTransaction transaction) {
    cacheHelp.cacheUpdateQuery(update, transaction);
  }

  /**
   * Invalidate parts of cache due to SqlUpdate or external modification etc.
   */
  void cachePersistTableIUD(TableIUD tableIUD, CacheChangeSet changeSet) {
    cacheHelp.persistTableIUD(tableIUD, changeSet);
  }

  /**
   * Remove a bean from the cache given its Id.
   */
  public void cachePersistDeleteByIds(Collection<Object> ids, CacheChangeSet changeSet) {
    cacheHelp.persistDeleteIds(ids, changeSet);
  }

  /**
   * Remove a bean from the cache given its Id.
   */
  public void cachePersistDelete(Object id, PersistRequestBean<T> deleteRequest, CacheChangeSet changeSet) {
    cacheHelp.persistDelete(id, deleteRequest, changeSet);
  }

  /**
   * Add the insert changes to the changeSet.
   */
  public void cachePersistInsert(PersistRequestBean<T> insertRequest, CacheChangeSet changeSet) {
    cacheHelp.persistInsert(insertRequest, changeSet);
  }

  /**
   * Add the update to the changeSet.
   */
  public void cachePersistUpdate(Object id, PersistRequestBean<T> updateRequest, CacheChangeSet changeSet) {
    cacheHelp.persistUpdate(id, updateRequest, changeSet);
  }

  /**
   * Apply the update to the cache.
   */
  public void cacheApplyBeanUpdate(String key, Map<String, Object> changes, boolean updateNaturalKey, long version) {
    cacheHelp.cacheBeanUpdate(key, changes, updateNaturalKey, version);
  }

  /**
   * Prepare the read audit of a findFutureList() query.
   */
  public void readAuditFutureList(SpiQuery<T> spiQuery) {
    if (isReadAuditing()) {
      ReadEvent event = new ReadEvent(fullName);
      // prepare in the foreground thread while we have the user context
      // information (query is processed/executed later in bg thread)
      readAuditPrepare(event);
      spiQuery.setFutureFetchAudit(event);
    }
  }

  /**
   * Write a bean read to the read audit log.
   */
  public void readAuditBean(String queryKey, String bindLog, Object bean) {
    ReadEvent event = new ReadEvent(fullName, queryKey, bindLog, getIdForJson(bean));
    readAuditPrepare(event);
    getReadAuditLogger().auditBean(event);
  }

  private void readAuditPrepare(ReadEvent event) {
    ReadAuditPrepare prepare = getReadAuditPrepare();
    if (prepare != null) {
      prepare.prepare(event);
    }
  }

  /**
   * Write a many bean read to the read audit log.
   */
  public void readAuditMany(String queryKey, String bindLog, List<Object> ids) {
    ReadEvent event = new ReadEvent(fullName, queryKey, bindLog, ids);
    readAuditPrepare(event);
    getReadAuditLogger().auditMany(event);
  }

  /**
   * Write a futureList many read to the read audit log.
   */
  public void readAuditFutureMany(ReadEvent event) {
    // this has already been prepared (in foreground thread)
    getReadAuditLogger().auditMany(event);
  }

  /**
   * Return the base table alias. This is always the first letter of the bean name.
   */
  public String getBaseTableAlias() {
    return baseTableAlias;
  }

  public void preAllocateIds(int batchSize) {
    if (idGenerator != null) {
      idGenerator.preAllocateIds(batchSize);
    }
  }

  public Object nextId(Transaction t) {
    if (idGenerator != null) {
      return idGenerator.nextId(t);
    } else {
      return null;
    }
  }

  public DeployPropertyParser parser() {
    return new DeployPropertyParser(this);
  }

  /**
   * Convert the logical orm update statement into sql by converting the bean
   * properties and bean name to database columns and table.
   */
  public String convertOrmUpdateToSql(String ormUpdateStatement) {
    return new DeployUpdateParser(this).parse(ormUpdateStatement);
  }

  void queryPlanInit(QueryPlanInit request, List<MetaQueryPlan> list) {
    for (CQueryPlan queryPlan : queryPlanCache.values()) {
      if (request.includeHash(queryPlan.getHash())) {
        queryPlan.queryPlanInit(request.getThresholdMicros());
        list.add(queryPlan.createMeta(null, null));
      }
    }
  }

  /**
   * Visit all the ORM query plan metrics (includes UpdateQuery with updates and deletes).
   */
  public void visitMetrics(MetricVisitor visitor) {
    iudMetrics.visit(visitor);
    for (CQueryPlan queryPlan : queryPlanCache.values()) {
      if (!queryPlan.isEmptyStats()) {
        visitor.visitQuery(queryPlan.getSnapshot(visitor.isReset()));
      }
    }
  }

  /**
   * Reset the statistics on all the query plans.
   */
  public void clearQueryStatistics() {
    for (CQueryPlan queryPlan : queryPlanCache.values()) {
      queryPlan.resetStatistics();
    }
  }

  /**
   * Trim query plans not used since the passed in epoch time.
   */
  void trimQueryPlans(long unusedSince) {
    queryPlanCache.values().removeIf(queryPlan -> queryPlan.getLastQueryTime() < unusedSince);
  }

  /**
   * Execute the postLoad if a BeanPostLoad exists for this bean.
   */
  @Override
  public void postLoad(Object bean) {
    if (beanPostLoad != null) {
      beanPostLoad.postLoad(bean);
    }
  }

  public CQueryPlan getQueryPlan(CQueryPlanKey key) {
    return queryPlanCache.get(key);
  }

  public void putQueryPlan(CQueryPlanKey key, CQueryPlan plan) {
    queryPlanCache.put(key, plan);
  }

  /**
   * Get a UpdatePlan for a given hash.
   */
  public SpiUpdatePlan getUpdatePlan(String key) {
    return updatePlanCache.get(key);
  }

  /**
   * Add a UpdatePlan to the cache with a given hash.
   */
  public void putUpdatePlan(String key, SpiUpdatePlan plan) {
    updatePlanCache.put(key, plan);
  }

  /**
   * Return a Sql update statement to set the importedId value (deferred execution).
   */
  public String getUpdateImportedIdSql(ImportedId prop) {
    return "update " + baseTable + " set " + prop.importedIdClause() + " where " + idBinder.getBindIdSql(null);
  }

  /**
   * Return true if save does not recurse to other beans. That is return true if
   * there are no assoc one or assoc many beans that cascade save.
   */
  boolean isSaveRecurseSkippable() {
    return saveRecurseSkippable;
  }

  /**
   * Return true if delete does not recurse to other beans. That is return true
   * if there are no assoc one or assoc many beans that cascade delete.
   */
  boolean isDeleteRecurseSkippable() {
    return deleteRecurseSkippable;
  }

  /**
   * Return true if delete can use a single SQL statement.
   * <p>
   * This implies cascade delete does not continue depth wise and that this is no
   * associated L2 bean caching.
   */
  public boolean isDeleteByStatement() {
    return persistListener == null
      && persistController == null
      && deleteRecurseSkippable && !isBeanCaching();
  }

  public boolean isDeleteByBulk() {
    return persistListener == null
      && persistController == null
      && propertiesManyToMany.length == 0;
  }

  /**
   * Return the 'when modified' property if there is one defined.
   */
  @Override
  public BeanProperty getWhenModifiedProperty() {
    return whenModifiedProperty;
  }

  /**
   * Return the 'when created' property if there is one defined.
   */
  @Override
  public BeanProperty getWhenCreatedProperty() {
    return whenCreatedProperty;
  }

  /**
   * Find a property annotated with @WhenCreated or @CreatedTimestamp.
   */
  private BeanProperty findWhenCreatedProperty() {
    for (BeanProperty baseScalar : propertiesBaseScalar) {
      if (baseScalar.isGeneratedWhenCreated()) {
        return baseScalar;
      }
    }
    return null;
  }

  /**
   * Find a property annotated with @WhenModified or @UpdatedTimestamp.
   */
  private BeanProperty findWhenModifiedProperty() {
    for (BeanProperty baseScalar : propertiesBaseScalar) {
      if (baseScalar.isGeneratedWhenModified()) {
        return baseScalar;
      }
    }
    return null;
  }

  /**
   * Return the many property included in the query or null if one is not.
   */
  public BeanPropertyAssocMany<?> getManyProperty(SpiQuery<?> query) {
    OrmQueryDetail detail = query.getDetail();
    for (BeanPropertyAssocMany<?> many : propertiesMany) {
      if (detail.includesPath(many.getName())) {
        return many;
      }
    }
    return null;
  }

  /**
   * Return a raw expression for 'where parent id in ...' clause.
   */
  String getParentIdInExpr(int parentIdSize, String rawWhere) {
    String inClause = idBinder.getIdInValueExpr(false, parentIdSize);
    return idBinder.isIdInExpandedForm() ? inClause : rawWhere + inClause;
  }

  /**
   * Return the IdBinder which is helpful for handling the various types of Id.
   */
  @Override
  public IdBinder getIdBinder() {
    return idBinder;
  }

  /**
   * Return true if this bean type has a simple single Id property.
   */
  boolean isSimpleId() {
    return idBinder instanceof IdBinderSimple;
  }

  @Override
  public boolean hasId() {
    return idProperty != null;
  }

  /**
   * Return false for IdClass case with multiple @Id properties.
   */
  public boolean hasSingleIdProperty() {
    return idPropertyIndex != -1;
  }

  /**
   * Return true if this type has a simple Id and the platform supports mutli-value binding.
   */
  public boolean isMultiValueIdSupported() {
    return multiValueSupported && isSimpleId();
  }

  /**
   * Return true if Id IN expression should have bind parameters padded.
   */
  public boolean isPadInExpression() {
    return !multiValueSupported && isSimpleId();
  }

  /**
   * Return the sql for binding an id. This is the columns with table alias that
   * make up the id.
   */
  public String getIdBinderIdSql(String alias) {
    if (alias == null) {
      return idBinderIdSql;
    } else {
      return idBinder.getBindIdSql(alias);
    }
  }

  /**
   * Return the sql for binding id's using an IN clause.
   */
  public String getIdBinderInLHSSql() {
    return idBinderInLHSSql;
  }

  /**
   * Bind the idValue to the preparedStatement.
   * <p>
   * This takes care of the various id types such as embedded beans etc.
   */
  public void bindId(DataBind dataBind, Object idValue) throws SQLException {
    idBinder.bindId(dataBind, idValue);
  }

  /**
   * Return the id as an array of scalar bindable values.
   * <p>
   * This 'flattens' any EmbeddedId or multiple Id property cases.
   */
  public Object[] getBindIdValues(Object idValue) {
    return idBinder.getBindValues(idValue);
  }

  @Override
  @SuppressWarnings("unchecked")
  public T createBean() {
    return (T) createEntityBean(true);
  }

  /**
   * Creates a new EntityBean.
   * The parameter <code>isNew</code> controls either this is a new bean (then
   * {@link BeanPostConstructListener#postCreate(Object)} will be invoked) or
   * a reference (then {@link BeanPostLoad#postLoad(Object)} will be invoked
   * on first access (lazy load) or immediately (eager load)
   */
  private EntityBean createEntityBean(boolean isNew) {
    if (prototypeEntityBean == null) {
      throw new UnsupportedOperationException("cannot create entity bean for abstract entity " + getName());
    }
    try {
      EntityBean bean = (EntityBean) prototypeEntityBean._ebean_newInstance();
      if (beanPostConstructListener != null) {
        beanPostConstructListener.autowire(bean); // calls all registered listeners
        beanPostConstructListener.postConstruct(bean); // calls first the @PostConstruct method and then the listeners
      }
      if (isNew) {
        if (beanPostConstructListener != null) {
          beanPostConstructListener.postCreate(bean);
          // if bean is not new, postLoad will be executed later in the bean's lifecycle
        }
        // do not unload properties for new beans!
      } else if (unloadProperties.length > 0) {
        // 'unload' any properties initialised in the default constructor
        EntityBeanIntercept ebi = bean._ebean_getIntercept();
        for (int unloadProperty : unloadProperties) {
          ebi.setPropertyUnloaded(unloadProperty);
        }
      }
      return bean;
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }
  }

  /**
   * Creates a new entity bean without invoking {@link BeanPostConstructListener#postCreate(Object)}
   */
  @Override
  public EntityBean createEntityBean() {
    return createEntityBean(false);
  }

  /**
   * Create an entity bean for JSON marshalling (which differs for the element collection case).
   */
  public EntityBean createEntityBeanForJson() {
    return createEntityBean();
  }

  /**
   * We actually need to do a query because we don't know the type without the discriminator value.
   */
  private T findReferenceBean(Object id, PersistenceContext pc) {
    DefaultOrmQuery<T> query = new DefaultOrmQuery<>(this, ebeanServer, ebeanServer.getExpressionFactory());
    query.setPersistenceContext(pc);
    return query.setId(id).findOne();
  }

  /**
   * Create a reference with a check for the bean in the persistence context.
   */
  public EntityBean createReference(Boolean readOnly, Object id, PersistenceContext pc) {
    Object refBean = contextGet(pc, id);
    if (refBean == null) {
      refBean = createReference(readOnly, false, id, pc);
    }
    return (EntityBean) refBean;
  }

  /**
   * Create a reference bean based on the id.
   */
  @SuppressWarnings("unchecked")
  public T createReference(Boolean readOnly, boolean disableLazyLoad, Object id, PersistenceContext pc) {
    if (cacheSharableBeans && !disableLazyLoad && !Boolean.FALSE.equals(readOnly)) {
      CachedBeanData d = cacheHelp.beanCacheGetData(cacheKey(id));
      if (d != null) {
        Object shareableBean = d.getSharableBean();
        if (shareableBean != null) {
          if (isReadAuditing()) {
            readAuditBean("ref", "", shareableBean);
          }
          return (T) shareableBean;
        }
      }
    }
    try {
      if (inheritInfo != null && !inheritInfo.isConcrete()) {
        return findReferenceBean(id, pc);
      }
      EntityBean eb = createEntityBean();
      id = convertSetId(id, eb);
      EntityBeanIntercept ebi = eb._ebean_getIntercept();
      if (disableLazyLoad) {
        ebi.setDisableLazyLoad(true);
      } else {
        ebi.setBeanLoader(refBeanLoader());
      }
      ebi.setReference(idPropertyIndex);
      if (Boolean.TRUE == readOnly) {
        ebi.setReadOnly(true);
      }
      if (pc != null) {
        contextPut(pc, id, eb);
        ebi.setPersistenceContext(pc);
      }
      return (T) eb;
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }
  }

  SingleBeanLoader refBeanLoader() {
    return new SingleBeanLoader.Ref(ebeanServer);
  }

  SingleBeanLoader l2BeanLoader() {
    return new SingleBeanLoader.L2(ebeanServer);
  }

  /**
   * Create a non read only reference bean without checking cacheSharableBeans.
   */
  public T createReference(Object id, PersistenceContext pc) {
    if (inheritInfo != null && !inheritInfo.isConcrete()) {
      return findReferenceBean(id, pc);
    }
    return createRef(id, pc);
  }

  @SuppressWarnings("unchecked")
  public T createRef(Object id, PersistenceContext pc) {
    try {
      EntityBean eb = createEntityBean();
      id = convertSetId(id, eb);
      EntityBeanIntercept ebi = eb._ebean_getIntercept();
      ebi.setBeanLoader(refBeanLoader());
      ebi.setReference(idPropertyIndex);
      if (pc != null) {
        contextPut(pc, id, eb);
        ebi.setPersistenceContext(pc);
      }
      return (T) eb;
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }
  }

  /**
   * Return the bean property traversing the object graph and taking into
   * account inheritance.
   */
  @Override
  public BeanProperty findPropertyFromPath(String path) {
    BeanDescriptor<?> other = this;
    while (true) {
      String[] split = SplitName.splitBegin(path);
      if (split[1] == null) {
        return other._findBeanProperty(split[0]);
      }
      BeanPropertyAssoc<?> assocProp = (BeanPropertyAssoc<?>) other._findBeanProperty(split[0]);
      if (assocProp == null) {
        throw new IllegalStateException("Unknown property path [" + split[0] + "] from[" + path + "]");
      }
      BeanDescriptor<?> targetDesc = assocProp.getTargetDescriptor();
      path = split[1];
      other = targetDesc;
    }
  }

  @Override
  public BeanType<?> getBeanTypeAtPath(String path) {
    return getBeanDescriptor(path);
  }

  /**
   * Return the BeanDescriptor for a given path of Associated One or Many beans.
   */
  public BeanDescriptor<?> getBeanDescriptor(String path) {
    BeanDescriptor<?> result = this;
    while (true) {
      if (path == null) {
        return result;
      }
      String[] splitBegin = SplitName.splitBegin(path);
      BeanProperty beanProperty = result.findProperty(splitBegin[0]);
      if (beanProperty instanceof BeanPropertyAssoc<?>) {
        BeanPropertyAssoc<?> assocProp = (BeanPropertyAssoc<?>) beanProperty;
        path = splitBegin[1];
        result = assocProp.getTargetDescriptor();
      } else {
        throw new PersistenceException("Invalid path " + path + " from " + result.getFullName());
      }
    }
  }

  /**
   * Return the BeanDescriptor of another bean type.
   */
  public <U> BeanDescriptor<U> getBeanDescriptor(Class<U> otherType) {
    return owner.getBeanDescriptor(otherType);
  }

  /**
   * Returns true, if the table is managed (i.e. an existing m2m relation).
   */
  public boolean isTableManaged(String tableName) {
    return owner.isTableManaged(tableName);
  }
  
  /**
   * Return the order column property.
   */
  public BeanProperty getOrderColumn() {
    return orderColumn;
  }

  /**
   * Return the "shadow" property to support unidirectional relationships.
   * <p>
   * For bidirectional this is a real property on the bean. For unidirectional
   * relationships we have this 'shadow' property which is not externally
   * visible.
   */
  public BeanPropertyAssocOne<?> getUnidirectional() {
    BeanDescriptor<?> other = this;
    while (true) {
      if (other.unidirectional != null) {
        return other.unidirectional;
      }
      if (other.inheritInfo != null && !other.inheritInfo.isRoot()) {
        other = other.inheritInfo.getParent().desc();
        continue;
      }
      return null;
    }
  }

  /**
   * Return true if this bean type should use IdGeneration.
   * <p>
   * If this is false and the Id is null it is assumed that a database auto
   * increment feature is being used to populate the id.
   */
  public boolean isUseIdGenerator() {
    return idGenerator != null;
  }

  /**
   * Return bean class name.
   */
  public String getDescriptorId() {
    return fullName;
  }

  /**
   * Return the class type this BeanDescriptor describes.
   */
  @Override
  @Nonnull
  public Class<T> getBeanType() {
    return beanType;
  }

  /**
   * Return the bean class name this descriptor is used for.
   * <p>
   * If this BeanDescriptor is for a table then this returns the table name
   * instead.
   */
  @Override
  @Nonnull
  public String getFullName() {
    return fullName;
  }

  /**
   * Return the short name of the entity bean.
   */
  @Override
  @Nonnull
  public String getName() {
    return name;
  }

  /**
   * Return the simple name of the entity bean.
   */
  public String getSimpleName() {
    return beanType.getSimpleName();
  }

  /**
   * Summary description.
   */
  @Override
  public String toString() {
    return fullName;
  }

  /**
   * Get the bean from the persistence context.
   */
  public Object contextGet(PersistenceContext pc, Object id) {
    return pc.get(rootBeanType, id);
  }

  /**
   * Get the bean from the persistence context with delete check option.
   */
  public PersistenceContext.WithOption contextGetWithOption(PersistenceContext pc, Object id) {
    return pc.getWithOption(rootBeanType, id);
  }

  /**
   * Put the bean into the persistence context.
   */
  public void contextPut(PersistenceContext pc, Object id, Object bean) {
    pc.put(rootBeanType, id, bean);
  }

  /**
   * Put the bean into the persistence context if it is absent.
   */
  @Override
  public Object contextPutIfAbsent(PersistenceContext pc, Object id, EntityBean localBean) {
    return pc.putIfAbsent(rootBeanType, id, localBean);
  }

  /**
   * Create a reference bean and put it in the persistence context (and return it).
   */
  public Object contextRef(PersistenceContext pc, Boolean readOnly, boolean disableLazyLoad, Object id) {
    return createReference(readOnly, disableLazyLoad, id, pc);
  }

  /**
   * Clear a bean from the persistence context.
   */
  public void contextClear(PersistenceContext pc, Object idValue) {
    pc.clear(rootBeanType, idValue);
  }

  /**
   * Delete a bean from the persistence context (such that we don't fetch it in the same transaction).
   */
  public void contextDeleted(PersistenceContext pc, Object idValue) {
    pc.deleted(rootBeanType, idValue);
  }

  /**
   * Return the Id property name or null if no Id property exists.
   */
  public String getIdName() {
    return (idProperty == null) ? null : idProperty.getName();
  }

  /**
   * Helper method to return the unique property. If only one property makes up
   * the unique id then it's value is returned. If there is a concatenated
   * unique id then a Map is built with the keys being the names of the
   * properties that make up the unique id.
   */
  public Object getId(EntityBean bean) {
    return (idProperty == null) ? null : idProperty.getValueIntercept(bean);
  }

  /**
   * Return the cache key for the given bean (based on id value).
   */
  String cacheKeyForBean(EntityBean bean) {
    return cacheKey(idProperty.getValue(bean));
  }

  /**
   * Return the cache key for the given id value.
   */
  public String cacheKey(Object id) {
    return idBinder.cacheKey(id);
  }

  @Override
  public Object beanId(Object bean) {
    return getId((EntityBean) bean);
  }

  @Override
  public Object getBeanId(T bean) {
    return getId((EntityBean) bean);
  }

  /**
   * Return the Id value for the bean with embeddedId beans converted into maps.
   * <p>
   * The usage is to provide simple id types for JSON processing (for embeddedId's).
   */
  public Object getIdForJson(Object bean) {
    return idBinder.getIdForJson((EntityBean) bean);
  }

  /**
   * Convert the idValue assuming embeddedId values are Maps.
   * <p>
   * The usage is to provide simple id types for JSON processing (for embeddedId's).
   */
  Object convertIdFromJson(Object idValue) {
    return idBinder.convertIdFromJson(idValue);
  }

  /**
   * Return the default order by that may need to be added if a many property is
   * included in the query.
   */
  public String getDefaultOrderBy() {
    return idBinder.getDefaultOrderBy();
  }

  /**
   * Convert the type of the idValue if required.
   */
  public Object convertId(Object idValue) {
    return idBinder.convertId(idValue);
  }

  /**
   * Set the bean id value converting if necessary.
   */
  @Override
  public void setBeanId(T bean, Object idValue) {
    idBinder.convertSetId(idValue, (EntityBean) bean);
  }

  /**
   * Convert and set the id value.
   * <p>
   * If the bean is not null, the id value is set to the id property of the bean
   * after it has been converted to the correct type.
   */
  public Object convertSetId(Object idValue, EntityBean bean) {
    return idBinder.convertSetId(idValue, bean);
  }

  /**
   * Set the Id value to the bean (without type conversion).
   */
  public void setId(Object idValue, EntityBean bean) {
    idProperty.setValueIntercept(bean, idValue);
  }

  @Override
  public Property getProperty(String propName) {
    return findProperty(propName);
  }

  /**
   * Get a BeanProperty by its name.
   */
  public BeanProperty getBeanProperty(String propName) {
    return propMap.get(propName);
  }

  public void sort(List<T> list, String sortByClause) {
    list.sort(getElComparator(sortByClause));
  }

  public ElComparator<T> getElComparator(String propNameOrSortBy) {
    return comparatorCache.computeIfAbsent(propNameOrSortBy, this::createComparator);
  }

  /**
   * Register all the assoc many properties on this bean that are not populated with the load context.
   * <p>
   * This provides further lazy loading via the load context.
   */
  public void lazyLoadRegister(String prefix, EntityBeanIntercept ebi, EntityBean bean, LoadContext loadContext) {
    // load the List/Set/Map proxy objects (deferred fetching of lists)
    for (BeanPropertyAssocMany<?> many : propertiesMany()) {
      if (!ebi.isLoadedProperty(many.getPropertyIndex())) {
        BeanCollection<?> ref = many.createReferenceIfNull(bean);
        if (ref != null && !ref.isRegisteredWithLoadContext()) {
          String path = SplitName.add(prefix, many.getName());
          loadContext.register(path, many, ref);
        }
      }
    }
  }

  /**
   * Return true if the lazy loading property is a Many in which case just
   * define a Reference for the collection and not invoke a query.
   */
  public boolean lazyLoadMany(EntityBeanIntercept ebi) {
    return lazyLoadMany(ebi, null);
  }

  public boolean lazyLoadMany(EntityBeanIntercept ebi, LoadBeanContext parent) {
    int lazyLoadProperty = ebi.getLazyLoadPropertyIndex();
    if (lazyLoadProperty == -1) {
      return false;
    }
    if (inheritInfo != null) {
      return descOf(ebi.getOwner().getClass()).lazyLoadMany(ebi, lazyLoadProperty, parent);
    }
    return lazyLoadMany(ebi, lazyLoadProperty, parent);
  }

  /**
   * Return true if this is a generated property.
   */
  public boolean isGeneratedProperty(int propertyIndex) {
    return propertiesIndex[propertyIndex].isGenerated();
  }

  /**
   * Check for lazy loading of many property.
   */
  private boolean lazyLoadMany(EntityBeanIntercept ebi, int lazyLoadProperty, LoadBeanContext loadBeanContext) {
    BeanProperty lazyLoadBeanProp = propertiesIndex[lazyLoadProperty];
    if (lazyLoadBeanProp instanceof BeanPropertyAssocMany<?>) {
      BeanPropertyAssocMany<?> manyProp = (BeanPropertyAssocMany<?>) lazyLoadBeanProp;
      final BeanCollection<?> collection = manyProp.createReference(ebi.getOwner());
      ebi.setLoadedLazy();
      if (loadBeanContext != null) {
        loadBeanContext.register(manyProp, collection);
      }
      return true;
    }
    return false;
  }

  /**
   * Return the correct BeanDescriptor based on the bean class type.
   */
  BeanDescriptor<?> descOf(Class<?> type) {
    return inheritInfo.readType(type).desc();
  }

  /**
   * Return a Comparator for local sorting of lists.
   *
   * @param sortByClause list of property names with optional ASC or DESC suffix.
   */
  @SuppressWarnings("unchecked")
  private ElComparator<T> createComparator(String sortByClause) {
    SortByClause sortBy = SortByClauseParser.parse(sortByClause);
    if (sortBy.size() == 1) {
      // simple comparator for a single property
      return createPropertyComparator(sortBy.getProperties().get(0));
    }

    // create a compound comparator based on the list of properties
    ElComparator<T>[] comparators = new ElComparator[sortBy.size()];
    List<SortByClause.Property> sortProps = sortBy.getProperties();
    for (int i = 0; i < sortProps.size(); i++) {
      SortByClause.Property sortProperty = sortProps.get(i);
      comparators[i] = createPropertyComparator(sortProperty);
    }
    return new ElComparatorCompound<>(comparators);
  }

  private ElComparator<T> createPropertyComparator(SortByClause.Property sortProp) {
    ElPropertyValue elGetValue = getElGetValue(sortProp.getName());
    if (elGetValue == null) {
      logger.error("Sort property [" + sortProp + "] not found in " + beanType + ". Cannot sort.");
      return new ElComparatorNoop<>();
    }
    if (elGetValue.isAssocMany()) {
      logger.error("Sort property [" + sortProp + "] in " + beanType + " is a many-property. Cannot sort.");
      return new ElComparatorNoop<>();
    }
    Boolean nullsHigh = sortProp.getNullsHigh();
    if (nullsHigh == null) {
      nullsHigh = Boolean.TRUE;
    }
    return new ElComparatorProperty<>(elGetValue, sortProp.isAscending(), nullsHigh);
  }

  @Override
  public boolean isValidExpression(String propertyName) {
    try {
      return (getElGetValue(propertyName) != null);
    } catch (PersistenceException e) {
      return false;
    }
  }

  /**
   * Get an Expression language Value object.
   */
  public ElPropertyValue getElGetValue(String propName) {
    ElPropertyValue elGetValue = elCache.get(propName);
    if (elGetValue != null) {
      return elGetValue;
    }
    elGetValue = buildElGetValue(propName, null, false);
    if (elGetValue != null) {
      elCache.put(propName, elGetValue);
    }
    return elGetValue;
  }

  @Override
  public ExpressionPath getExpressionPath(String path) {
    return getElGetValue(path);
  }

  /**
   * Similar to ElPropertyValue but also uses foreign key shortcuts.
   * <p>
   * The foreign key shortcuts means we can avoid unnecessary joins.
   */
  public ElPropertyDeploy getElPropertyDeploy(String propName) {
    ElPropertyDeploy elProp = elDeployCache.get(propName);
    if (elProp != null) {
      return elProp;
    }
    if (!propName.contains(".")) {
      // No period means simple property and no need to look for
      // foreign key properties (in order to avoid an extra join)
      elProp = getElGetValue(propName);
    } else {
      elProp = buildElGetValue(propName, null, true);
    }
    if (elProp != null) {
      elDeployCache.put(propName, elProp);
    }
    return elProp;
  }

  ElPropertyValue buildElGetValue(String propName, ElPropertyChainBuilder chain, boolean propertyDeploy) {
    if (propertyDeploy && chain != null) {
      ElPropertyDeploy fk = elDeployCache.get(propName);
      if (fk instanceof BeanFkeyProperty) {
        // propertyDeploy chain for foreign key column
        return ((BeanFkeyProperty) fk).create(chain.getExpression(), chain.isContainsMany());
      }
    }
    int basePos = propName.indexOf('.');
    if (basePos > -1) {
      // nested or embedded property
      String baseName = propName.substring(0, basePos);
      BeanProperty assocProp = _findBeanProperty(baseName);
      if (assocProp == null) {
        return null;
      }
      String remainder = propName.substring(basePos + 1);
      return assocProp.buildElPropertyValue(propName, remainder, chain, propertyDeploy);
    }

    BeanProperty property = _findBeanProperty(propName);
    if (chain == null) {
      return property;
    }
    if (property == null) {
      throw new PersistenceException("No property found for [" + propName + "] in expression " + chain.getExpression());
    }
    if (property.containsMany()) {
      chain.setContainsMany();
    }
    return chain.add(property).build();
  }

  /**
   * Return the property path given the db table and column.
   */
  public String findBeanPath(String schemaName, String tableName, String columnName) {
    if (matchBaseTable(tableName)) {
      return columnPath.get(columnName);
    }
    BeanPropertyAssoc<?> assocProperty = tablePath.get(tableName);
    if (assocProperty == null) {
      assocProperty = tablePath.get(schemaName + "." + tableName);
    }
    if (assocProperty != null) {
      String relativePath = assocProperty.getTargetDescriptor().findBeanPath(schemaName, tableName, columnName);
      if (relativePath != null) {
        return SplitName.add(assocProperty.getName(), relativePath);
      }
    }
    return null;
  }

  boolean matchBaseTable(String tableName) {
    return tableName.isEmpty()
      || baseTable.equalsIgnoreCase(tableName)
      || baseTable.endsWith("." + tableName);
  }

  /**
   * Return a 'dynamic property' used to read a formula.
   */
  private STreeProperty findSqlTreeFormula(String formula, String path) {
    String key = formula + "-" + path;
    return dynamicProperty.computeIfAbsent(key, (fullKey) -> FormulaPropertyPath.create(this, formula, path));
  }

  /**
   * Return a property that is part of the SQL tree.
   * <p>
   * The property can be a dynamic formula or a well known bean property.
   */
  @Override
  public STreeProperty findPropertyWithDynamic(String propName, String path) {
    if (propName.indexOf('(') > -1) {
      return findSqlTreeFormula(propName, path);
    }
    return _findBeanProperty(propName);
  }

  /**
   * Find a BeanProperty including searching the inheritance hierarchy.
   * <p>
   * This searches this BeanDescriptor and then searches further down the
   * inheritance tree (not up).
   */
  @Override
  public BeanProperty findProperty(String propName) {
    int basePos = propName.indexOf('.');
    if (basePos > -1) {
      // embedded property
      String baseName = propName.substring(0, basePos);
      return _findBeanProperty(baseName);
    }

    return _findBeanProperty(propName);
  }

  BeanProperty _findBeanProperty(String propName) {
    BeanProperty prop = propMap.get(propName);
    if (prop == null && inheritInfo != null) {
      // search in sub types...
      return inheritInfo.findSubTypeProperty(propName);
    }
    return prop;
  }

  /**
   * Reset the many properties to empty state ready for reloading.
   */
  public void resetManyProperties(Object dbBean) {
    EntityBean bean = (EntityBean) dbBean;
    for (BeanPropertyAssocMany<?> many : propertiesMany) {
      if (many.isCascadeRefresh()) {
        many.resetMany(bean);
      }
    }
  }

  /**
   * Return true if this bean can cache sharable instances.
   * <p>
   * This means is has no relationships and has readOnly=true in its cache
   * options.
   */
  public boolean isCacheSharableBeans() {
    return cacheSharableBeans;
  }

  /**
   * Return true if queries for beans of this type are auto tunable.
   */
  public boolean isAutoTunable() {
    return autoTunable;
  }

  /**
   * Returns the Inheritance mapping information. This will be null if this type
   * of bean is not involved in any ORM inheritance mapping.
   */
  @Override
  public InheritInfo getInheritInfo() {
    return inheritInfo;
  }

  @Override
  public boolean hasInheritance() {
    return inheritInfo != null;
  }

  @Override
  public String getDiscColumn() {
    return inheritInfo.getDiscriminatorColumn();
  }

  /**
   * Return the discriminator value for this bean type (or null when there is no inheritance).
   */
  public String getDiscValue() {
    return inheritInfo == null ? null : inheritInfo.getDiscriminatorStringValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T createBeanUsingDisc(Object discValue) {
    return (T) inheritInfo.getType(discValue.toString()).desc().createBean();
  }

  @Override
  public void addInheritanceWhere(Query<?> query) {
    if (inheritInfo != null && !inheritInfo.isRoot()) {
      query.where().eq(inheritInfo.getDiscriminatorColumn(), inheritInfo.getDiscriminatorValue());
    }
  }

  /**
   * Return true if this is an embedded bean.
   */
  public boolean isEmbedded() {
    return EntityType.EMBEDDED == entityType;
  }

  /**
   * Return the compound unique constraints.
   */
  public IndexDefinition[] getIndexDefinitions() {
    return indexDefinitions;
  }

  /**
   * Return the beanListener.
   */
  @Override
  public BeanPersistListener getPersistListener() {
    return persistListener;
  }

  /**
   * Return the beanFinder (Migrate over to getFindController).
   */
  public BeanFindController getBeanFinder() {
    return beanFinder;
  }

  /**
   * Return the find controller (SPI interface).
   */
  @Override
  public BeanFindController getFindController() {
    return beanFinder;
  }

  /**
   * Return the BeanQueryAdapter or null if none is defined.
   */
  @Override
  public BeanQueryAdapter getQueryAdapter() {
    return queryAdapter;
  }

  /**
   * De-register the BeanPersistListener.
   */
  public void deregister(BeanPersistListener listener) {
    BeanPersistListener currentListener = persistListener;
    if (currentListener != null) {
      if (currentListener instanceof ChainedBeanPersistListener) {
        // remove it from the existing chain
        persistListener = ((ChainedBeanPersistListener) currentListener).deregister(listener);
      } else if (currentListener.equals(listener)) {
        persistListener = null;
      }
    }
  }

  /**
   * De-register the BeanPersistController.
   */
  public void deregister(BeanPersistController controller) {
    BeanPersistController currentController = persistController;
    if (currentController != null) {
      if (currentController instanceof ChainedBeanPersistController) {
        // remove it from the existing chain
        persistController = ((ChainedBeanPersistController) currentController).deregister(controller);
      } else if (currentController.equals(controller)) {
        persistController = null;
      }
    }
  }

  /**
   * Register the new BeanPersistController.
   */
  public void register(BeanPersistListener newPersistListener) {
    if (newPersistListener.isRegisterFor(beanType)) {
      BeanPersistListener currentListener = persistListener;
      if (currentListener == null) {
        persistListener = newPersistListener;
      } else {
        if (currentListener instanceof ChainedBeanPersistListener) {
          // add it to the existing chain
          persistListener = ((ChainedBeanPersistListener) currentListener).register(newPersistListener);
        } else {
          // build new chain of the 2
          persistListener = new ChainedBeanPersistListener(currentListener, newPersistListener);
        }
      }
    }
  }

  /**
   * Register the new BeanPersistController.
   */
  public void register(BeanPersistController newController) {
    if (newController.isRegisterFor(beanType)) {
      BeanPersistController currentController = persistController;
      if (currentController == null) {
        persistController = newController;
      } else {
        if (currentController instanceof ChainedBeanPersistController) {
          persistController = ((ChainedBeanPersistController) currentController).register(newController);
        } else {
          persistController = new ChainedBeanPersistController(currentController, newController);
        }
      }
    }
  }

  /**
   * Return the Controller.
   */
  @Override
  public BeanPersistController getPersistController() {
    return persistController;
  }

  /**
   * Returns true if this bean is based on RawSql.
   */
  @Override
  public boolean isRawSqlBased() {
    return EntityType.SQL == entityType;
  }

  /**
   * Return the DB comment for the base table.
   */
  public String getDbComment() {
    return dbComment;
  }

  /**
   * Return true if foreign keys to the base table should be suppressed.
   */
  public boolean suppressForeignKey() {
    return partitionMeta != null;
  }

  /**
   * Return the partition details of the bean.
   */
  public PartitionMeta getPartitionMeta() {
    return partitionMeta;
  }

  /**
   * Return the storage engine.
   */
  public String getStorageEngine() {
    return storageEngine;
  }

  /**
   * Return the dependent tables for a view based entity.
   */
  public String[] getDependentTables() {
    return dependentTables;
  }

  /**
   * Return the base table. Only properties mapped to the base table are by default persisted.
   */
  @Override
  public String getBaseTable() {
    return baseTable;
  }

  /**
   * Return true if this type is a base table entity type.
   */
  public boolean isBaseTable() {
    return baseTable != null && entityType == EntityType.ORM;
  }

  /**
   * Return the base table to use given the query temporal mode.
   */
  @Override
  public String getBaseTable(SpiQuery.TemporalMode mode) {
    switch (mode) {
      case DRAFT:
        return draftTable;
      case VERSIONS:
        return baseTableVersionsBetween;
      case AS_OF:
        return baseTableAsOf;
      default:
        return baseTable;
    }
  }

  /**
   * Return the associated draft table.
   */
  public String getDraftTable() {
    return draftTable;
  }

  /**
   * Return true if read auditing is on this entity bean.
   */
  public boolean isReadAuditing() {
    return readAuditing;
  }

  @Override
  public boolean isSoftDelete() {
    return softDelete;
  }

  public void setSoftDeleteValue(EntityBean bean) {
    softDeleteProperty.setSoftDeleteValue(bean);
  }

  String getSoftDeleteDbSet() {
    return softDeleteProperty.getSoftDeleteDbSet();
  }

  @Override
  public String getSoftDeletePredicate(String tableAlias) {
    return softDeleteProperty.getSoftDeleteDbPredicate(tableAlias);
  }

  @Override
  public void markAsDeleted(EntityBean bean) {
    if (softDeleteProperty == null) {
      Object id = getId(bean);
      logger.info("(Lazy) loading unsuccessful for type:{} id:{} - expecting when bean has been deleted", getName(), id);
      bean._ebean_getIntercept().setLazyLoadFailure(id);
    } else {
      setSoftDeleteValue(bean);
      bean._ebean_getIntercept().setLoaded();
      setAllLoaded(bean);
    }
  }

  /**
   * Return true if this entity type is draftable.
   */
  public boolean isDraftable() {
    return draftable;
  }

  /**
   * Return true if this entity type is a draftable element (child).
   */
  public boolean isDraftableElement() {
    return draftableElement;
  }

  @Override
  public boolean isEmbeddedPath(String propertyPath) {
    ElPropertyDeploy elProp = getElPropertyDeploy(propertyPath);
    if (elProp == null) {
      throw new PersistenceException("Invalid path " + propertyPath + " from " + getFullName());
    }
    return elProp.getBeanProperty().isEmbedded();
  }

  @Override
  public ExtraJoin extraJoin(String propertyPath) {
    ElPropertyValue elGetValue = getElGetValue(propertyPath);
    if (elGetValue != null) {
      BeanProperty beanProperty = elGetValue.getBeanProperty();
      if (beanProperty instanceof BeanPropertyAssoc<?>) {
        BeanPropertyAssoc<?> assocProp = (BeanPropertyAssoc<?>) beanProperty;
        if (!assocProp.isEmbedded()) {
          return new ExtraJoin(assocProp, elGetValue.containsMany());
        }
      }
    }
    return null;
  }

  @Override
  public void inheritanceLoad(SqlBeanLoad sqlBeanLoad, STreeProperty property, DbReadContext ctx) {
    BeanProperty p = getBeanProperty(property.getName());
    if (p != null) {
      p.load(sqlBeanLoad);
    } else {
      property.loadIgnore(ctx);
    }
  }

  void setUnmappedJson(EntityBean bean, Map<String, Object> unmappedProperties) {
    if (unmappedJson != null) {
      unmappedJson.setValueIntercept(bean, unmappedProperties);
    }
  }

  /**
   * Set the Tenant Id value to the bean.
   */
  public void setTenantId(EntityBean entityBean, Object tenantId) {
    if (tenant != null) {
      tenant.setTenantValue(entityBean, tenantId);
    }
  }

  /**
   * Set the draft to true for this entity bean instance.
   * This bean is being loaded via asDraft() query.
   */
  @Override
  public void setDraft(EntityBean entityBean) {
    if (draft != null) {
      draft.setValue(entityBean, true);
    }
  }

  /**
   * Return true if the bean is considered a 'draft' instance (not 'live').
   */
  public boolean isDraftInstance(EntityBean entityBean) {
    if (draft != null) {
      return Boolean.TRUE == draft.getValue(entityBean);
    }
    // no draft property - so return false
    return false;
  }

  @Override
  public boolean isToManyDirty(EntityBean bean) {
    final EntityBeanIntercept ebi = bean._ebean_getIntercept();
    for (BeanPropertyAssocMany<?> many : propertiesManySave) {
      if (ebi.isLoadedProperty(many.getPropertyIndex())) {
        final BeanCollection<?> value = (BeanCollection<?>) many.getValue(bean);
        if (value != null && value.hasModifications()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Return true if the bean is draftable and considered a 'live' instance.
   */
  public boolean isLiveInstance(EntityBean entityBean) {
    if (draft != null) {
      return Boolean.FALSE == draft.getValue(entityBean);
    }
    // no draft property - so return false
    return false;
  }

  /**
   * If there is a @DraftDirty property set it's value on the bean.
   */
  public void setDraftDirty(EntityBean entityBean, boolean value) {
    if (draftDirty != null) {
      // check to see if the dirty property has already
      // been set and if so do not set the value
      if (!entityBean._ebean_getIntercept().isChangedProperty(draftDirty.getPropertyIndex())) {
        draftDirty.setValueIntercept(entityBean, value);
      }
    }
  }

  /**
   * Optimise the draft query fetching any draftable element relationships.
   */
  public void draftQueryOptimise(Query<T> query) {
    // use per query PersistenceContext to ensure fresh beans loaded
    query.setPersistenceContextScope(PersistenceContextScope.QUERY);
    draftHelp.draftQueryOptimise(query);
  }

  /**
   * Return true if this entity bean has history support.
   */
  @Override
  public boolean isHistorySupport() {
    return historySupport;
  }

  /**
   * Return the identity generation type.
   */
  @Override
  public IdType getIdType() {
    return idType;
  }

  /**
   * Set the generated Id value if appropriate.
   */
  public void setGeneratedId(EntityBean entityBean, Transaction transaction) {
    if (idGenerator == null || idProperty == null || idProperty.isEmbedded()) {
      return;
    }
    if (isNullOrZero(idProperty.getValue(entityBean))) {
      convertSetId(nextId(transaction), entityBean);
    }
  }

  /**
   * Return true if the Id value is marked as a <code>@GeneratedValue</code>.
   */
  public boolean isIdGeneratedValue() {
    return idGeneratedValue;
  }

  public IdentityMode getIdentityMode() {
    return identityMode;
  }

  /**
   * Return the SQL used to return the last inserted id.
   * <p>
   * This is only used with Identity columns and getGeneratedKeys is not
   * supported.
   */
  public String getSelectLastInsertedId(boolean publish) {
    return publish ? selectLastInsertedId : selectLastInsertedIdDraft;
  }

  /**
   * Return true if this bean uses a SQL select to fetch the last inserted id value.
   */
  public boolean supportsSelectLastInsertedId() {
    return selectLastInsertedId != null;
  }

  @Override
  @Nonnull
  public Collection<? extends Property> allProperties() {
    return propertiesAll();
  }

  /**
   * Return a collection of all BeanProperty. This includes transient properties.
   */
  public Collection<BeanProperty> propertiesAll() {
    return propMap.values();
  }

  /**
   * Return the property that holds unmapped JSON content.
   */
  public BeanProperty propertyUnmappedJson() {
    return unmappedJson;
  }

  /**
   * Return the non transient non id properties.
   */
  public BeanProperty[] propertiesNonTransient() {
    return propertiesNonTransient;
  }

  /**
   * Return the transient properties.
   */
  public BeanProperty[] propertiesTransient() {
    return propertiesTransient;
  }

  /**
   * Return the beans that are embedded. These share the base table with the owner bean.
   */
  public BeanPropertyAssocOne<?>[] propertiesEmbedded() {
    return propertiesEmbedded;
  }

  /**
   * Return true if the query detail includes an aggregation property.
   */
  public boolean includesAggregation(OrmQueryDetail detail) {
    return detail != null && propertiesAggregate.length > 0 && includesAggregation(detail.getChunk(null, false));
  }

  private boolean includesAggregation(OrmQueryProperties rootProps) {
    if (rootProps != null) {
      final Set<String> included = rootProps.getIncluded();
      if (included != null) {
        for (BeanProperty property : propertiesAggregate) {
          if (included.contains(property.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Set all properties to be loaded (recurse to embedded beans).
   */
  public void setAllLoaded(EntityBean bean) {
    bean._ebean_getIntercept().setLoadedPropertyAll();
    for (BeanPropertyAssocOne<?> embedded : propertiesEmbedded) {
      embedded.setAllLoadedEmbedded(bean);
    }
  }

  public TableJoin getPrimaryKeyJoin() {
    return primaryKeyJoin;
  }

  @Override
  public BeanProperty getIdProperty() {
    return idProperty;
  }

  /**
   * Return true if this bean should be inserted rather than updated.
   *
   * @param ebi        The entity bean intercept
   * @param insertMode true if the 'root request' was an insert rather than an update
   */
  public boolean isInsertMode(EntityBeanIntercept ebi, boolean insertMode) {
    if (ebi.isLoaded()) {
      // must be an update as the bean is loaded
      return false;
    }
    if (idProperty.isEmbedded()) {
      // not using Id generator so just base on isLoaded()
      return !ebi.isLoaded();
    }
    if (!hasIdValue(ebi.getOwner())) {
      // No Id property means it must be an insert
      return true;
    }
    // same as the 'root request'
    return insertMode;
  }

  public boolean isReference(EntityBeanIntercept ebi) {
    return ebi.isReference() || referenceIdPropertyOnly(ebi);
  }

  public boolean referenceIdPropertyOnly(EntityBeanIntercept ebi) {
    return idOnlyReference && ebi.hasIdOnly(idPropertyIndex);
  }

  public boolean isIdLoaded(EntityBeanIntercept ebi) {
    // assume id loaded for IdClass case with idPropertyIndex == -1
    return idPropertyIndex == -1 ? true : ebi.isLoadedProperty(idPropertyIndex);
  }

  boolean hasIdValue(EntityBean bean) {
    return (idProperty != null && !isNullOrZero(idProperty.getValue(bean)));
  }

  boolean hasVersionProperty(EntityBeanIntercept ebi) {
    return versionPropertyIndex > -1 && ebi.isLoadedProperty(versionPropertyIndex);
  }

  /**
   * Set the version value returning it in primitive long form.
   */
  @SuppressWarnings("unchecked")
  public long setVersion(EntityBean entityBean, Object versionValue) {
    versionProperty.setValueIntercept(entityBean, versionValue);
    return versionProperty.scalarType.asVersion(versionValue);
  }

  /**
   * Return the version value in primitive long form (if exists and set).
   */
  @SuppressWarnings("unchecked")
  public long getVersion(EntityBean entityBean) {
    if (versionProperty == null) {
      return 0;
    }
    Object value = versionProperty.getValue(entityBean);
    return value == null ? 0 : versionProperty.scalarType.asVersion(value);
  }

  /**
   * Check for mutable scalar types and mark as dirty if necessary.
   */
  public void checkMutableProperties(EntityBeanIntercept ebi) {
    for (BeanProperty beanProperty : propertiesMutable) {
      int propertyIndex = beanProperty.getPropertyIndex();
      if (!ebi.isDirtyProperty(propertyIndex) && ebi.isLoadedProperty(propertyIndex)) {
        Object value = beanProperty.getValue(ebi.getOwner());
        if (value != null && beanProperty.isDirtyValue(ebi, value)) {
          // mutable scalar value which is considered dirty so mark
          // it as such so that it is included in an update
          ebi.markPropertyAsChanged(propertyIndex);
        }
      }
    }
  }

  public ConcurrencyMode getConcurrencyMode(EntityBeanIntercept ebi) {
    if (!hasVersionProperty(ebi)) {
      return ConcurrencyMode.NONE;
    } else {
      return concurrencyMode;
    }
  }

  /**
   * Return the diff comparing the bean values.
   */
  public Map<String, ValuePair> diff(EntityBean newBean, EntityBean oldBean) {
    Map<String, ValuePair> map = new LinkedHashMap<>();
    diff(null, map, newBean, oldBean);
    return map;
  }

  /**
   * Populate the diff for updates with flattened non-null property values.
   */
  public void diff(String prefix, Map<String, ValuePair> map, EntityBean newBean, EntityBean oldBean) {
    for (BeanProperty baseScalar : propertiesBaseScalar) {
      baseScalar.diff(prefix, map, newBean, oldBean);
    }
    for (BeanPropertyAssocOne<?> one : propertiesOne) {
      one.diff(prefix, map, newBean, oldBean);
    }
    for (BeanPropertyAssocOne<?> embedded : propertiesEmbedded) {
      embedded.diff(prefix, map, newBean, oldBean);
    }
  }

  /**
   * Appends the Id property to the OrderBy clause if it is not believed
   * to be already contained in the order by.
   * <p>
   * This is primarily used for paging queries to ensure that an order by clause is provided and that the order by
   * provides unique ordering of the rows (so that the paging is predicable).
   */
  public void appendOrderById(SpiQuery<T> query) {
    if (idProperty != null && !idProperty.isEmbedded() && !query.order().containsProperty(idProperty.getName())) {
      query.order().asc(idProperty.getName());
    }
  }

  @Override
  public STreeProperty[] propsBaseScalar() {
    return propertiesBaseScalar;
  }

  @Override
  public STreePropertyAssoc[] propsEmbedded() {
    return propertiesEmbedded;
  }

  @Override
  public STreePropertyAssocOne[] propsOne() {
    return propertiesOne;
  }

  @Override
  public STreePropertyAssocMany[] propsMany() {
    return propertiesMany;
  }

  /**
   * All the BeanPropertyAssocOne that are not embedded. These are effectively
   * joined beans. For ManyToOne and OneToOne associations.
   */
  public BeanPropertyAssocOne<?>[] propertiesOne() {
    return propertiesOne;
  }

  /**
   * Returns ManyToOnes and OneToOnes on the imported owning side.
   * <p>
   * Excludes OneToOnes on the exported side.
   */
  public BeanPropertyAssocOne<?>[] propertiesOneImported() {
    return propertiesOneImported;
  }

  /**
   * Imported Assoc Ones with cascade save true.
   */
  public BeanPropertyAssocOne<?>[] propertiesOneImportedSave() {
    return propertiesOneImportedSave;
  }

  /**
   * Imported Assoc Ones with cascade delete true.
   */
  public BeanPropertyAssocOne<?>[] propertiesOneImportedDelete() {
    return propertiesOneImportedDelete;
  }

  /**
   * Exported assoc ones with cascade save.
   */
  public BeanPropertyAssocOne<?>[] propertiesOneExportedSave() {
    return propertiesOneExportedSave;
  }

  /**
   * Exported assoc ones with delete cascade.
   */
  public BeanPropertyAssocOne<?>[] propertiesOneExportedDelete() {
    return propertiesOneExportedDelete;
  }

  /**
   * All Non Assoc Many's for this descriptor.
   */
  public BeanProperty[] propertiesNonMany() {
    return propertiesNonMany;
  }

  /**
   * All Assoc Many's for this descriptor.
   */
  public BeanPropertyAssocMany<?>[] propertiesMany() {
    return propertiesMany;
  }

  /**
   * Assoc Many's with save cascade.
   */
  public BeanPropertyAssocMany<?>[] propertiesManySave() {
    return propertiesManySave;
  }

  /**
   * Assoc Many's with delete cascade.
   */
  public BeanPropertyAssocMany<?>[] propertiesManyDelete() {
    return propertiesManyDelete;
  }

  /**
   * Assoc ManyToMany's.
   */
  public BeanPropertyAssocMany<?>[] propertiesManyToMany() {
    return propertiesManyToMany;
  }

  /**
   * Return the first version property that exists on the bean. Returns null if
   * no version property exists on the bean.
   * <p>
   * Note that this DOES NOT find a version property on an embedded bean.
   */
  public BeanProperty getVersionProperty() {
    return versionProperty;
  }

  /**
   * Return true if this type is tenant aware.
   */
  public boolean isMultiTenant() {
    return tenant != null;
  }

  /**
   * Return the tenant property when multi-tenant partitioning support is used.
   */
  public BeanProperty getTenantProperty() {
    return tenant;
  }

  /**
   * Scalar properties without the unique id or secondary table properties.
   */
  public BeanProperty[] propertiesBaseScalar() {
    return propertiesBaseScalar;
  }

  /**
   * Return the properties local to this type for inheritance.
   */
  public BeanProperty[] propertiesLocal() {
    return propertiesLocal;
  }

  /**
   * Return the properties set as generated values on insert.
   */
  public BeanProperty[] propertiesGenInsert() {
    return propertiesGenInsert;
  }

  /**
   * Return the properties set as generated values on update.
   */
  public BeanProperty[] propertiesGenUpdate() {
    return propertiesGenUpdate;
  }

  public void jsonWriteDirty(SpiJsonWriter writeJson, EntityBean bean, boolean[] dirtyProps) throws IOException {
    jsonHelp.jsonWriteDirty(writeJson, bean, dirtyProps);
  }

  void jsonWriteDirtyProperties(SpiJsonWriter writeJson, EntityBean bean, boolean[] dirtyProps) throws IOException {
    jsonHelp.jsonWriteDirtyProperties(writeJson, bean, dirtyProps);
  }

  public void jsonWriteMapEntry(SpiJsonWriter ctx, Map.Entry<?, ?> entry) throws IOException {
    throw new IllegalStateException("Unexpected - expect Element override");
  }

  public void jsonWriteElement(SpiJsonWriter ctx, Object element) {
    throw new IllegalStateException("Unexpected - expect Element override");
  }

  public Object jsonReadCollection(SpiJsonReader readJson, EntityBean parentBean) throws IOException {
    throw new IllegalStateException("Unexpected - expect Element override");
  }

  public boolean isJsonReadCollection() {
    return false;
  }

  public void jsonWrite(SpiJsonWriter writeJson, EntityBean bean) throws IOException {
    jsonHelp.jsonWrite(writeJson, bean, null);
  }

  public void jsonWrite(SpiJsonWriter writeJson, EntityBean bean, String key) throws IOException {
    jsonHelp.jsonWrite(writeJson, bean, key);
  }

  void jsonWriteProperties(SpiJsonWriter writeJson, EntityBean bean) {
    jsonHelp.jsonWriteProperties(writeJson, bean);
  }

  public T jsonRead(SpiJsonReader jsonRead, String path) throws IOException {
    return jsonHelp.jsonRead(jsonRead, path, true);
  }

  T jsonReadObject(SpiJsonReader jsonRead, String path) throws IOException {
    return jsonHelp.jsonRead(jsonRead, path, false);
  }

  public List<BeanProperty[]> getUniqueProps() {
    return propertiesUnique;
  }

  @Override
  public List<BeanType<?>> getInheritanceChildren() {
    if (hasInheritance()) {
      return getInheritInfo().getChildren()
        .stream()
        .map(InheritInfo::desc)
        .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public BeanType<?> getInheritanceParent() {
    return getInheritInfo() == null ? null : getInheritInfo().getParent().desc();
  }

  @Override
  public void visitAllInheritanceChildren(Consumer<BeanType<?>> visitor) {
    if (hasInheritance()) {
      getInheritInfo().visitChildren(info -> visitor.accept(info.desc()));
    }
  }
}
