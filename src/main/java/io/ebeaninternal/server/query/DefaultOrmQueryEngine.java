package io.ebeaninternal.server.query;

import io.ebean.QueryIterator;
import io.ebean.Version;
import io.ebean.bean.BeanCollection;
import io.ebean.bean.EntityBean;
import io.ebean.event.BeanFindController;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.SpiTransaction;
import io.ebeaninternal.server.core.OrmQueryEngine;
import io.ebeaninternal.server.core.OrmQueryRequest;
import io.ebeaninternal.server.deploy.BeanDescriptor;

import javax.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Main Finder implementation.
 */
public class DefaultOrmQueryEngine implements OrmQueryEngine {

  /**
   * Find using predicates
   */
  private final CQueryEngine queryEngine;

  /**
   * Create the Finder.
   */
  public DefaultOrmQueryEngine(CQueryEngine queryEngine) {
    this.queryEngine = queryEngine;
  }

  @Override
  public <T> PersistenceException translate(OrmQueryRequest<T> request, String bindLog, String sql, SQLException e) {
    return queryEngine.translate(request, bindLog, sql, e);
  }

  /**
   * Flushes the jdbc batch by default unless explicitly turned off on the transaction.
   */
  private <T> void flushJdbcBatchOnQuery(OrmQueryRequest<T> request) {

    SpiTransaction t = request.getTransaction();
    if (t.isBatchFlushOnQuery()) {
      // before we perform a query, we need to flush any
      // previous persist requests that are queued/batched.
      // The query may read data affected by those requests.
      t.flushBatch();
    }
  }

  @Override
  public <T> int delete(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);
    return queryEngine.delete(request);
  }

  @Override
  public <T> int update(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);
    return queryEngine.update(request);
  }

  @Override
  public <T> int findCount(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);
    int result = queryEngine.findCount(request);
    if (request.getQuery().getUseQueryCache().isPut()) {
      request.putToQueryCache(result);
    }
    return result;
  }

  @Override
  public <A> List<A> findIds(OrmQueryRequest<?> request) {

    flushJdbcBatchOnQuery(request);
    List<A> result = queryEngine.findIds(request);
    if (request.getQuery().getUseQueryCache().isPut()) {
      result = Collections.unmodifiableList(result);
      request.putToQueryCache(result);
      if (Boolean.FALSE.equals(request.getQuery().isReadOnly())) {
        result = new ArrayList<>(result);
      }
    }
    return result;
  }

  @Override
  public <A> List<A> findSingleAttributeList(OrmQueryRequest<?> request) {
    flushJdbcBatchOnQuery(request);
    List<A> result = queryEngine.findSingleAttributeList(request);
    if (!result.isEmpty() && request.getQuery().getUseQueryCache().isPut()) {
      // load the query result into the query cache
      result = Collections.unmodifiableList(result);
      request.putToQueryCache(result);
      if (Boolean.FALSE.equals(request.getQuery().isReadOnly())) {
        result = new ArrayList<>(result);
      }
    }
    return result;
  }

  @Override
  public <T> QueryIterator<T> findIterate(OrmQueryRequest<T> request) {

    // LIMITATION: You can not use QueryIterator to load bean cache

    flushJdbcBatchOnQuery(request);
    return queryEngine.findIterate(request);
  }

  @Override
  public <T> List<Version<T>> findVersions(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);
    return queryEngine.findVersions(request);
  }

  @Override
  public <T> BeanCollection<T> findMany(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);

    BeanFindController finder = request.getBeanFinder();

    BeanCollection<T> result;
    if (finder != null && finder.isInterceptFindMany(request)) {
      // intercept this request
      result = finder.findMany(request);
    } else {
      result = queryEngine.findMany(request);
    }

    SpiQuery<T> query = request.getQuery();

    if (query.isLoadBeanCache()) {
      // load the individual beans into the bean cache
      BeanDescriptor<T> descriptor = request.getBeanDescriptor();
      Collection<T> c = result.getActualDetails();
      for (T bean : c) {
        descriptor.cacheBeanPut((EntityBean) bean);
      }
    }

    if (!result.isEmpty() && query.getUseQueryCache().isPut()) {
      // load the query result into the query cache
      result.setReadOnly(true);
      request.putToQueryCache(result);
      if (Boolean.FALSE.equals(query.isReadOnly())) {
        result = result.getShallowCopy();
      }
    }

    return result;
  }

  /**
   * Find a single bean using its unique id.
   */
  @Override
  public <T> T findId(OrmQueryRequest<T> request) {

    flushJdbcBatchOnQuery(request);

    BeanFindController finder = request.getBeanFinder();

    T result;
    if (finder != null && finder.isInterceptFind(request)) {
      result = finder.find(request);
    } else {
      result = queryEngine.find(request);
    }

    if (result != null && request.isUseBeanCache()) {
      request.getBeanDescriptor().cacheBeanPut((EntityBean) result);
    }

    return result;
  }

}
