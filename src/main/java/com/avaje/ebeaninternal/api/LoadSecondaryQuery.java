package com.avaje.ebeaninternal.api;

import com.avaje.ebeaninternal.server.core.OrmQueryRequest;

/**
 * Defines the method for executing secondary queries.
 * <p>
 * That is +query nodes in a orm query get executed after
 * the initial query as 'secondary' queries.
 * </p>
 */
public interface LoadSecondaryQuery {

  /**
   * Execute the secondary query with a given batch size.
   */
  void loadSecondaryQuery(OrmQueryRequest<?> parentRequest, boolean forEach);
}
