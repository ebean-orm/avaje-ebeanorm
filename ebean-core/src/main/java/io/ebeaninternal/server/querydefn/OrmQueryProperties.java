package io.ebeaninternal.server.querydefn;

import io.ebean.ExpressionFactory;
import io.ebean.FetchConfig;
import io.ebean.OrderBy;
import io.ebean.Query;
import io.ebean.util.SplitName;
import io.ebeaninternal.api.SpiExpression;
import io.ebeaninternal.api.SpiExpressionFactory;
import io.ebeaninternal.api.SpiExpressionList;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.server.expression.FilterExprPath;
import io.ebeaninternal.server.expression.FilterExpressionList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the Properties of an Object Relational query.
 */
public class OrmQueryProperties implements Serializable {

  private static final long serialVersionUID = -8785582703966455658L;

  static final FetchConfig DEFAULT_FETCH = FetchConfig.ofDefault();

  private final String parentPath;
  private final String path;
  private final String properties;
  private final Set<String> included;
  private final FetchConfig fetchConfig;

  /**
   * Flag set when this fetch path needs to be a query join.
   */
  private boolean markForQueryJoin;

  private boolean cache;

  private boolean readOnly;

  /**
   * Included bean joins.
   */
  private Set<String> includedBeanJoin;

  /**
   * Add these properties to the select so that the foreign key columns are included in the query.
   */
  private Set<String> secondaryQueryJoins;

  private List<OrmQueryProperties> secondaryChildren;

  /**
   * OrderBy properties that where on the main query but moved here as they relate to this (query join).
   */
  @SuppressWarnings("rawtypes")
  private OrderBy orderBy;

  /**
   * A filter that can be applied to the fetch of this path in the object graph.
   */
  @SuppressWarnings("rawtypes")
  private SpiExpressionList filterMany;

  /**
   * Construct for root so path (and parentPath) are null.
   */
  public OrmQueryProperties() {
    this(null);
  }

  /**
   * Construct with a given path.
   */
  public OrmQueryProperties(String path) {
    this.path = path;
    this.parentPath = SplitName.parent(path);
    this.properties = null;
    this.included = null;
    this.fetchConfig = DEFAULT_FETCH;
  }

  public OrmQueryProperties(String path, String rawProperties) {
    this(path, rawProperties, null);
  }

  public OrmQueryProperties(String path, String rawProperties, FetchConfig fetchConfig) {
    this.path = path;
    this.parentPath = SplitName.parent(path);

    OrmQueryPropertiesParser.Response response = OrmQueryPropertiesParser.parse(rawProperties);
    this.properties = response.properties;
    this.included = response.included;
    this.cache = response.cache;
    this.readOnly = response.readOnly;
    if (fetchConfig != null) {
      this.fetchConfig = fetchConfig;
      if (fetchConfig.isCache()) {
        this.cache = true;
      }
    } else {
      this.fetchConfig = response.fetchConfig;
    }
  }

  public OrmQueryProperties(String path, Set<String> included) {
    this.path = path;
    this.parentPath = SplitName.parent(path);
    // for rawSql parsedProperties can be empty (when only fetching Id property)
    this.included = included;
    this.properties = String.join(",", included);
    this.cache = false;
    this.readOnly = false;
    this.fetchConfig = DEFAULT_FETCH;
  }

  /**
   * Copy constructor.
   */
  private OrmQueryProperties(OrmQueryProperties source, FetchConfig sourceFetchConfig) {
    this.fetchConfig = sourceFetchConfig;
    this.parentPath = source.parentPath;
    this.path = source.path;
    this.properties = source.properties;
    this.cache = source.cache;
    this.readOnly = source.readOnly;
    this.filterMany = source.filterMany;
    this.markForQueryJoin = source.markForQueryJoin;
    this.included = (source.included == null) ? null : new LinkedHashSet<>(source.included);
  }

  /**
   * Creates a copy of the OrmQueryProperties.
   */
  public OrmQueryProperties copy() {
    return new OrmQueryProperties(this, this.fetchConfig);
  }

  /**
   * Create a copy with the given fetch config.
   */
  public OrmQueryProperties copy(FetchConfig fetchConfig) {
    return new OrmQueryProperties(this, fetchConfig);
  }

  /**
   * Move a OrderBy.Property from the main query to this query join.
   */
  void addSecJoinOrderProperty(OrderBy.Property orderProp) {
    if (orderBy == null) {
      orderBy = new OrderBy();
    }
    orderBy.add(orderProp);
  }

  public FetchConfig getFetchConfig() {
    return fetchConfig;
  }

  /**
   * Return the expressions used to filter on this path. This should be a many path to use this
   * method.
   */
  @SuppressWarnings({"unchecked"})
  public <T> SpiExpressionList<T> filterMany(Query<T> rootQuery) {
    if (filterMany == null) {
      FilterExprPath exprPath = new FilterExprPath(path);
      SpiExpressionFactory queryEf = (SpiExpressionFactory) rootQuery.getExpressionFactory();
      ExpressionFactory filterEf = queryEf.createExpressionFactory();// exprPath);
      filterMany = new FilterExpressionList(exprPath, filterEf, rootQuery);
      // by default we need to make this a 'query join' now
      markForQueryJoin = true;
    }
    return filterMany;
  }

  /**
   * Return the filterMany expression list (can be null).
   */
  private SpiExpressionList<?> getFilterManyTrimPath(int trimPath) {
    if (filterMany == null) {
      return null;
    }
    return filterMany.trimPath(trimPath);
  }

  /**
   * Return the filterMany expression list (can be null).
   */
  public SpiExpressionList<?> getFilterMany() {
    return filterMany;
  }

  /**
   * Set the filterMany expression list.
   */
  public void setFilterMany(SpiExpressionList<?> filterMany) {
    this.filterMany = filterMany;
    this.markForQueryJoin = true;
  }

  /**
   * Define the select and joins for this query.
   */
  @SuppressWarnings("unchecked")
  public void configureBeanQuery(SpiQuery<?> query) {

    if (properties != null && !properties.isEmpty()) {
      query.select(properties);
    }

    if (filterMany != null) {
      filterMany.applyRowLimits(query);
      SpiExpressionList<?> trimPath = filterMany.trimPath(path.length() + 1);
      for (SpiExpression spiExpression : trimPath.getUnderlyingList()) {
        query.where().add(spiExpression);
      }
    }

    if (secondaryChildren != null) {
      int trimPath = path.length() + 1;
      for (OrmQueryProperties p : secondaryChildren) {
        String path = p.getPath();
        path = path.substring(trimPath);
        query.fetch(path, p.getProperties(), p.getFetchConfig());
        query.setFilterMany(path, p.getFilterManyTrimPath(trimPath));
      }
    }

    if (orderBy != null) {
      query.setOrder(orderBy.copyWithTrim(path));
    }
  }

  public boolean hasSelectClause() {
    if ("*".equals(properties)) {
      // explicitly selected all properties
      return true;
    }
    // explicitly selected some properties
    return included != null || filterMany != null;
  }

  /**
   * Return true if the properties and configuration are empty.
   */
  public boolean isEmpty() {
    return properties == null || properties.isEmpty();
  }

  public void asStringDebug(String prefix, StringBuilder sb) {
    sb.append(prefix);
    if (path != null) {
      sb.append(path).append(" ");
    }
    if (!isEmpty()) {
      sb.append("(").append(properties).append(")");
    }
  }

  boolean isChild(OrmQueryProperties possibleChild) {
    return possibleChild.getPath().startsWith(path + ".");
  }

  /**
   * For secondary queries add a child element.
   */
  public void add(OrmQueryProperties child) {
    if (secondaryChildren == null) {
      secondaryChildren = new ArrayList<>();
    }
    secondaryChildren.add(child);
  }

  /**
   * Return the raw properties.
   */
  public String getProperties() {
    return properties;
  }

  /**
   * Return true if this includes all properties on the path.
   */
  public boolean allProperties() {
    return included == null;
  }

  /**
   * Return true if this property is included as a bean join.
   * <p>
   * If a property is included as a bean join then it should not be included as a reference/proxy to
   * avoid duplication.
   * </p>
   */
  public boolean isIncludedBeanJoin(String propertyName) {
    return includedBeanJoin != null && includedBeanJoin.contains(propertyName);
  }

  /**
   * Add a bean join property.
   */
  void includeBeanJoin(String propertyName) {
    if (includedBeanJoin == null) {
      includedBeanJoin = new HashSet<>();
    }
    includedBeanJoin.add(propertyName);
  }

  public Set<String> getSelectQueryJoin() {
    return secondaryQueryJoins;
  }

  void addSecondaryQueryJoin(String property) {
    if (secondaryQueryJoins == null) {
      secondaryQueryJoins = new HashSet<>(4);
    }
    secondaryQueryJoins.add(property);
  }

  /**
   * Return the property set.
   */
  public Set<String> getIncluded() {
    return included;
  }

  boolean isIncluded(String propName) {
    if (includedBeanJoin != null && includedBeanJoin.contains(propName)) {
      return false;
    }
    // all properties included
    return included == null || included.contains(propName);
  }

  /**
   * Mark this path as needing to be a query join.
   */
  void markForQueryJoin() {
    markForQueryJoin = true;
  }

  /**
   * Return true if this path is a 'query join'.
   */
  public boolean isQueryFetch() {
    return markForQueryJoin || cache || fetchConfig.isQuery();
  }

  /**
   * Return true if this path is a 'fetch join'.
   */
  boolean isFetchJoin() {
    return !markForQueryJoin && fetchConfig.isJoin();
  }

  /**
   * Return true if this path is a lazy fetch.
   */
  boolean isLazyFetch() {
    return fetchConfig.isLazy();
  }

  public int getBatchSize() {
    return fetchConfig.getBatchSize();
  }

  /**
   * Return true if this path has the +readonly option.
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Return true if this path has the +cache option to hit the cache.
   */
  public boolean isCache() {
    return cache;
  }

  /**
   * Return the parent path.
   */
  String getParentPath() {
    return parentPath;
  }

  /**
   * Return the path relative to the root of the graph.
   */
  public String getPath() {
    return path;
  }

  /**
   * Return true if the properties are the same for autoTune purposes.
   */
  boolean isSameByAutoTune(OrmQueryProperties p2) {
    if (included == null) {
      return p2 == null || p2.included == null;
    } else if (p2 == null) {
      return false;
    }
    return included.equals(p2.included);
  }

  /**
   * Calculate the query plan hash.
   */
  public void queryPlanHash(StringBuilder builder) {
    builder.append("{");
    if (path != null) {
      builder.append(path);
    }
    if (included != null){
      builder.append("/i").append(included);
    }
    if (secondaryQueryJoins != null) {
      builder.append("/s").append(secondaryQueryJoins);
    }
    if (filterMany != null) {
      builder.append("/f");
      filterMany.queryPlanHash(builder);
    }
    if (fetchConfig != null) {
      builder.append("/c").append(fetchConfig.hashCode());
    }
    builder.append("}");
  }

}
