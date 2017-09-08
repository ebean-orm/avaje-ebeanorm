package io.ebeaninternal.server.persist.dmlbind;

import io.ebeaninternal.server.deploy.BeanDescriptor;
import io.ebeaninternal.server.deploy.BeanProperty;

/**
 * Creates a Bindable to support version concurrency where clauses.
 */
public class FactoryVersion {


  public FactoryVersion() {
  }

  /**
   * Create a Bindable for the version property(s) for a bean type.
   */
  public Bindable create(BeanDescriptor<?> desc) {

    BeanProperty versionProperty = desc.getVersionProperty();
    return (versionProperty == null) ? null : new BindableProperty(versionProperty);
  }

  /**
   * Create a Bindable for TenantId If multi-tenant with partitioning is on this bean type.
   */
  public Bindable createTenantId(BeanDescriptor<?> desc) {

    BeanProperty tenant = desc.getTenantProperty();
    return (tenant == null) ? null : new BindableProperty(tenant);
  }
}
