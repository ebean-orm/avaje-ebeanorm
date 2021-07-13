package io.ebeaninternal.server.query;

import io.ebean.util.SplitName;
import io.ebeaninternal.api.ManyWhereJoins;
import io.ebeaninternal.api.PropertyJoin;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.SpiQuery.Type;
import io.ebeaninternal.server.core.OrmQueryRequest;
import io.ebeaninternal.server.deploy.InheritInfo;
import io.ebeaninternal.server.deploy.TableJoin;
import io.ebeaninternal.server.querydefn.OrmQueryDetail;
import io.ebeaninternal.server.querydefn.OrmQueryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory for SqlTree.
 */
public final class SqlTreeBuilder {

  private static final Logger logger = LoggerFactory.getLogger(SqlTreeBuilder.class);

  private final SpiQuery<?> query;

  private final STreeType desc;

  private final OrmQueryDetail queryDetail;

  private final CQueryPredicates predicates;

  private final boolean subQuery;

  private final boolean distinctOnPlatform;
  /**
   * Property if resultSet contains master and detail rows.
   */
  private STreePropertyAssocMany manyProperty;

  private final SqlTreeAlias alias;

  private final DefaultDbSqlContext ctx;

  private final HashSet<String> selectIncludes = new HashSet<>();

  private final ManyWhereJoins manyWhereJoins;

  private final TableJoin includeJoin;

  private final boolean rawSql;

  /**
   * rawNoId true if the RawSql does not include the @Id property
   */
  private final boolean rawNoId;

  private final boolean disableLazyLoad;

  private final SpiQuery.TemporalMode temporalMode;

  private SqlTreeNode rootNode;

  private boolean sqlDistinct;

  /**
   * Construct for RawSql query.
   */
  SqlTreeBuilder(OrmQueryRequest<?> request, CQueryPredicates predicates, OrmQueryDetail queryDetail, boolean rawNoId) {
    this.rawSql = true;
    this.desc = request.getBeanDescriptor();
    this.rawNoId = rawNoId;
    this.disableLazyLoad = request.getQuery().isDisableLazyLoading();
    this.query = null;
    this.subQuery = false;
    this.distinctOnPlatform = false;
    this.queryDetail = queryDetail;
    this.predicates = predicates;
    this.temporalMode = SpiQuery.TemporalMode.CURRENT;
    this.includeJoin = null;
    this.manyWhereJoins = null;
    this.alias = null;
    this.ctx = null;
  }

  /**
   * The predicates are used to determine if 'extra' joins are required to
   * support the where and/or order by clause. If so these extra joins are added
   * to the root node.
   */
  SqlTreeBuilder(String columnAliasPrefix, CQueryBuilder builder, OrmQueryRequest<?> request, CQueryPredicates predicates) {
    this.rawSql = false;
    this.rawNoId = false;
    this.desc = request.getBeanDescriptor();
    this.query = request.getQuery();
    this.temporalMode = SpiQuery.TemporalMode.of(query);
    this.disableLazyLoad = query.isDisableLazyLoading();
    this.subQuery = Type.SUBQUERY == query.getType() || Type.ID_LIST == query.getType() || Type.DELETE == query.getType() || query.isCountDistinct();
    this.includeJoin = query.getM2mIncludeJoin();
    this.manyWhereJoins = query.getManyWhereJoins();
    this.queryDetail = query.getDetail();
    this.predicates = predicates;
    this.alias = new SqlTreeAlias(request.getBaseTableAlias(), temporalMode);
    this.distinctOnPlatform = builder.isPlatformDistinctOn();
    String fromForUpdate = builder.fromForUpdate(query);
    CQueryHistorySupport historySupport = builder.getHistorySupport(query);
    CQueryDraftSupport draftSupport = builder.getDraftSupport(query);
    String colAlias = subQuery ? null : columnAliasPrefix;
    this.ctx = new DefaultDbSqlContext(alias, colAlias, historySupport, draftSupport, fromForUpdate);
  }

  /**
   * Build based on the includes and using the BeanJoinTree.
   */
  public SqlTree build() {
    // build the appropriate chain of SelectAdapter's
    buildRoot(desc);
    // build the actual String
    String distinctOn = null;
    String selectSql = null;
    String fromSql = null;
    String inheritanceWhereSql = null;
    String groupBy = null;
    STreeProperty[] encryptedProps = null;
    if (!rawSql) {
      selectSql = buildSelectClause();
      fromSql = buildFromClause();
      inheritanceWhereSql = buildWhereClause();
      groupBy = buildGroupByClause();
      distinctOn = buildDistinctOn();
      encryptedProps = ctx.getEncryptedProps();
    }

    boolean includeJoins = alias != null && alias.isIncludeJoins();
    return new SqlTree(rootNode, distinctOn, selectSql, fromSql, groupBy, inheritanceWhereSql, encryptedProps, manyProperty, includeJoins);
  }

  private String buildSelectClause() {
    if (rawSql) {
      return "Not Used";
    }
    rootNode.appendSelect(ctx, subQuery);
    return trimComma(ctx.getContent());
  }

  private String buildGroupByClause() {
    if (rawSql || (!rootNode.isAggregation() && query.getHavingExpressions() == null)) {
      return null;
    }
    ctx.startGroupBy();
    rootNode.appendGroupBy(ctx, subQuery);
    return trimComma(ctx.getContent());
  }

  private String buildDistinctOn() {
    if (rawSql || !distinctOnPlatform || !sqlDistinct || Type.COUNT == query.getType()) {
      return null;
    }
    ctx.startGroupBy();
    rootNode.appendDistinctOn(ctx, subQuery);
    String idCols = trimComma(ctx.getContent());
    return idCols == null ? null : mergeOnDistinct(idCols, predicates.getDbOrderBy());
  }

  static String mergeOnDistinct(String idCols, String dbOrderBy) {
    if (dbOrderBy == null) {
      return idCols;
    }
    dbOrderBy = DbOrderByTrim.trim(dbOrderBy);
    StringBuilder sb = new StringBuilder(dbOrderBy.length() + idCols.length() + 2);
    sb.append(dbOrderBy);
    String[] split = idCols.split(",");
    for (String col : split) {
      col = col.trim();
      if (!dbOrderBy.contains(col)) {
        sb.append(", ").append(col);
      }
    }
    return sb.toString();
  }

  /**
   * Trim the first comma.
   */
  private String trimComma(String groupBy) {
    if (groupBy.length() < SqlTreeNode.COMMA.length()) {
      return null;
    } else {
      return groupBy.substring(SqlTreeNode.COMMA.length());
    }
  }

  private String buildWhereClause() {
    if (rawSql) {
      return "Not Used";
    }
    rootNode.appendWhere(ctx);
    return ctx.getContent();
  }

  private String buildFromClause() {
    if (rawSql) {
      return "Not Used";
    }
    rootNode.appendFrom(ctx, SqlJoinType.AUTO);
    return ctx.getContent();
  }

  private void buildRoot(STreeType desc) {
    rootNode = buildSelectChain(null, null, desc, null);
    if (!rawSql) {
      alias.addJoin(queryDetail.getFetchPaths(), desc);
      alias.addJoin(predicates.getPredicateIncludes(), desc);
      alias.addManyWhereJoins(manyWhereJoins.getPropertyNames());
      // build set of table alias
      alias.buildAlias();
      predicates.parseTableAlias(alias);
    }
  }

  /**
   * Recursively build the query tree depending on what leaves in the tree
   * should be included.
   */
  private SqlTreeNode buildSelectChain(String prefix, STreePropertyAssoc prop,
                                       STreeType desc, List<SqlTreeNode> joinList) {
    List<SqlTreeNode> myJoinList = new ArrayList<>();
    List<STreePropertyAssocOne> extraProps = new ArrayList<>();
    for (STreePropertyAssocOne one : desc.propsOne()) {
      String propPrefix = SplitName.add(prefix, one.getName());
      if (isIncludeBean(propPrefix)) {
        selectIncludes.add(propPrefix);
        if (!one.hasForeignKey()) {
          extraProps.add(one);
        }
        buildSelectChain(propPrefix, one, one.target(), myJoinList);
      }
    }

    for (STreePropertyAssocMany many : desc.propsMany()) {
      String propPrefix = SplitName.add(prefix, many.getName());
      if (isIncludeMany(propPrefix, many)) {
        selectIncludes.add(propPrefix);
        buildSelectChain(propPrefix, many, many.target(), myJoinList);
      }
    }

    OrmQueryProperties queryProps = queryDetail.getChunk(prefix, false);
    SqlTreeProperties props = getBaseSelect(desc, queryProps);

    if (prefix == null && !rawSql) {
      if (props.requireSqlDistinct(manyWhereJoins)) {
        sqlDistinct = true;
      }
      addManyWhereJoins(myJoinList);
    }
    extraProps.forEach(props::add);

    SqlTreeNode selectNode = buildNode(prefix, prop, desc, myJoinList, props);
    if (joinList != null) {
      joinList.add(selectNode);
    }
    return selectNode;
  }

  /**
   * Add joins used to support where clause predicates on 'many' properties.
   * <p>
   * These joins are effectively independent of any fetch joins on 'many'
   * properties.
   * </p>
   */
  private void addManyWhereJoins(List<SqlTreeNode> myJoinList) {
    Collection<PropertyJoin> includes = manyWhereJoins.getPropertyJoins();
    for (PropertyJoin joinProp : includes) {
      STreePropertyAssoc beanProperty = (STreePropertyAssoc) desc.findPropertyFromPath(joinProp.getProperty());
      SqlTreeNodeManyWhereJoin nodeJoin = new SqlTreeNodeManyWhereJoin(joinProp.getProperty(), beanProperty, joinProp.getSqlJoinType(), temporalMode);
      myJoinList.add(nodeJoin);
    }
    if (manyWhereJoins.isFormulaWithJoin()) {
      for (String property : manyWhereJoins.getFormulaJoinProperties()) {
        STreeProperty beanProperty = desc.findPropertyFromPath(property);
        myJoinList.add(new SqlTreeNodeFormulaWhereJoin(beanProperty, SqlJoinType.OUTER));
      }
    }
  }

  private SqlTreeNode buildNode(String prefix, STreePropertyAssoc prop, STreeType desc, List<SqlTreeNode> myList, SqlTreeProperties props) {
    if (prefix == null) {
      buildExtraJoins(desc, myList);

      // Optional many property for lazy loading query
      STreePropertyAssocMany lazyLoadMany = (query == null) ? null : query.getLazyLoadMany();
      boolean withId = !rawNoId && !subQuery && (query == null || query.isWithId());

      String baseTable = (query == null) ? null : query.getBaseTable();
      if (baseTable == null) {
        baseTable = desc.getBaseTable(temporalMode);
      }
      return new SqlTreeNodeRoot(desc, props, myList, withId, includeJoin, lazyLoadMany, temporalMode, disableLazyLoad, sqlDistinct, baseTable);

    } else if (prop instanceof STreePropertyAssocMany) {
      return new SqlTreeNodeManyRoot(prefix, (STreePropertyAssocMany) prop, props, myList, temporalMode, disableLazyLoad);

    } else {
      // do not read Id on child beans (e.g. when used with fetch())
      boolean withId = isNotSingleAttribute() && !subQuery;
      return new SqlTreeNodeBean(prefix, prop, props, myList, withId, temporalMode, disableLazyLoad);
    }
  }

  /**
   * Build extra joins to support properties used in where clause but not
   * already in select clause.
   */
  private void buildExtraJoins(STreeType desc, List<SqlTreeNode> myList) {
    if (rawSql) {
      return;
    }
    Set<String> predicateIncludes = predicates.getPredicateIncludes();
    if (predicateIncludes == null) {
      return;
    }

    // Note includes - basically means joins.
    // The selectIncludes is the set of joins that are required to support
    // the 'select' part of the query. We may need to add other joins to
    // support the predicates or order by clauses.

    // remove ManyWhereJoins from the predicateIncludes
    predicateIncludes.removeAll(manyWhereJoins.getPropertyNames());
    predicateIncludes.addAll(predicates.getOrderByIncludes());

    // look for predicateIncludes that are not in selectIncludes and add
    // them as extra joins to the query
    IncludesDistiller extraJoinDistill = new IncludesDistiller(desc, selectIncludes, predicateIncludes, temporalMode);
    Collection<SqlTreeNodeExtraJoin> extraJoins = extraJoinDistill.getExtraJoinRootNodes();
    if (!extraJoins.isEmpty()) {
      // add extra joins required to support predicates
      // and/or order by clause
      for (SqlTreeNodeExtraJoin extraJoin : extraJoins) {
        myList.add(extraJoin);
        if (extraJoin.isManyJoin()) {
          // as we are now going to join to the many then we need
          // to add the distinct to the sql query to stop duplicate
          // rows...
          sqlDistinct = true;
        }
      }
    }
  }

  /**
   * A subQuery has slightly different rules in that it just generates SQL (into
   * the where clause) and its properties are not required to read the resultSet
   * etc.
   * <p>
   * This means it can included individual properties of an embedded bean.
   * </p>
   */
  private void addPropertyToSubQuery(SqlTreeProperties selectProps, STreeType desc, String propName) {
    STreeProperty p = desc.findProperty(propName);
    if (p == null) {
      logger.error("property [" + propName + "]not found on " + desc + " for query - excluding it.");

    } else if (p instanceof STreePropertyAssoc && p.isEmbedded()) {
      // if the property is embedded we need to lookup the real column name
      int pos = propName.indexOf('.');
      if (pos > -1) {
        String name = propName.substring(pos + 1);
        p = ((STreePropertyAssoc) p).target().findProperty(name);
      }
    }
    selectProps.add(p);
  }

  private void addProperty(SqlTreeProperties selectProps, STreeType desc, OrmQueryProperties queryProps, String propName) {
    if (subQuery) {
      addPropertyToSubQuery(selectProps, desc, propName);
      return;
    }

    int basePos = propName.indexOf('.');
    if (basePos > -1) {
      // property on an embedded bean. Embedded beans do not yet
      // support being partially populated so we include the
      // 'base' property and make sure we only do that once
      String baseName = propName.substring(0, basePos);

      // make sure we only included the base/embedded bean once
      if (!selectProps.containsProperty(baseName)) {
        STreeProperty p = desc.findPropertyWithDynamic(baseName, null);
        if (p == null) {
          // maybe dynamic formula with schema prefix
          p = desc.findPropertyWithDynamic(propName, null);
          if (p != null) {
            selectProps.add(p);
          } else {
            logger.error("property [" + propName + "] not found on " + desc + " for query - excluding it.");
          }
        } else if (p.isEmbedded() || (p instanceof STreePropertyAssoc && !queryProps.isIncludedBeanJoin(p.getName()))) {
          // add the embedded bean or the *ToOne assoc bean.  We skip the check that the *ToOne propName maps to Id property ...
          selectProps.add(p);
        } else {
          logger.error("property [" + p.getFullBeanName() + "] expected to be an embedded or *ToOne bean for query - excluding it.");
        }
      }

    } else {
      // find the property including searching the
      // sub class hierarchy if required
      STreeProperty p = desc.findPropertyWithDynamic(propName, queryProps.getPath());
      if (p == null) {
        logger.error("property [" + propName + "] not found on " + desc + " for query - excluding it.");
        p = desc.findProperty("id");
        selectProps.add(p);

      } else if (p.isId() && excludeIdProperty()) {
        // do not bother to include id for normal queries as the
        // id is always added (except for subQueries)

      } else if (p instanceof STreePropertyAssoc) {
        // need to check if this property should be
        // excluded. This occurs when this property is
        // included as a bean join. With a bean join
        // the property should be excluded as the bean
        // join has its own node in the SqlTree.
        if (!queryProps.isIncludedBeanJoin(p.getName())) {
          // include the property... which basically
          // means include the foreign key column(s)
          selectProps.add(p);
        }
      } else {
        selectProps.add(p);
      }
    }
  }

  private SqlTreeProperties getBaseSelectPartial(STreeType desc, OrmQueryProperties queryProps) {
    SqlTreeProperties selectProps = new SqlTreeProperties();
    // add properties in the order in which they appear
    // in the query. Gives predictable sql/properties for
    // use with SqlSelect type queries.

    // Also note that this can include transient properties.
    // This makes sense for transient properties used to
    // hold sum() count() type values (with SqlSelect)
    final Set<String> selectInclude = queryProps.getIncluded();
    for (String propName : selectInclude) {
      if (!propName.isEmpty()) {
        addProperty(selectProps, desc, queryProps, propName);
      }
    }

    if (!selectProps.isAggregationManyToOne()) {
      final Set<String> selectQueryJoin = queryProps.getSelectQueryJoin();
      if (selectQueryJoin != null) {
        for (String joinProperty : selectQueryJoin) {
          if (!selectInclude.contains(joinProperty)) {
            addProperty(selectProps, desc, queryProps, joinProperty);
          }
        }
      }
    }

    return selectProps;
  }

  private SqlTreeProperties getBaseSelect(STreeType desc, OrmQueryProperties queryProps) {
    boolean partial = queryProps != null && !queryProps.allProperties();
    if (partial) {
      return getBaseSelectPartial(desc, queryProps);
    }

    SqlTreeProperties selectProps = new SqlTreeProperties();
    selectProps.setAllProperties();

    // normal simple properties of the bean
    selectProps.add(desc.propsBaseScalar());
    selectProps.add(desc.propsEmbedded());

    for (STreePropertyAssocOne propertyAssocOne : desc.propsOne()) {
      //noinspection StatementWithEmptyBody
      if (queryProps != null
        && queryProps.isIncludedBeanJoin(propertyAssocOne.getName())
        && propertyAssocOne.hasForeignKey()
        && !propertyAssocOne.isFormula()) {
        // if it is a joined bean with FK constraint... then don't add the property
        // as it will have its own entire Node in the SqlTree
      } else {
        selectProps.add(propertyAssocOne);
      }
    }

    InheritInfo inheritInfo = desc.getInheritInfo();
    if (inheritInfo != null) {
      // add sub type properties
      inheritInfo.addChildrenProperties(selectProps);

    }
    return selectProps;
  }

  /**
   * Return true if this many node should be included in the query.
   */
  private boolean isIncludeMany(String propName, STreePropertyAssocMany manyProp) {
    if (queryDetail.isJoinsEmpty()) {
      return false;
    }
    if (queryDetail.includesPath(propName)) {
      if (manyProperty != null) {
        // only one many associated allowed to be included in fetch
        if (logger.isDebugEnabled()) {
          logger.debug("Not joining [" + propName + "] as already joined to a Many[" + manyProperty + "].");
        }
        return false;
      }
      manyProperty = manyProp;
      return true;
    }
    return false;
  }

  /**
   * Test to see if we are including this node into the query.
   * <p>
   * Return true if this node is FULLY included resulting in table join. If the
   * node is not included but its parent has been included then a "bean proxy"
   * is added and false is returned.
   * </p>
   */
  private boolean isIncludeBean(String prefix) {
    if (queryDetail.includesPath(prefix)) {
      // explicitly included
      String[] splitNames = SplitName.split(prefix);
      queryDetail.includeBeanJoin(splitNames[0], splitNames[1]);
      return true;
    }

    return false;
  }

  /**
   * Takes the select includes and the predicates includes and determines the
   * extra joins required to support the predicates (that are not already
   * supported by the select includes).
   * <p>
   * This returns ONLY the leaves. The joins for the leaves
   * </p>
   */
  private static class IncludesDistiller {

    private final STreeType desc;
    private final Set<String> selectIncludes;
    private final Set<String> predicateIncludes;
    private final SpiQuery.TemporalMode temporalMode;

    private final Map<String, SqlTreeNodeExtraJoin> joinRegister = new HashMap<>();
    private final Map<String, SqlTreeNodeExtraJoin> rootRegister = new HashMap<>();

    private IncludesDistiller(STreeType desc, Set<String> selectIncludes,
                              Set<String> predicateIncludes, SpiQuery.TemporalMode temporalMode) {
      this.desc = desc;
      this.selectIncludes = selectIncludes;
      this.predicateIncludes = predicateIncludes;
      this.temporalMode = temporalMode;
    }

    /**
     * Build the collection of extra joins returning just the roots.
     * <p>
     * each root returned here could contain a little tree of joins. This
     * follows the more natural pattern and allows for forcing outer joins from
     * a join to a 'many' down through the rest of its tree.
     * </p>
     */
    private Collection<SqlTreeNodeExtraJoin> getExtraJoinRootNodes() {
      String[] extras = findExtras();
      if (extras.length == 0) {
        return rootRegister.values();
      }
      // sort so we process only getting the leaves
      // excluding nodes between root and the leaf
      Arrays.sort(extras);
      // reverse order so get the leaves first...
      for (String extra : extras) {
        createExtraJoin(extra);
      }
      return rootRegister.values();
    }

    private void createExtraJoin(String includeProp) {
      SqlTreeNodeExtraJoin extraJoin = createJoinLeaf(includeProp);
      if (extraJoin != null) {
        // add the extra join...

        // find root of this extra join... linking back to the
        // parents (creating the tree) as it goes.
        SqlTreeNodeExtraJoin root = findExtraJoinRoot(includeProp, extraJoin);
        // register the root because these are the only ones we
        // return back.
        rootRegister.put(root.getName(), root);
      }
    }

    /**
     * Create a SqlTreeNodeExtraJoin, register and return it.
     */
    private SqlTreeNodeExtraJoin createJoinLeaf(String propertyName) {
      ExtraJoin extra = desc.extraJoin(propertyName);
      if (extra == null) {
        return null;
      } else {
        SqlTreeNodeExtraJoin extraJoin = new SqlTreeNodeExtraJoin(propertyName, extra.getProperty(), extra.isContainsMany(), temporalMode);
        joinRegister.put(propertyName, extraJoin);
        return extraJoin;
      }
    }

    /**
     * Find the root the this extra join tree.
     * <p>
     * This may need to create a parent join implicitly if a predicate join
     * 'skips' a level. e.g. where details.user.id = 1 (maybe join to details is
     * not specified and is implicitly created.
     * </p>
     */
    private SqlTreeNodeExtraJoin findExtraJoinRoot(String includeProp, SqlTreeNodeExtraJoin childJoin) {
      while (true) {
        int dotPos = includeProp.lastIndexOf('.');
        if (dotPos == -1) {
          // no parent possible(parent is root)
          return childJoin;

        } else {
          // look in register ...
          String parentPropertyName = includeProp.substring(0, dotPos);
          if (selectIncludes.contains(parentPropertyName)) {
            // parent already handled by select
            return childJoin;
          }

          SqlTreeNodeExtraJoin parentJoin = joinRegister.get(parentPropertyName);
          if (parentJoin == null) {
            // we need to create this the parent implicitly...
            parentJoin = createJoinLeaf(parentPropertyName);
          }

          parentJoin.addChild(childJoin);
          childJoin = parentJoin;
          includeProp = parentPropertyName;
        }
      }
    }

    /**
     * Find the extra joins required by predicates and not already taken care of
     * by the select.
     */
    private String[] findExtras() {
      List<String> extras = new ArrayList<>();
      for (String predProp : predicateIncludes) {
        if (!selectIncludes.contains(predProp)) {
          extras.add(predProp);
        }
      }
      return extras.toArray(new String[0]);
    }

  }

  /**
   * Return true if the Id property should be excluded (as it is automatically included).
   */
  private boolean excludeIdProperty() {
    return query == null || !query.isSingleAttribute() && !query.isManualId();
  }

  /**
   * Return true if the query is not a single attribute query.
   */
  private boolean isNotSingleAttribute() {
    return query == null || !query.isSingleAttribute();
  }
}
