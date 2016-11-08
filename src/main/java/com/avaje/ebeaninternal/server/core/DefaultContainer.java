package com.avaje.ebeaninternal.server.core;

import com.avaje.ebean.BackgroundExecutor;
import com.avaje.ebean.cache.ServerCacheFactory;
import com.avaje.ebean.cache.ServerCacheManager;
import com.avaje.ebean.cache.ServerCacheOptions;
import com.avaje.ebean.cache.ServerCachePlugin;
import com.avaje.ebean.common.SpiContainer;
import com.avaje.ebean.config.ContainerConfig;
import com.avaje.ebean.config.PropertyMap;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.UnderscoreNamingConvention;
import com.avaje.ebean.config.dbplatform.DatabasePlatform;
import com.avaje.ebean.dbmigration.DbOffline;
import com.avaje.ebeaninternal.api.SpiBackgroundExecutor;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.cache.DefaultServerCacheManager;
import com.avaje.ebeaninternal.server.cache.DefaultServerCachePlugin;
import com.avaje.ebeaninternal.server.cluster.ClusterManager;
import com.avaje.ebeaninternal.server.core.bootup.BootupClassPathSearch;
import com.avaje.ebeaninternal.server.core.bootup.BootupClasses;
import com.avaje.ebeaninternal.server.lib.ShutdownManager;
import org.avaje.datasource.DataSourceAlertFactory;
import org.avaje.datasource.DataSourceConfig;
import org.avaje.datasource.DataSourceFactory;
import org.avaje.datasource.DataSourcePoolListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.PersistenceException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * Default Server side implementation of ServerFactory.
 */
public class DefaultContainer implements SpiContainer {

  private static final Logger logger = LoggerFactory.getLogger("com.avaje.ebean.internal.DefaultContainer");

  private final ClusterManager clusterManager;

  private final JndiDataSourceLookup jndiDataSourceFactory;

  public DefaultContainer(ContainerConfig containerConfig) {

    this.clusterManager = new ClusterManager(containerConfig);
    this.jndiDataSourceFactory = new JndiDataSourceLookup();

    // register so that we can shutdown any Ebean wide
    // resources such as clustering
    ShutdownManager.registerContainer(this);
  }

  public void shutdown() {
    clusterManager.shutdown();
  }

  /**
   * Create the server reading configuration information from ebean.properties.
   */
  public SpiEbeanServer createServer(String name) {

    ServerConfig config = new ServerConfig();
    config.setName(name);

    Properties prop = PropertyMap.defaultProperties();
    config.loadFromProperties(prop);

    return createServer(config);
  }

  private SpiBackgroundExecutor createBackgroundExecutor(ServerConfig serverConfig) {

    String namePrefix = "ebean-" + serverConfig.getName();
    int schedulePoolSize = serverConfig.getBackgroundExecutorSchedulePoolSize();
    int shutdownSecs = serverConfig.getBackgroundExecutorShutdownSecs();

    return new DefaultBackgroundExecutor(schedulePoolSize, shutdownSecs, namePrefix);
  }

  /**
   * Create the implementation from the configuration.
   */
  public SpiEbeanServer createServer(ServerConfig serverConfig) {

    synchronized (this) {
      setNamingConvention(serverConfig);

      BootupClasses bootupClasses = getBootupClasses(serverConfig);

      setDataSource(serverConfig);
      // check the autoCommit and Transaction Isolation
      boolean online = checkDataSource(serverConfig);

      // determine database platform (Oracle etc)
      setDatabasePlatform(serverConfig);
      if (serverConfig.getDbEncrypt() != null) {
        // use a configured DbEncrypt rather than the platform default
        serverConfig.getDatabasePlatform().setDbEncrypt(serverConfig.getDbEncrypt());
      }

      // inform the NamingConvention of the associated DatabasePlatform
      serverConfig.getNamingConvention().setDatabasePlatform(serverConfig.getDatabasePlatform());

      // executor and l2 caching service setup early (used during server construction)
      SpiBackgroundExecutor executor = createBackgroundExecutor(serverConfig);
      ServerCacheManager cacheManager = getCacheManager(online, serverConfig, executor);

      InternalConfiguration c = new InternalConfiguration(clusterManager, cacheManager, executor, serverConfig, bootupClasses);

      DefaultServer server = new DefaultServer(c, cacheManager);

      // generate and run DDL if required
      // if there are any other tasks requiring action in their plugins, do them as well
      if (!DbOffline.isGenerateMigration()) {
        server.executePlugins(online);

        // initialise prior to registering with clusterManager
        server.initialise();
        if (online) {
          if (clusterManager.isClustering()) {
            // register the server once it has been created
            clusterManager.registerServer(server);
          }
        }
        // start any services after registering with clusterManager
        server.start();
      }
      DbOffline.reset();
      return server;
    }
  }

  /**
   * Create and return the CacheManager.
   */
  private ServerCacheManager getCacheManager(boolean online, ServerConfig serverConfig, BackgroundExecutor executor) {

    if (!online || serverConfig.isDisableL2Cache()) {
      // use local only L2 cache implementation as placeholder
      return new DefaultServerCacheManager();
    }

    ServerCacheManager serverCacheManager = serverConfig.getServerCacheManager();
    if (serverCacheManager != null) {
      return serverCacheManager;
    }

    // reasonable default settings are for a cache per bean type
    ServerCacheOptions beanOptions = new ServerCacheOptions();
    beanOptions.setMaxSize(serverConfig.getCacheMaxSize());
    beanOptions.setMaxIdleSecs(serverConfig.getCacheMaxIdleTime());
    beanOptions.setMaxSecsToLive(serverConfig.getCacheMaxTimeToLive());

    // reasonable default settings for the query cache per bean type
    ServerCacheOptions queryOptions = new ServerCacheOptions();
    queryOptions.setMaxSize(serverConfig.getQueryCacheMaxSize());
    queryOptions.setMaxIdleSecs(serverConfig.getQueryCacheMaxIdleTime());
    queryOptions.setMaxSecsToLive(serverConfig.getQueryCacheMaxTimeToLive());

    boolean localL2Caching = false;
    ServerCachePlugin plugin = serverConfig.getServerCachePlugin();
    if (plugin == null) {
      ServiceLoader<ServerCachePlugin> cacheFactories = ServiceLoader.load(ServerCachePlugin.class);
      Iterator<ServerCachePlugin> iterator = cacheFactories.iterator();
      if (iterator.hasNext()) {
        // use the cacheFactory (via classpath service loader)
        plugin = iterator.next();
        logger.debug("using ServerCacheFactory {}", plugin.getClass());
      } else {
        // use the built in default l2 caching which is local cache based
        localL2Caching = true;
        plugin = new DefaultServerCachePlugin();
      }
    }

    ServerCacheFactory factory = plugin.create(serverConfig, executor);
    return new DefaultServerCacheManager(localL2Caching, factory, beanOptions, queryOptions);
  }

  /**
   * Get the entities, scalarTypes, Listeners etc combining the class registered
   * ones with the already created instances.
   */
  private BootupClasses getBootupClasses(ServerConfig serverConfig) {

    BootupClasses bootup = getBootupClasses1(serverConfig);
    bootup.addIdGenerators(serverConfig.getIdGenerators());
    bootup.addPersistControllers(serverConfig.getPersistControllers());
    bootup.addPostLoaders(serverConfig.getPostLoaders());
    bootup.addPostConstructListeners(serverConfig.getPostConstructListeners());
    bootup.addFindControllers(serverConfig.getFindControllers());
    bootup.addPersistListeners(serverConfig.getPersistListeners());
    bootup.addQueryAdapters(serverConfig.getQueryAdapters());
    bootup.addServerConfigStartup(serverConfig.getServerConfigStartupListeners());
    bootup.addChangeLogInstances(serverConfig);

    // run any ServerConfigStartup instances
    bootup.runServerConfigStartup(serverConfig);
    return bootup;
  }

  /**
   * Get the class based entities, scalarTypes, Listeners etc.
   */
  private BootupClasses getBootupClasses1(ServerConfig serverConfig) {

    List<Class<?>> entityClasses = serverConfig.getClasses();
    if (serverConfig.isDisableClasspathSearch() || (entityClasses != null && !entityClasses.isEmpty())) {
      // use classes we explicitly added via configuration
      return new BootupClasses(entityClasses);
    }

    return BootupClassPathSearch.search(serverConfig);
  }

  /**
   * Set the naming convention to underscore if it has not already been set.
   */
  private void setNamingConvention(ServerConfig config) {
    if (config.getNamingConvention() == null) {
      config.setNamingConvention(new UnderscoreNamingConvention());
    }
  }

  /**
   * Set the DatabasePlatform if it has not already been set.
   */
  private void setDatabasePlatform(ServerConfig config) {

    DatabasePlatform dbPlatform = config.getDatabasePlatform();
    if (dbPlatform == null) {
      DatabasePlatformFactory factory = new DatabasePlatformFactory();
      DatabasePlatform db = factory.create(config);
      db.configure(config);
      config.setDatabasePlatform(db);
      logger.info("DatabasePlatform name:{} platform:{}", config.getName(), db.getName());
    }
  }

  /**
   * Set the DataSource if it has not already been set.
   */
  private void setDataSource(ServerConfig config) {
    if (config.getDataSource() == null) {
      config.setDataSource(getDataSourceFromConfig(config));
    }
  }

  private DataSource getDataSourceFromConfig(ServerConfig config) {

    if (DbOffline.isSet()) {
      logger.debug("... DbOffline using platform [{}]", DbOffline.getPlatform());
      return null;
    }

    DataSource ds;

    if (config.getDataSourceJndiName() != null) {
      ds = jndiDataSourceFactory.lookup(config.getDataSourceJndiName());
      if (ds == null) {
        throw new PersistenceException("JNDI lookup for DataSource " + config.getDataSourceJndiName() + " returned null.");
      } else {
        return ds;
      }
    }

    DataSourceConfig dsConfig = config.getDataSourceConfig();
    if (dsConfig == null) {
      throw new PersistenceException("No DataSourceConfig defined for " + config.getName());
    }

    if (dsConfig.isOffline()) {
      if (config.getDatabasePlatformName() == null) {
        throw new PersistenceException("You MUST specify a DatabasePlatformName on ServerConfig when offline");
      }
      return null;
    }

    DataSourceFactory factory = config.service(DataSourceFactory.class);
    if (factory == null) {
      throw new IllegalStateException("No DataSourceFactory service implementation found in class path."
        + " Probably missing dependency to avaje-datasource?");
    }

    DataSourceAlertFactory alertFactory = config.service(DataSourceAlertFactory.class);
    if (alertFactory != null) {
      dsConfig.setAlert(alertFactory.createAlert());
    }

    attachListener(config, dsConfig);

    return factory.createPool(config.getName(), dsConfig);
  }

  /**
   * Create and attach a DataSourcePoolListener if it has been specified via properties and there is not one already attached.
   */
  private void attachListener(ServerConfig config, DataSourceConfig dsConfig) {
    if (dsConfig.getListener() == null) {
      String poolListener = dsConfig.getPoolListener();
      if (poolListener != null) {
        dsConfig.setListener((DataSourcePoolListener) config.getClassLoadConfig().newInstance(poolListener));
      }
    }
  }

  /**
   * Check the autoCommit and Transaction Isolation levels of the DataSource.
   * <p>
   * If autoCommit is true this could be a real problem.
   * </p>
   * <p>
   * If the Isolation level is not READ_COMMITTED then optimistic concurrency
   * checking may not work as expected.
   * </p>
   */
  private boolean checkDataSource(ServerConfig serverConfig) {

    if (DbOffline.isSet()) {
      return false;
    }

    if (serverConfig.getDataSource() == null) {
      if (serverConfig.getDataSourceConfig().isOffline()) {
        // this is ok - offline DDL generation etc
        return false;
      }
      throw new RuntimeException("DataSource not set?");
    }

    Connection c = null;
    try {
      c = serverConfig.getDataSource().getConnection();
      if (c.getAutoCommit()) {
        logger.warn("DataSource [{}] has autoCommit defaulting to true!", serverConfig.getName());
      }
      return true;

    } catch (SQLException ex) {
      throw new PersistenceException(ex);

    } finally {
      if (c != null) {
        try {
          c.close();
        } catch (SQLException ex) {
          logger.error("Error closing connection", ex);
        }
      }
    }
  }

}
