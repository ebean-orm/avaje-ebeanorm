package io.ebeaninternal.api;

import io.ebean.EbeanServer;
import io.ebean.PersistenceContextScope;
import io.ebean.Query;
import io.ebean.Transaction;
import io.ebean.TxScope;
import io.ebean.bean.BeanCollectionLoader;
import io.ebean.bean.BeanLoader;
import io.ebean.bean.CallStack;
import io.ebean.bean.ObjectGraphNode;
import io.ebean.config.ServerConfig;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.databind.DataTimeZone;
import io.ebean.event.readaudit.ReadAuditLogger;
import io.ebean.event.readaudit.ReadAuditPrepare;
import io.ebeaninternal.server.deploy.BeanDescriptor;
import io.ebeaninternal.server.query.CQuery;
import io.ebeaninternal.server.transaction.RemoteTransactionEvent;

import java.util.List;

/**
 * Service Provider extension to EbeanServer.
 */
public interface SpiEbeanServer extends EbeanServer, BeanLoader, BeanCollectionLoader {

  /**
   * Return the server extended Json context.
   */
  SpiJsonContext jsonExtended();

  /**
   * For internal use, shutdown of the server invoked by JVM Shutdown.
   */
  void shutdownManaged();

  /**
   * Return true if query origins should be collected.
   */
  boolean isCollectQueryOrigins();

  /**
   * Return true if updates in JDBC batch should include all columns if unspecified on the transaction.
   */
  boolean isUpdateAllPropertiesInBatch();

  /**
   * Return the current Tenant Id.
   */
  Object currentTenantId();

  /**
   * Return the server configuration.
   */
  ServerConfig getServerConfig();

  /**
   * Return the DatabasePlatform for this server.
   */
  DatabasePlatform getDatabasePlatform();

  /**
   * Create an object to represent the current CallStack.
   * <p>
   * Typically used to identify the origin of queries for AutoTune and object
   * graph costing.
   * </p>
   */
  CallStack createCallStack();

  /**
   * Return the PersistenceContextScope to use defined at query or server level.
   */
  PersistenceContextScope getPersistenceContextScope(SpiQuery<?> query);

  /**
   * Clear the query execution statistics.
   */
  void clearQueryStatistics();

  /**
   * Return all the descriptors.
   */
  List<BeanDescriptor<?>> getBeanDescriptors();

  /**
   * Return the BeanDescriptor for a given type of bean.
   */
  <T> BeanDescriptor<T> getBeanDescriptor(Class<T> type);

  /**
   * Return BeanDescriptor using it's unique id.
   */
  BeanDescriptor<?> getBeanDescriptorById(String className);

  /**
   * Return BeanDescriptor using it's unique doc store queueId.
   */
  BeanDescriptor<?> getBeanDescriptorByQueueId(String queueId);

  /**
   * Return BeanDescriptors mapped to this table.
   */
  List<BeanDescriptor<?>> getBeanDescriptors(String tableName);

  /**
   * Process committed changes from another framework.
   * <p>
   * This notifies this instance of the framework that beans have been committed
   * externally to it. Either by another framework or clustered server. It uses
   * this to maintain its cache and text indexes appropriately.
   * </p>
   */
  void externalModification(TransactionEventTable event);

  /**
   * Create a ServerTransaction.
   * <p>
   * To specify to use the default transaction isolation use a value of -1.
   * </p>
   */
  SpiTransaction createServerTransaction(boolean isExplicit, int isolationLevel);

  /**
   * Return the current transaction or null if there is no current transaction.
   */
  SpiTransaction getCurrentServerTransaction();

  /**
   * Create a ScopeTrans for a method for the given scope definition.
   */
  ScopeTrans createScopeTrans(TxScope txScope);

  /**
   * Create a ServerTransaction for query purposes.
   *
   * @param tenantId For multi-tenant lazy loading provide the tenantId to use.
   */
  SpiTransaction createQueryTransaction(Object tenantId);

  /**
   * An event from another server in the cluster used to notify local
   * BeanListeners of remote inserts updates and deletes.
   */
  void remoteTransactionEvent(RemoteTransactionEvent event);

  /**
   * Compile a query.
   */
  <T> CQuery<T> compileQuery(Query<T> query, Transaction t);

  /**
   * Execute the findId's query but without copying the query.
   * <p>
   * Used so that the list of Id's can be made accessible to client code before
   * the query has finished (if executing in a background thread).
   * </p>
   */
  <A, T> List<A> findIdsWithCopy(Query<T> query, Transaction t);

  /**
   * Execute the findCount query but without copying the query.
   */
  <T> int findCountWithCopy(Query<T> query, Transaction t);

  /**
   * Load a batch of Associated One Beans.
   */
  void loadBean(LoadBeanRequest loadRequest);

  /**
   * Lazy load a batch of Many's.
   */
  void loadMany(LoadManyRequest loadRequest);

  /**
   * Return the default batch size for lazy loading.
   */
  int getLazyLoadBatchSize();

  /**
   * Return true if the type is known as an Entity or Xml type or a List Set or
   * Map of known bean types.
   */
  boolean isSupportedType(java.lang.reflect.Type genericType);

  /**
   * Collect query statistics by ObjectGraphNode. Used for Lazy loading reporting.
   */
  void collectQueryStats(ObjectGraphNode objectGraphNode, long loadedBeanCount, long timeMicros);

  /**
   * Return the ReadAuditLogger to use for logging all read audit events.
   */
  ReadAuditLogger getReadAuditLogger();

  /**
   * Return the ReadAuditPrepare used to populate the read audit events with
   * user context information (user id, user ip address etc).
   */
  ReadAuditPrepare getReadAuditPrepare();

  /**
   * Return the DataTimeZone to use when reading/writing timestamps via JDBC.
   */
  DataTimeZone getDataTimeZone();

  /**
   * Check for slow query event.
   */
  void slowQueryCheck(long executionTimeMicros, int rowCount, SpiQuery<?> query);
}
