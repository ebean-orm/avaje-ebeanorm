package com.avaje.ebeaninternal.server.cache;

import com.avaje.ebean.cache.*;


/**
 * Manages the bean and query caches.
 */
public class DefaultServerCacheManager implements ServerCacheManager {

  private final DefaultCacheHolder beanCache;

  private final DefaultCacheHolder queryCache;

  private final DefaultCacheHolder naturalKeyCache;

  private final DefaultCacheHolder collectionIdsCache;

  private final boolean localL2Caching;

  /**
   * Create with a cache factory and default cache options.
   */
  public DefaultServerCacheManager(boolean localL2Caching, ServerCacheFactory cacheFactory, ServerCacheOptions defaultBeanOptions, ServerCacheOptions defaultQueryOptions) {
    this.localL2Caching = localL2Caching;
    this.beanCache = new DefaultCacheHolder(cacheFactory, defaultBeanOptions);
    this.queryCache = new DefaultCacheHolder(cacheFactory, defaultQueryOptions);
    this.naturalKeyCache = new DefaultCacheHolder(cacheFactory, defaultBeanOptions);
    this.collectionIdsCache = new DefaultCacheHolder(cacheFactory, defaultBeanOptions);
  }

  /**
   * Construct when l2 cache is disabled.
   */
  public DefaultServerCacheManager() {
    this(true, new DefaultServerCacheFactory(), new ServerCacheOptions(), new ServerCacheOptions());
  }

  public boolean isLocalL2Caching() {
    return localL2Caching;
  }

  /**
   * Clear both the bean cache and the query cache for a
   * given bean type.
   */
  public void clear(Class<?> beanType) {
    String beanName = beanType.getName();
    beanCache.clearCache(beanName);
    naturalKeyCache.clearCache(beanName);
    collectionIdsCache.clearCache(beanName);
    queryCache.clearCache(beanName);
  }

  /**
   * Clear all caches.
   */
  public void clearAll() {
    beanCache.clearAll();
    queryCache.clearAll();
    naturalKeyCache.clearAll();
    collectionIdsCache.clearAll();
  }

  public ServerCache getCollectionIdsCache(Class<?> beanType, String propertyName) {
    return collectionIdsCache.getCache(beanType.getName() + "." + propertyName, ServerCacheType.COLLECTION_IDS);
  }

  public ServerCache getNaturalKeyCache(Class<?> beanType) {
    return naturalKeyCache.getCache(beanType.getName(), ServerCacheType.NATURAL_KEY);
  }

  /**
   * Return the query cache for a given bean type.
   */
  public ServerCache getQueryCache(Class<?> beanType) {
    return queryCache.getCache(beanType.getName(), ServerCacheType.QUERY);
  }

  /**
   * Return the bean cache for a given bean type.
   */
  public ServerCache getBeanCache(Class<?> beanType) {
    return beanCache.getCache(beanType.getName(), ServerCacheType.BEAN);
  }

  /**
   * Return true if there is an active cache for the given bean type.
   */
  public boolean isBeanCaching(Class<?> beanType) {
    return beanCache.isCaching(beanType.getName());
  }

}
