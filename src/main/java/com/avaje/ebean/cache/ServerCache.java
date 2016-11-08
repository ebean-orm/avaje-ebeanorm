package com.avaje.ebean.cache;

/**
 * Represents part of the "L2" server side cache.
 * <p>
 * This is used to cache beans or query results (bean collections).
 * </p>
 * <p>
 * There are 2 ServerCache's for each bean type. One is used as the 'bean cache'
 * which holds beans of a given type. The other is the 'query cache' holding
 * query results for a given type.
 * </p>
 *
 * @author rbygrave
 */
public interface ServerCache {

  /**
   * Return the value given the key.
   */
  Object get(Object id);

  /**
   * Put the value in the cache with a given id.
   */
  Object put(Object id, Object value);

  /**
   * Remove a entry from the cache given its id.
   */
  Object remove(Object id);

  /**
   * Clear all entries from the cache.
   * <p>
   * NOTE: Be careful using this method in that most of the time application
   * code should clear BOTH the bean and query caches at the same time. This can
   * be done via {@link ServerCacheManager#clear(Class)}.
   * </p>
   */
  void clear();

  /**
   * Return the number of entries in the cache.
   */
  int size();

  /**
   * Return the hit ratio the cache is currently getting.
   */
  int getHitRatio();

  /**
   * Return statistics for the cache.
   *
   * @param reset if true the statistics are reset.
   */
  ServerCacheStatistics getStatistics(boolean reset);
}
