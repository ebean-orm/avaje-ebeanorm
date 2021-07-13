package io.ebeaninternal.server.querydefn;

import io.ebean.CacheMode;
import io.ebean.CountDistinctOrder;
import io.ebean.Database;
import io.ebean.DtoQuery;
import io.ebean.Expression;
import io.ebean.ExpressionFactory;
import io.ebean.ExpressionList;
import io.ebean.FetchConfig;
import io.ebean.FetchGroup;
import io.ebean.FetchPath;
import io.ebean.FutureIds;
import io.ebean.FutureList;
import io.ebean.FutureRowCount;
import io.ebean.OrderBy;
import io.ebean.OrderBy.Property;
import io.ebean.PagedList;
import io.ebean.PersistenceContextScope;
import io.ebean.ProfileLocation;
import io.ebean.Query;
import io.ebean.QueryIterator;
import io.ebean.QueryType;
import io.ebean.RawSql;
import io.ebean.Transaction;
import io.ebean.UpdateQuery;
import io.ebean.Version;
import io.ebean.bean.CallOrigin;
import io.ebean.bean.ObjectGraphNode;
import io.ebean.bean.ObjectGraphOrigin;
import io.ebean.bean.PersistenceContext;
import io.ebean.event.BeanQueryRequest;
import io.ebean.event.readaudit.ReadEvent;
import io.ebean.plugin.BeanType;
import io.ebeaninternal.api.BindParams;
import io.ebeaninternal.api.CQueryPlanKey;
import io.ebeaninternal.api.CacheIdLookup;
import io.ebeaninternal.api.CacheIdLookupMany;
import io.ebeaninternal.api.CacheIdLookupSingle;
import io.ebeaninternal.api.HashQuery;
import io.ebeaninternal.api.ManyWhereJoins;
import io.ebeaninternal.api.NaturalKeyQueryData;
import io.ebeaninternal.api.SpiEbeanServer;
import io.ebeaninternal.api.SpiExpression;
import io.ebeaninternal.api.SpiExpressionList;
import io.ebeaninternal.api.SpiExpressionValidation;
import io.ebeaninternal.api.SpiNamedParam;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.SpiQuerySecondary;
import io.ebeaninternal.api.SpiTransaction;
import io.ebeaninternal.server.autotune.ProfilingListener;
import io.ebeaninternal.server.core.SpiOrmQueryRequest;
import io.ebeaninternal.server.deploy.BeanDescriptor;
import io.ebeaninternal.server.deploy.BeanNaturalKey;
import io.ebeaninternal.server.deploy.BeanPropertyAssocMany;
import io.ebeaninternal.server.deploy.InheritInfo;
import io.ebeaninternal.server.deploy.TableJoin;
import io.ebeaninternal.server.el.ElPropertyDeploy;
import io.ebeaninternal.server.expression.DefaultExpressionList;
import io.ebeaninternal.server.expression.IdInExpression;
import io.ebeaninternal.server.expression.SimpleExpression;
import io.ebeaninternal.server.query.NativeSqlQueryPlanKey;
import io.ebeaninternal.server.rawsql.SpiRawSql;
import io.ebeaninternal.server.transaction.ExternalJdbcTransaction;

import javax.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Default implementation of an Object Relational query.
 */
public class DefaultOrmQuery<T> extends AbstractQuery implements SpiQuery<T> {

  private static final String DEFAULT_QUERY_NAME = "default";

  private static final FetchConfig FETCH_CACHE = FetchConfig.ofCache();

  private static final FetchConfig FETCH_QUERY = FetchConfig.ofQuery();

  private static final FetchConfig FETCH_LAZY = FetchConfig.ofLazy();

  private final ReentrantLock lock = new ReentrantLock();

  private final Class<T> beanType;

  private final ExpressionFactory expressionFactory;

  private final BeanDescriptor<T> rootBeanDescriptor;

  private BeanDescriptor<T> beanDescriptor;

  private SpiEbeanServer server;

  private SpiTransaction transaction;

  /**
   * For lazy loading of ManyToMany we need to add a join to the intersection table. This is that
   * join to the intersection table.
   */
  private TableJoin m2mIncludeJoin;

  private ProfilingListener profilingListener;

  private Type type;

  private String label;

  private Mode mode = Mode.NORMAL;

  private Object tenantId;

  /**
   * Holds query in structured form.
   */
  private OrmQueryDetail detail;

  private int maxRows;

  private int firstRow;

  /**
   * Set to true to disable lazy loading on the object graph returned.
   */
  private boolean disableLazyLoading;

  /**
   * Lazy loading batch size (can override server wide default).
   */
  private int lazyLoadBatchSize;

  private OrderBy<T> orderBy;

  private String loadMode;

  private String loadDescription;

  private String generatedSql;

  private String lazyLoadProperty;

  private String lazyLoadManyPath;

  private boolean allowLoadErrors;

  /**
   * Flag set for report/DTO beans when we may choose to explicitly include the Id property.
   */
  private boolean manualId;

  /**
   * Set to true by a user wanting a DISTINCT query (id property must be excluded).
   */
  private boolean distinct;

  /**
   * Set to true if this is a future fetch using background threads.
   */
  private boolean futureFetch;

  /**
   * Only used for read auditing with findFutureList() query.
   */
  private ReadEvent futureFetchAudit;

  private int timeout;

  /**
   * The property used to get the key value for a Map.
   */
  private String mapKey;

  /**
   * Used for find by id type query.
   */
  private Object id;

  private Map<String, ONamedParam> namedParams;

  /**
   * Bind parameters when using the query language.
   */
  private BindParams bindParams;

  private DefaultExpressionList<T> textExpressions;

  private DefaultExpressionList<T> whereExpressions;

  private DefaultExpressionList<T> havingExpressions;

  private boolean asOfBaseTable;

  private int asOfTableCount;

  /**
   * Set for flashback style 'as of' query.
   */
  private Timestamp asOf;

  private TemporalMode temporalMode = TemporalMode.CURRENT;

  private Timestamp versionsStart;
  private Timestamp versionsEnd;

  private List<String> softDeletePredicates;

  private boolean disableReadAudit;

  private int bufferFetchSizeHint;

  private boolean usageProfiling = true;

  private CacheMode useBeanCache = CacheMode.AUTO;

  private CacheMode useQueryCache = CacheMode.OFF;

  private Boolean readOnly;

  private PersistenceContextScope persistenceContextScope;

  /**
   * Allow for explicit on off or null for default.
   */
  private Boolean autoTune;

  private LockWait forUpdate;
  private LockType lockType;

  private boolean singleAttribute;

  private CountDistinctOrder countDistinctOrder;

  /**
   * Set to true if this query has been tuned by autoTune.
   */
  private boolean autoTuned;

  /**
   * Root table alias. For {@link Query#alias(String)} command.
   */
  private String rootTableAlias;

  private String baseTable;

  /**
   * The node of the bean or collection that fired lazy loading. Not null if profiling is on and
   * this query is for lazy loading. Used to hook back a lazy loading query to the "original" query
   * point.
   */
  private ObjectGraphNode parentNode;

  private BeanPropertyAssocMany<?> lazyLoadForParentsProperty;

  /**
   * Hash of final query after AutoTune tuning.
   */
  private CQueryPlanKey queryPlanKey;

  private PersistenceContext persistenceContext;

  private ManyWhereJoins manyWhereJoins;

  private SpiRawSql rawSql;

  private boolean useDocStore;

  private String docIndexName;

  private OrmUpdateProperties updateProperties;

  private String nativeSql;

  private boolean orderById;

  private ProfileLocation profileLocation;

  public DefaultOrmQuery(BeanDescriptor<T> desc, SpiEbeanServer server, ExpressionFactory expressionFactory) {
    this.beanDescriptor = desc;
    this.rootBeanDescriptor = desc;
    this.beanType = desc.getBeanType();
    this.server = server;
    this.orderById = server.getServerConfig().isDefaultOrderById();
    this.disableLazyLoading = server.getServerConfig().isDisableLazyLoading();
    this.expressionFactory = expressionFactory;
    this.detail = new OrmQueryDetail();
  }

  public void setNativeSql(String nativeSql) {
    this.nativeSql = nativeSql;
  }

  @Override
  public <D> DtoQuery<D> asDto(Class<D> dtoClass) {
    return server.findDto(dtoClass, this);
  }

  @Override
  public UpdateQuery<T> asUpdate() {
    return new DefaultUpdateQuery<>(this);
  }

  @Override
  public BeanDescriptor<T> getBeanDescriptor() {
    return beanDescriptor;
  }


  @Override
  public boolean isFindAll() {
    return whereExpressions == null && nativeSql == null && rawSql == null;
  }

  @Override
  public boolean isFindById() {
    if (id == null && whereExpressions != null) {
      id = whereExpressions.idEqualTo(beanDescriptor.getIdName());
      if (id != null) {
        whereExpressions = null;
      }
    }
    return id != null;
  }

  @Override
  public String profileEventId() {
    switch (mode) {
      case LAZYLOAD_BEAN:
        return FIND_ONE_LAZY;
      case LAZYLOAD_MANY:
        return FIND_MANY_LAZY;
      default:
        return type.profileEventId();
    }
  }

  @Override
  public String getProfileId() {
    return getPlanLabel();
  }

  @Override
  public Query<T> setProfileLocation(ProfileLocation profileLocation) {
    this.profileLocation = profileLocation;
    return this;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public String getPlanLabel() {
    if (label != null) {
      return label;
    }
    if (profileLocation != null) {
      return profileLocation.label();
    }
    return null;
  }

  @Override
  public void setProfilePath(String label, String relativePath, ProfileLocation profileLocation) {
    this.profileLocation = profileLocation;
    this.label = ((profileLocation == null) ? label : profileLocation.label()) + "_" + relativePath;
  }

  @Override
  public Query<T> setLabel(String label) {
    this.label = label;
    return this;
  }

  @Override
  public boolean isAutoTunable() {
    return nativeSql == null && beanDescriptor.isAutoTunable();
  }

  @Override
  public Query<T> setUseDocStore(boolean useDocStore) {
    this.useDocStore = useDocStore;
    return this;
  }

  @Override
  public boolean isUseDocStore() {
    return useDocStore;
  }

  @Override
  public Query<T> apply(FetchPath fetchPath) {
    fetchPath.apply(this);
    return this;
  }

  @Override
  public void addSoftDeletePredicate(String softDeletePredicate) {
    if (softDeletePredicates == null) {
      softDeletePredicates = new ArrayList<>();
    }
    softDeletePredicates.add(softDeletePredicate);
  }

  @Override
  public List<String> getSoftDeletePredicates() {
    return softDeletePredicates;
  }

  @Override
  public boolean isAsOfBaseTable() {
    return asOfBaseTable;
  }

  @Override
  public void setAsOfBaseTable() {
    this.asOfBaseTable = true;
  }

  @Override
  public Query<T> setAllowLoadErrors() {
    this.allowLoadErrors = true;
    return this;
  }

  @Override
  public void incrementAsOfTableCount() {
    asOfTableCount++;
  }

  @Override
  public int getAsOfTableCount() {
    return asOfTableCount;
  }

  @Override
  public Timestamp getAsOf() {
    return asOf;
  }

  @Override
  public Query<T> asOf(Timestamp asOfDateTime) {
    this.temporalMode = (asOfDateTime != null) ? TemporalMode.AS_OF : TemporalMode.CURRENT;
    this.asOf = asOfDateTime;
    return this;
  }

  @Override
  public Query<T> asDraft() {
    this.temporalMode = TemporalMode.DRAFT;
    this.useBeanCache = CacheMode.OFF;
    return this;
  }

  @Override
  public Query<T> setIncludeSoftDeletes() {
    this.temporalMode = TemporalMode.SOFT_DELETED;
    return this;
  }

  @Override
  public Query<T> setDocIndexName(String indexName) {
    this.docIndexName = indexName;
    this.useDocStore = true;
    return this;
  }

  @Override
  public String getDocIndexName() {
    return docIndexName;
  }

  @Override
  public SpiRawSql getRawSql() {
    return rawSql;
  }

  @Override
  public Query<T> setRawSql(RawSql rawSql) {
    this.rawSql = (SpiRawSql) rawSql;
    return this;
  }

  @Override
  public String getOriginKey() {
    if (parentNode == null || parentNode.getOriginQueryPoint() == null) {
      return null;
    } else {
      return parentNode.getOriginQueryPoint().getKey();
    }
  }

  @Override
  public int getLazyLoadBatchSize() {
    return lazyLoadBatchSize;
  }

  @Override
  public Query<T> setLazyLoadBatchSize(int lazyLoadBatchSize) {
    this.lazyLoadBatchSize = lazyLoadBatchSize;
    return this;
  }

  @Override
  public String getLazyLoadProperty() {
    return lazyLoadProperty;
  }

  @Override
  public void setLazyLoadProperty(String lazyLoadProperty) {
    this.lazyLoadProperty = lazyLoadProperty;
  }

  @Override
  public ExpressionFactory getExpressionFactory() {
    return expressionFactory;
  }

  private void createExtraJoinsToSupportManyWhereClause() {
    manyWhereJoins = new ManyWhereJoins();
    if (whereExpressions != null) {
      whereExpressions.containsMany(beanDescriptor, manyWhereJoins);
    }
    if (havingExpressions != null) {
      havingExpressions.containsMany(beanDescriptor, manyWhereJoins);
    }
    if (orderBy != null) {
      for (Property orderProperty : orderBy.getProperties()) {
        ElPropertyDeploy elProp = beanDescriptor.getElPropertyDeploy(orderProperty.getProperty());
        if (elProp != null && elProp.containsFormulaWithJoin()) {
          manyWhereJoins.addFormulaWithJoin(orderProperty.getProperty());
        }
      }
    }
  }

  /**
   * Return the extra joins required to support the where clause for 'Many' properties.
   */
  @Override
  public ManyWhereJoins getManyWhereJoins() {
    return manyWhereJoins;
  }

  /**
   * Return true if select all properties was used to ensure the property invoking a lazy load was
   * included in the query.
   */
  @Override
  public boolean selectAllForLazyLoadProperty() {
    if (lazyLoadProperty != null) {
      if (!detail.containsProperty(lazyLoadProperty)) {
        detail.select("*");
        return true;
      }
    }
    return false;
  }

  private List<OrmQueryProperties> removeQueryJoins() {
    List<OrmQueryProperties> queryJoins = detail.removeSecondaryQueries();
    if (queryJoins != null) {
      if (orderBy != null) {
        // remove any orderBy properties that relate to
        // paths of the secondary queries
        for (OrmQueryProperties joinPath : queryJoins) {
          // loop through the orderBy properties and
          // move any ones related to the query join
          List<Property> properties = orderBy.getProperties();
          Iterator<Property> it = properties.iterator();
          while (it.hasNext()) {
            Property property = it.next();
            if (property.getProperty().startsWith(joinPath.getPath())) {
              // remove this orderBy segment and
              // add it to the secondary join
              it.remove();
              joinPath.addSecJoinOrderProperty(property);
            }
          }
        }
      }
    }
    return queryJoins;
  }

  private List<OrmQueryProperties> removeLazyJoins() {
    return detail.removeSecondaryLazyQueries();
  }

  @Override
  public void setLazyLoadManyPath(String lazyLoadManyPath) {
    this.lazyLoadManyPath = lazyLoadManyPath;
  }

  @Override
  public SpiQuerySecondary convertJoins() {
    if (!useDocStore) {
      createExtraJoinsToSupportManyWhereClause();
    }
    markQueryJoins();
    return new OrmQuerySecondary(removeQueryJoins(), removeLazyJoins());
  }

  /**
   * Limit the number of fetch joins to Many properties, mark as query joins as needed.
   */
  private void markQueryJoins() {
    detail.markQueryJoins(beanDescriptor, lazyLoadManyPath, isAllowOneManyFetch(), type != Type.ATTRIBUTE && type != Type.SUBQUERY);
  }

  private boolean isAllowOneManyFetch() {
    if (Mode.LAZYLOAD_MANY == mode) {
      return false;
    } else {
      return !hasMaxRowsOrFirstRow() || isRawSql();
    }
  }

  @Override
  public void setDefaultSelectClause() {
    if (type != Type.ATTRIBUTE && type != Type.SUBQUERY) {
      detail.setDefaultSelectClause(beanDescriptor);
    } else if (!detail.hasSelectClause()) {
      // explicit empty select when single attribute query on non-root fetch path
      detail.setEmptyBase();
    }
  }

  @Override
  public void setTenantId(Object tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public Object getTenantId() {
    return tenantId;
  }

  @Override
  public void setDetail(OrmQueryDetail detail) {
    this.detail = detail;
  }

  @Override
  public boolean tuneFetchProperties(OrmQueryDetail tunedDetail) {
    return detail.tuneFetchProperties(tunedDetail);
  }

  @Override
  public OrmQueryDetail getDetail() {
    return detail;
  }

  @Override
  public ExpressionList<T> filterMany(String prop) {

    OrmQueryProperties chunk = detail.getChunk(prop, true);
    return chunk.filterMany(this);
  }

  @Override
  public void setFilterMany(String prop, ExpressionList<?> filterMany) {
    if (filterMany != null) {
      OrmQueryProperties chunk = detail.getChunk(prop, true);
      chunk.setFilterMany((SpiExpressionList<?>) filterMany);
    }
  }

  @Override
  public void prepareDocNested() {
    if (textExpressions != null) {
      textExpressions.prepareDocNested(beanDescriptor);
    }
    if (whereExpressions != null) {
      whereExpressions.prepareDocNested(beanDescriptor);
    }
  }

  /**
   * Setup to be a delete or update query.
   */
  @Override
  public void setupForDeleteOrUpdate() {
    forUpdate = null;
    rootTableAlias = "${RTA}"; // alias we remove later
    setSelectId();
  }

  @Override
  public CQueryPlanKey setDeleteByIdsPlan() {
    // re-build plan for cascading via delete by ids
    queryPlanKey = queryPlanKey.withDeleteByIds();
    return queryPlanKey;
  }

  /**
   * Set the select clause to select the Id property.
   */
  @Override
  public void setSelectId() {
    // clear select and fetch joins..
    detail.clear();
    select(beanDescriptor.getIdBinder().getIdProperty());
  }

  @Override
  public void setSingleAttribute() {
    this.singleAttribute = true;
  }

  /**
   * Return true if this is a single attribute query.
   */
  @Override
  public boolean isSingleAttribute() {
    return singleAttribute;
  }

  @Override
  public CountDistinctOrder getCountDistinctOrder() {
    return countDistinctOrder;
  }

  /**
   * Return true if the Id should be included in the query.
   */
  @Override
  public boolean isWithId() {
    return !manualId && !distinct && !singleAttribute;
  }

  @Override
  public CacheIdLookup<T> cacheIdLookup() {
    if (whereExpressions == null) {
      return null;
    }
    List<SpiExpression> underlyingList = whereExpressions.getUnderlyingList();
    if (underlyingList.isEmpty()) {
      if (id != null) {
        return new CacheIdLookupSingle<>(id);
      }
    } else if (underlyingList.size() == 1) {
      SpiExpression singleExpression = underlyingList.get(0);
      if (singleExpression instanceof IdInExpression) {
        return new CacheIdLookupMany<>((IdInExpression) singleExpression);
      }
    }
    return null;
  }

  @Override
  public NaturalKeyQueryData<T> naturalKey() {
    if (whereExpressions == null) {
      return null;
    }
    BeanNaturalKey naturalKey = beanDescriptor.getNaturalKey();
    if (naturalKey == null) {
      return null;
    }

    NaturalKeyQueryData<T> data = new NaturalKeyQueryData<>(naturalKey);
    for (SpiExpression expression : whereExpressions.getUnderlyingList()) {
      // must be eq or in
      if (!expression.naturalKey(data)) {
        return null;
      }
    }
    return data;
  }

  @Override
  public NaturalKeyBindParam getNaturalKeyBindParam() {
    NaturalKeyBindParam namedBind = null;
    if (bindParams != null) {
      namedBind = bindParams.getNaturalKeyBindParam();
      if (namedBind == null) {
        return null;
      }
    }

    if (whereExpressions != null) {
      List<SpiExpression> exprList = whereExpressions.internalList();
      if (exprList.size() > 1) {
        return null;
      } else if (exprList.isEmpty()) {
        return namedBind;
      } else {
        if (namedBind != null) {
          return null;
        }
        SpiExpression se = exprList.get(0);
        if (se instanceof SimpleExpression) {
          SimpleExpression e = (SimpleExpression) se;
          if (e.isOpEquals()) {
            return new NaturalKeyBindParam(e.getPropName(), e.getValue());
          }
        }
      }
    }
    return null;
  }

  @Override
  public SpiQuery<T> copy() {
    return copy(server);
  }

  @Override
  public SpiQuery<T> copy(SpiEbeanServer server) {
    DefaultOrmQuery<T> copy = new DefaultOrmQuery<>(beanDescriptor, server, expressionFactory);
    copy.transaction = transaction;
    copy.m2mIncludeJoin = m2mIncludeJoin;
    copy.profilingListener = profilingListener;
    copy.profileLocation = profileLocation;
    copy.baseTable = baseTable;
    copy.rootTableAlias = rootTableAlias;
    copy.distinct = distinct;
    copy.allowLoadErrors = allowLoadErrors;
    copy.timeout = timeout;
    copy.mapKey = mapKey;
    copy.id = id;
    copy.label = label;
    copy.nativeSql = nativeSql;
    copy.useBeanCache = useBeanCache;
    copy.useQueryCache = useQueryCache;
    copy.readOnly = readOnly;
    if (detail != null) {
      copy.detail = detail.copy();
    }
    copy.temporalMode = temporalMode;
    copy.firstRow = firstRow;
    copy.maxRows = maxRows;
    if (orderBy != null) {
      copy.orderBy = orderBy.copy();
    }
    copy.orderById = orderById;
    if (bindParams != null) {
      copy.bindParams = bindParams.copy();
    }
    if (whereExpressions != null) {
      copy.whereExpressions = whereExpressions.copy(copy);
    }
    if (havingExpressions != null) {
      copy.havingExpressions = havingExpressions.copy(copy);
    }
    copy.persistenceContextScope = persistenceContextScope;
    copy.usageProfiling = usageProfiling;
    copy.autoTune = autoTune;
    copy.parentNode = parentNode;
    copy.forUpdate = forUpdate;
    copy.rawSql = rawSql;
    setCancelableQuery(copy); // required to cancel findId query
    return copy;
  }

  @Override
  public Query<T> setPersistenceContextScope(PersistenceContextScope scope) {
    this.persistenceContextScope = scope;
    return this;
  }

  @Override
  public PersistenceContextScope getPersistenceContextScope() {
    return persistenceContextScope;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public void setType(Type type) {
    this.type = type;
  }

  @Override
  public String getLoadDescription() {
    return loadDescription;
  }

  @Override
  public String getLoadMode() {
    return loadMode;
  }

  @Override
  public void setLoadDescription(String loadMode, String loadDescription) {
    this.loadMode = loadMode;
    this.loadDescription = loadDescription;
  }

  /**
   * Return the TransactionContext.
   * <p>
   * If no TransactionContext is present on the query then the TransactionContext from the
   * Transaction is used (transaction scoped persistence context).
   * </p>
   */
  @Override
  public PersistenceContext getPersistenceContext() {
    return persistenceContext;
  }

  /**
   * Set an explicit TransactionContext (typically for a refresh query).
   * <p>
   * If no TransactionContext is present on the query then the TransactionContext from the
   * Transaction is used (transaction scoped persistence context).
   * </p>
   */
  @Override
  public void setPersistenceContext(PersistenceContext persistenceContext) {
    this.persistenceContext = persistenceContext;
  }

  @Override
  public void setLazyLoadForParents(BeanPropertyAssocMany<?> many) {
    this.lazyLoadForParentsProperty = many;
  }

  @Override
  public BeanPropertyAssocMany<?> getLazyLoadMany() {
    return lazyLoadForParentsProperty;
  }

  /**
   * Return true if the query detail has neither select or joins specified.
   */
  @Override
  public boolean isDetailEmpty() {
    return detail.isEmpty();
  }

  @Override
  public boolean isAutoTuned() {
    return autoTuned;
  }

  @Override
  public void setAutoTuned(boolean autoTuned) {
    this.autoTuned = autoTuned;
  }

  @Override
  public Boolean isAutoTune() {
    return autoTune;
  }

  @Override
  public void setDefaultRawSqlIfRequired() {
    if (beanDescriptor.isRawSqlBased() && rawSql == null) {
      rawSql = beanDescriptor.getNamedRawSql(DEFAULT_QUERY_NAME);
    }
  }

  @Override
  public Query<T> setAutoTune(boolean autoTune) {
    this.autoTune = autoTune;
    return this;
  }

  @Override
  public Query<T> withLock(LockType lockType) {
    return setForUpdateWithMode(LockWait.WAIT, lockType);
  }

  @Override
  public Query<T> withLock(LockType lockType, LockWait lockWait) {
    return setForUpdateWithMode(lockWait, lockType);
  }

  @Override
  public Query<T> forUpdate() {
    return setForUpdateWithMode(LockWait.WAIT, LockType.DEFAULT);
  }

  @Override
  public Query<T> forUpdateNoWait() {
    return setForUpdateWithMode(LockWait.NOWAIT, LockType.DEFAULT);
  }

  @Override
  public Query<T> forUpdateSkipLocked() {
    return setForUpdateWithMode(LockWait.SKIPLOCKED, LockType.DEFAULT);
  }

  private Query<T> setForUpdateWithMode(LockWait mode, LockType lockType) {
    this.forUpdate = mode;
    this.lockType = lockType;
    this.useBeanCache = CacheMode.OFF;
    return this;
  }

  @Override
  public boolean isForUpdate() {
    return forUpdate != null;
  }

  @Override
  public LockWait getForUpdateLockWait() {
    return forUpdate;
  }

  @Override
  public LockType getForUpdateLockType() {
    return lockType;
  }

  @Override
  public ProfilingListener getProfilingListener() {
    return profilingListener;
  }

  @Override
  public void setProfilingListener(ProfilingListener profilingListener) {
    this.profilingListener = profilingListener;
  }

  @Override
  public QueryType getQueryType() {
    if (type != null) {
      switch (type) {
        case DELETE:
          return QueryType.DELETE;
        case UPDATE:
          return QueryType.UPDATE;
      }
    }
    return QueryType.FIND;
  }

  @Override
  public Mode getMode() {
    return mode;
  }

  @Override
  public TemporalMode getTemporalMode() {
    return temporalMode;
  }

  @Override
  public boolean isAsOfQuery() {
    return asOf != null;
  }

  @Override
  public boolean isAsDraft() {
    return TemporalMode.DRAFT == temporalMode;
  }

  @Override
  public boolean isIncludeSoftDeletes() {
    return TemporalMode.SOFT_DELETED == temporalMode;
  }

  @Override
  public void setMode(Mode mode) {
    this.mode = mode;
  }

  @Override
  public boolean isUsageProfiling() {
    return usageProfiling;
  }

  @Override
  public void setUsageProfiling(boolean usageProfiling) {
    this.usageProfiling = usageProfiling;
  }

  @Override
  public void setParentNode(ObjectGraphNode parentNode) {
    this.parentNode = parentNode;
  }

  @Override
  public ObjectGraphNode getParentNode() {
    return parentNode;
  }

  @Override
  public ObjectGraphNode setOrigin(CallOrigin callOrigin) {
    // create a 'origin' which links this query to the profiling information
    ObjectGraphOrigin o = new ObjectGraphOrigin(calculateOriginQueryHash(), callOrigin, beanType.getName());
    parentNode = new ObjectGraphNode(o, null);
    return parentNode;
  }

  /**
   * Calculate a hash for use in determining the ObjectGraphOrigin.
   * <p>
   * This should be quite a stable hash as most uniqueness is determined by the CallStack, so we
   * only use the bean type and overall query type.
   * </p>
   * <p>
   * This stable hash allows the query to be changed (joins added etc) without losing the already
   * collected usage profiling.
   * </p>
   */
  private int calculateOriginQueryHash() {
    int hc = beanType.getName().hashCode();
    hc = hc * 92821 + (type == null ? 0 : type.ordinal());
    return hc;
  }

  /**
   * Calculate the query hash for either AutoTune query tuning or Query Plan caching.
   */
  CQueryPlanKey createQueryPlanKey() {
    if (isNativeSql()) {
      String bindHash = (bindParams == null) ? "" : bindParams.calcQueryPlanHash();
      queryPlanKey = new NativeSqlQueryPlanKey(type.ordinal() + nativeSql + "-" + firstRow + "-" + maxRows + "-" + bindHash);
    } else {
      queryPlanKey = new OrmQueryPlanKey(planDescription(), maxRows, firstRow, rawSql);
    }
    return queryPlanKey;
  }

  private String planDescription() {
    StringBuilder sb = new StringBuilder(300);
    if (type != null) {
      sb.append(type.ordinal());
    }
    if (useDocStore) {
      sb.append("/ds");
    }
    if (beanDescriptor.getDiscValue() != null) {
      sb.append("/dv").append(beanDescriptor.getDiscValue());
    }
    if (temporalMode != SpiQuery.TemporalMode.CURRENT) {
      sb.append("/tm").append(temporalMode.ordinal());
      if (versionsStart != null) {
        sb.append("v");
      }
    }
    if (forUpdate != null) {
      sb.append("/fu").append(forUpdate.ordinal());
      if (lockType != null) {
        sb.append("t").append(lockType.ordinal());
      }
    }
    if (id != null) {
      sb.append("/id");
    }
    if (manualId) {
      sb.append("/md");
    }
    if (distinct) {
      sb.append("/dt");
    }
    if (allowLoadErrors) {
      sb.append("/ae");
    }
    if (disableLazyLoading) {
      sb.append("/dl");
    }
    if (baseTable != null) {
      sb.append("/bt").append(baseTable);
    }
    if (rootTableAlias != null) {
      sb.append("/ra").append(rootTableAlias);
    }
    if (orderBy != null) {
      sb.append("/ob").append(orderBy.toStringFormat());
    }
    if (m2mIncludeJoin != null) {
      sb.append("/m2").append(m2mIncludeJoin.getTable());
    }
    if (mapKey != null) {
      sb.append("/mk").append(mapKey);
    }
    if (countDistinctOrder != null) {
      sb.append("/cd").append(countDistinctOrder.name());
    }
    if (detail != null) {
      sb.append("/d[");
      detail.queryPlanHash(sb);
      sb.append("]");
    }
    if (bindParams != null) {
      sb.append("/b[");
      bindParams.buildQueryPlanHash(sb);
      sb.append("]");
    }
    if (whereExpressions != null) {
      sb.append("/w[");
      whereExpressions.queryPlanHash(sb);
      sb.append("]");
    }
    if (havingExpressions != null) {
      sb.append("/h[");
      havingExpressions.queryPlanHash(sb);
      sb.append("]");
    }
    if (updateProperties != null) {
      sb.append("/u[");
      updateProperties.buildQueryPlanHash(sb);
      sb.append("]");
    }
    return sb.toString();
  }

  @Override
  public boolean isNativeSql() {
    return nativeSql != null;
  }

  @Override
  public String getNativeSql() {
    return nativeSql;
  }

  @Override
  public Object getQueryPlanKey() {
    return queryPlanKey;
  }

  /**
   * Prepare the query which prepares any expressions (sub-query expressions etc) and calculates the query plan key.
   */
  @Override
  public CQueryPlanKey prepare(SpiOrmQueryRequest<T> request) {
    prepareExpressions(request);
    prepareForPaging();
    queryPlanKey = createQueryPlanKey();
    return queryPlanKey;
  }

  /**
   * Prepare the expressions (compile sub-queries etc).
   */
  private void prepareExpressions(BeanQueryRequest<?> request) {
    detail.prepareExpressions(request);
    if (whereExpressions != null) {
      whereExpressions.prepareExpression(request);
    }
    if (havingExpressions != null) {
      havingExpressions.prepareExpression(request);
    }
  }

  /**
   * deemed to be a be a paging query - check that the order by contains the id
   * property to ensure unique row ordering for predicable paging but only in
   * case, this is not a distinct query
   */
  private void prepareForPaging() {
    // add the rawSql statement - if any
    if (orderByIsEmpty()) {
      if (rawSql != null && rawSql.getSql() != null) {
        order(rawSql.getSql().getOrderBy());
      }
    }
    if (checkPagingOrderBy()) {
      beanDescriptor.appendOrderById(this);
    }
  }

  /**
   * Calculate a hash based on the bind values used in the query.
   * <p>
   * Used with queryPlanHash() to get a unique hash for a query.
   * </p>
   */
  @Override
  public int queryBindHash() {
    int hc = (id == null ? 0 : id.hashCode());
    hc = hc * 92821 + (whereExpressions == null ? 0 : whereExpressions.queryBindHash());
    hc = hc * 92821 + (havingExpressions == null ? 0 : havingExpressions.queryBindHash());
    hc = hc * 92821 + (bindParams == null ? 0 : bindParams.queryBindHash());
    hc = hc * 92821 + (asOf == null ? 0 : asOf.hashCode());
    hc = hc * 92821 + (versionsStart == null ? 0 : versionsStart.hashCode());
    hc = hc * 92821 + (versionsEnd == null ? 0 : versionsEnd.hashCode());
    return hc;
  }

  /**
   * Return a hash that includes the query plan and bind values.
   * <p>
   * This hash can be used to identify if we have executed the exact same query (including bind
   * values) before.
   * </p>
   */
  @Override
  public HashQuery queryHash() {
    // calculateQueryPlanHash is called just after potential AutoTune tuning
    // so queryPlanHash is calculated well before this method is called
    int hc = queryBindHash();
    return new HashQuery(queryPlanKey, hc);
  }

  @Override
  public boolean isRawSql() {
    return rawSql != null;
  }

  /**
   * Return the timeout.
   */
  @Override
  public int getTimeout() {
    return timeout;
  }

  @Override
  public boolean hasMaxRowsOrFirstRow() {
    return maxRows > 0 || firstRow > 0;
  }

  @Override
  public boolean isVersionsBetween() {
    return versionsStart != null;
  }

  @Override
  public Timestamp getVersionStart() {
    return versionsStart;
  }

  @Override
  public Timestamp getVersionEnd() {
    return versionsEnd;
  }

  @Override
  public Boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public Query<T> setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
    return this;
  }

  @Override
  public boolean isBeanCachePut() {
    return useBeanCache.isPut() && beanDescriptor.isBeanCaching();
  }

  @Override
  public boolean isBeanCacheGet() {
    return useBeanCache.isGet() && beanDescriptor.isBeanCaching();
  }

  @Override
  public boolean isForceHitDatabase() {
    return forUpdate != null || CacheMode.PUT == useBeanCache;
  }

  @Override
  public void resetBeanCacheAutoMode(boolean findOne) {
    if (useBeanCache == CacheMode.AUTO) {
      if (!findOne || useQueryCache != CacheMode.OFF) {
        useBeanCache = CacheMode.OFF;
      }
    }
  }

  @Override
  public CacheMode getUseBeanCache() {
    return useBeanCache;
  }

  @Override
  public CacheMode getUseQueryCache() {
    return useQueryCache;
  }

  @Override
  public Query<T> setBeanCacheMode(CacheMode beanCacheMode) {
    this.useBeanCache = beanCacheMode;
    return this;
  }

  @Override
  public Query<T> setUseQueryCache(CacheMode useQueryCache) {
    this.useQueryCache = useQueryCache;
    return this;
  }

  @Override
  public Query<T> setLoadBeanCache(boolean loadBeanCache) {
    this.useBeanCache = CacheMode.PUT;
    return this;
  }

  @Override
  public Query<T> setTimeout(int secs) {
    this.timeout = secs;
    return this;
  }

  @Override
  public void selectProperties(Set<String> props) {
    detail.selectProperties(props);
  }

  @Override
  public void fetchProperties(String property, Set<String> columns, FetchConfig config) {
    detail.fetchProperties(property, columns, config);
  }

  @Override
  public void selectProperties(OrmQueryProperties properties) {
    detail.selectProperties(properties);
  }

  @Override
  public void fetchProperties(String path, OrmQueryProperties other) {
    detail.fetchProperties(path, other);
  }

  @Override
  public void addNested(String name, OrmQueryDetail nestedDetail, FetchConfig config) {
    detail.addNested(name, nestedDetail, config);
  }

  @Override
  public Query<T> select(String columns) {
    detail.select(columns);
    return this;
  }

  @Override
  public Query<T> select(FetchGroup<T> fetchGroup) {
    this.detail = ((SpiFetchGroup<T>) fetchGroup).detail();
    return this;
  }

  @Override
  public Query<T> fetch(String path) {
    return fetch(path, null, null);
  }

  @Override
  public Query<T> fetch(String path, FetchConfig joinConfig) {
    return fetch(path, null, joinConfig);
  }

  @Override
  public Query<T> fetch(String path, String properties) {
    return fetch(path, properties, null);
  }

  @Override
  public Query<T> fetch(String path, String properties, FetchConfig config) {
    if (nativeSql != null && (config == null || config.isJoin())) {
      // can't use fetch join with nativeSql (as the root query)
      config = FETCH_QUERY;
    }
    return fetchInternal(path, properties, config);
  }

  @Override
  public Query<T> fetchQuery(String path) {
    return fetchInternal(path, null, FETCH_QUERY);
  }

  public Query<T> fetchCache(String path) {
    return fetchInternal(path, null, FETCH_CACHE);
  }

  @Override
  public Query<T> fetchLazy(String path) {
    return fetchInternal(path, null, FETCH_LAZY);
  }

  @Override
  public Query<T> fetchQuery(String path, String properties) {
    return fetchInternal(path, properties, FETCH_QUERY);
  }

  @Override
  public Query<T> fetchCache(String path, String properties) {
    return fetchInternal(path, properties, FETCH_CACHE);
  }

  @Override
  public Query<T> fetchLazy(String path, String properties) {
    return fetchInternal(path, properties, FETCH_LAZY);
  }

  private Query<T> fetchInternal(String path, String properties, FetchConfig config) {
    detail.fetch(path, properties, config);
    return this;
  }

  @Override
  public Query<T> usingTransaction(Transaction transaction) {
    this.transaction = (SpiTransaction) transaction;
    return this;
  }

  @Override
  public Query<T> usingConnection(Connection connection) {
    this.transaction = new ExternalJdbcTransaction(connection);
    return this;
  }

  @Override
  public Query<T> usingDatabase(Database database) {
    this.server = (SpiEbeanServer) database;
    return this;
  }

  @Override
  public int delete() {
    return server.delete(this, transaction);
  }

  @Override
  public int delete(Transaction transaction) {
    return server.delete(this, transaction);
  }

  @Override
  public int update() {
    return server.update(this, transaction);
  }

  @Override
  public int update(Transaction transaction) {
    return server.update(this, transaction);
  }

  @Override
  public <A> List<A> findIds() {
    // a copy of this query is made in the server
    // as the query needs to modified (so we modify
    // the copy rather than this query instance)
    return server.findIds(this, transaction);
  }

  @Override
  public boolean exists() {
    return server.exists(this, transaction);
  }

  @Override
  public int findCount() {
    // a copy of this query is made in the server
    // as the query needs to modified (so we modify
    // the copy rather than this query instance)
    return server.findCount(this, transaction);
  }

  @Override
  public void findEachWhile(Predicate<T> consumer) {
    server.findEachWhile(this, consumer, transaction);
  }

  @Override
  public void findEach(Consumer<T> consumer) {
    server.findEach(this, consumer, transaction);
  }

  @Override
  public void findEach(int batch, Consumer<List<T>> consumer) {
    server.findEach(this, batch, consumer, transaction);
  }

  @Override
  public QueryIterator<T> findIterate() {
    return server.findIterate(this, transaction);
  }

  @Override
  public Stream<T> findStream() {
    return server.findStream(this, transaction);
  }

  @Override
  public Stream<T> findLargeStream() {
    return server.findLargeStream(this, transaction);
  }

  @Override
  public List<Version<T>> findVersions() {
    this.temporalMode = TemporalMode.VERSIONS;
    return server.findVersions(this, transaction);
  }

  @Override
  public List<Version<T>> findVersionsBetween(Timestamp start, Timestamp end) {
    if (start == null || end == null) {
      throw new IllegalArgumentException("start and end must not be null");
    }
    this.temporalMode = TemporalMode.VERSIONS;
    this.versionsStart = start;
    this.versionsEnd = end;
    return server.findVersions(this, transaction);
  }

  @Override
  public List<T> findList() {
    return server.findList(this, transaction);
  }

  @Override
  public Set<T> findSet() {
    return server.findSet(this, transaction);
  }

  @Override
  public <K> Map<K, T> findMap() {
    return server.findMap(this, transaction);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <A> List<A> findSingleAttributeList() {
    return (List<A>) server.findSingleAttributeList(this, transaction);
  }

  @Override
  public <A> A findSingleAttribute() {
    List<A> list = findSingleAttributeList();
    return !list.isEmpty() ? list.get(0) : null;
  }

  @Override
  public T findOne() {
    return server.findOne(this, transaction);
  }

  @Override
  public Optional<T> findOneOrEmpty() {
    return server.findOneOrEmpty(this, transaction);
  }

  @Override
  public FutureIds<T> findFutureIds() {
    return server.findFutureIds(this, transaction);
  }

  @Override
  public FutureList<T> findFutureList() {
    return server.findFutureList(this, transaction);
  }

  @Override
  public FutureRowCount<T> findFutureCount() {
    return server.findFutureCount(this, transaction);
  }

  @Override
  public PagedList<T> findPagedList() {
    return server.findPagedList(this, transaction);
  }

  @Override
  public Query<T> setParameter(Object value) {
    if (bindParams == null) {
      bindParams = new BindParams();
    }
    bindParams.setNextParameter(value);
    return this;
  }

  @Override
  public Query<T> setParameters(Object... values) {
    if (bindParams == null) {
      bindParams = new BindParams();
    }
    bindParams.setNextParameters(values);
    return this;
  }

  /**
   * Set an ordered bind parameter according to its position. Note that the position starts at 1 to
   * be consistent with JDBC PreparedStatement. You need to set a parameter value for each ? you
   * have in the query.
   */
  @Override
  public Query<T> setParameter(int position, Object value) {
    if (bindParams == null) {
      bindParams = new BindParams();
    }
    bindParams.setParameter(position, value);
    return this;
  }

  /**
   * Set a named bind parameter. Named parameters have a colon to prefix the name.
   */
  @Override
  public Query<T> setParameter(String name, Object value) {
    if (namedParams != null) {
      ONamedParam param = namedParams.get(name);
      if (param != null) {
        param.setValue(value);
        return this;
      }
    }
    if (bindParams == null) {
      bindParams = new BindParams();
    }
    bindParams.setParameter(name, value);
    return this;
  }

  @Override
  public boolean checkPagingOrderBy() {
    return orderById && !useDocStore;
  }

  @Override
  public boolean orderByIsEmpty() {
    return orderBy == null || orderBy.isEmpty();
  }

  @Override
  public OrderBy<T> getOrderBy() {
    return orderBy;
  }

  @Override
  @Deprecated
  public OrderBy<T> orderBy() {
    return order();
  }

  @Override
  public OrderBy<T> order() {
    if (orderBy == null) {
      orderBy = new OrderBy<>(this, null);
    }
    return orderBy;
  }

  @Override
  @Deprecated
  public Query<T> orderBy(String orderByClause) {
    return order(orderByClause);
  }

  @Override
  public Query<T> order(String orderByClause) {
    if (orderByClause == null || orderByClause.trim().isEmpty()) {
      this.orderBy = null;
    } else {
      this.orderBy = new OrderBy<>(this, orderByClause);
    }
    return this;
  }

  @Override
  @Deprecated
  public Query<T> setOrderBy(OrderBy<T> orderBy) {
    return setOrder(orderBy);
  }

  @Override
  public Query<T> setOrder(OrderBy<T> orderBy) {
    this.orderBy = orderBy;
    if (orderBy != null) {
      orderBy.setQuery(this);
    }
    return this;
  }

  @Override
  public boolean isManualId() {
    return manualId;
  }

  @Override
  public void setManualId() {
    if (detail != null && detail.hasSelectClause()) {
      this.manualId = true;
    }
  }

  /**
   * return true if user specified to use SQL DISTINCT (effectively excludes id property).
   */
  @Override
  public boolean isDistinct() {
    return distinct;
  }

  /**
   * Internally set to use SQL DISTINCT on the query but still have id property included.
   */
  @Override
  public Query<T> setDistinct(boolean distinct) {
    this.distinct = distinct;
    return this;
  }

  @Override
  public Query<T> setCountDistinct(CountDistinctOrder countDistinctOrder) {
    this.countDistinctOrder = countDistinctOrder;
    return this;
  }

  @Override
  public boolean isCountDistinct() {
    return countDistinctOrder != null;
  }

  @Override
  public Class<T> getBeanType() {
    return beanType;
  }

  @Override
  public Class<? extends T> getInheritType() {
    return beanDescriptor.getBeanType();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Query<T> setInheritType(Class<? extends T> type) {
    if (type == beanType) {
      return this;
    }
    InheritInfo inheritInfo = rootBeanDescriptor.getInheritInfo();
    inheritInfo = inheritInfo == null ? null : inheritInfo.readType(type);
    if (inheritInfo == null) {
      throw new IllegalArgumentException("Given type " + type + " is not a subtype of " + beanType);
    }
    beanDescriptor = (BeanDescriptor<T>) rootBeanDescriptor.getBeanDescriptor(type);
    return this;
  }

  @Override
  public String toString() {
    return "Query [" + whereExpressions + "]";
  }

  @Override
  public TableJoin getM2mIncludeJoin() {
    return m2mIncludeJoin;
  }

  @Override
  public void setM2MIncludeJoin(TableJoin m2mIncludeJoin) {
    this.m2mIncludeJoin = m2mIncludeJoin;
  }

  @Override
  public Query<T> setDisableLazyLoading(boolean disableLazyLoading) {
    this.disableLazyLoading = disableLazyLoading;
    return this;
  }

  @Override
  public boolean isDisableLazyLoading() {
    return disableLazyLoading;
  }

  @Override
  public int getFirstRow() {
    return firstRow;
  }

  @Override
  public Query<T> setFirstRow(int firstRow) {
    this.firstRow = firstRow;
    return this;
  }

  @Override
  public int getMaxRows() {
    return maxRows;
  }

  @Override
  public Query<T> setMaxRows(int maxRows) {
    this.maxRows = maxRows;
    return this;
  }

  @Override
  public String getMapKey() {
    return mapKey;
  }

  @Override
  public Query<T> setMapKey(String mapKey) {
    this.mapKey = mapKey;
    return this;
  }

  @Override
  public Object getId() {
    return id;
  }

  @Override
  public Query<T> setId(Object id) {
    if (id == null) {
      throw new NullPointerException("The id is null");
    }
    this.id = id;
    return this;
  }

  @Override
  public BindParams getBindParams() {
    return bindParams;
  }

  @Override
  public Query<T> where(Expression expression) {
    where().add(expression);
    return this;
  }

  @Override
  public ExpressionList<T> text() {
    if (textExpressions == null) {
      useDocStore = true;
      textExpressions = new DefaultExpressionList<>(this);
    }
    return textExpressions;
  }

  @Override
  public ExpressionList<T> where() {
    if (whereExpressions == null) {
      whereExpressions = new DefaultExpressionList<>(this, null);
    }
    return whereExpressions;
  }

  @Override
  public void simplifyExpressions() {
    if (whereExpressions != null) {
      whereExpressions.simplify();
    }
  }

  @Override
  public Query<T> having(Expression expression) {
    having().add(expression);
    return this;
  }

  @Override
  public ExpressionList<T> having() {
    if (havingExpressions == null) {
      havingExpressions = new DefaultExpressionList<>(this, null);
    }
    return havingExpressions;
  }

  @Override
  public SpiExpressionList<T> getHavingExpressions() {
    return havingExpressions;
  }

  @Override
  public SpiExpressionList<T> getWhereExpressions() {
    return whereExpressions;
  }

  @Override
  public SpiExpressionList<T> getTextExpression() {
    return textExpressions;
  }

  @Override
  public String getGeneratedSql() {
    return generatedSql;
  }

  @Override
  public void setGeneratedSql(String generatedSql) {
    this.generatedSql = generatedSql;
  }

  @Override
  public void checkNamedParameters() {
    if (namedParams != null) {
      for (ONamedParam value : namedParams.values()) {
        value.checkValueSet();
      }
    }
  }

  @Override
  public SpiNamedParam createNamedParameter(String name) {
    if (namedParams == null) {
      namedParams = new HashMap<>();
    }
    return namedParams.computeIfAbsent(name, ONamedParam::new);
  }

  @Override
  public void setDefaultFetchBuffer(int fetchSize) {
    if (bufferFetchSizeHint == 0) {
      bufferFetchSizeHint = fetchSize;
    }
  }

  @Override
  public Query<T> setBufferFetchSizeHint(int bufferFetchSizeHint) {
    this.bufferFetchSizeHint = bufferFetchSizeHint;
    return this;
  }

  @Override
  public int getBufferFetchSizeHint() {
    return bufferFetchSizeHint;
  }

  @Override
  public Query<T> setDisableReadAuditing() {
    this.disableReadAudit = true;
    return this;
  }

  @Override
  public boolean isDisableReadAudit() {
    return disableReadAudit;
  }

  @Override
  public boolean isFutureFetch() {
    return futureFetch;
  }

  @Override
  public void setFutureFetch(boolean backgroundFetch) {
    this.futureFetch = backgroundFetch;
  }

  @Override
  public void setFutureFetchAudit(ReadEvent event) {
    this.futureFetchAudit = event;
  }

  @Override
  public ReadEvent getFutureFetchAudit() {
    return futureFetchAudit;
  }

  @Override
  public Query<T> setBaseTable(String baseTable) {
    this.baseTable = baseTable;
    return this;
  }

  @Override
  public String getBaseTable() {
    return baseTable;
  }

  @Override
  public Query<T> alias(String alias) {
    this.rootTableAlias = alias;
    return this;
  }

  @Override
  public String getAlias() {
    return rootTableAlias;
  }

  @Override
  public String getAlias(String defaultAlias) {
    return rootTableAlias != null ? rootTableAlias : defaultAlias;
  }


  @Override
  public Set<String> validate() {
    return server.validateQuery(this);
  }

  /**
   * Validate all the expression properties/paths given the bean descriptor.
   */
  @Override
  public Set<String> validate(BeanType<T> desc) {
    SpiExpressionValidation validation = new SpiExpressionValidation(desc);
    if (whereExpressions != null) {
      whereExpressions.validate(validation);
    }
    if (havingExpressions != null) {
      havingExpressions.validate(validation);
    }
    if (orderBy != null) {
      for (Property property : orderBy.getProperties()) {
        validation.validate(property.getProperty());
      }
    }
    return validation.getUnknownProperties();
  }

  void setUpdateProperties(OrmUpdateProperties updateProperties) {
    this.updateProperties = updateProperties;
  }

  @Override
  public OrmUpdateProperties getUpdateProperties() {
    return updateProperties;
  }

  @Override
  public ProfileLocation getProfileLocation() {
    return profileLocation;
  }

  @Override
  public void handleLoadError(String fullName, Exception e) {
    if (!allowLoadErrors) {
      throw new PersistenceException("Error loading on " + fullName, e);
    }
  }

  @Override
  public Query<T> orderById(boolean orderById) {
    this.orderById = orderById;
    return this;
  }

}
