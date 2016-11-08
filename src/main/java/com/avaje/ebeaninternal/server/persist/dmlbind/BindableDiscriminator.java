package com.avaje.ebeaninternal.server.persist.dmlbind;

import com.avaje.ebean.bean.EntityBean;
import com.avaje.ebeaninternal.server.core.PersistRequestBean;
import com.avaje.ebeaninternal.server.deploy.InheritInfo;
import com.avaje.ebeaninternal.server.persist.dml.GenerateDmlRequest;

import javax.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;

/**
 * Bindable for inserting a discriminator value.
 */
public class BindableDiscriminator implements Bindable {

  private final String columnName;
  private final Object discValue;
  private final int sqlType;

  public BindableDiscriminator(InheritInfo inheritInfo) {
    this.columnName = inheritInfo.getDiscriminatorColumn();
    this.discValue = inheritInfo.getDiscriminatorValue();
    this.sqlType = inheritInfo.getDiscriminatorType();
  }

  public String toString() {
    return columnName + " = " + discValue;
  }

  @Override
  public boolean isDraftOnly() {
    return false;
  }

  @Override
  public void addToUpdate(PersistRequestBean<?> request, List<Bindable> list) {
    throw new PersistenceException("Never called (only for inserts)");
  }

  @Override
  public void dmlAppend(GenerateDmlRequest request) {
    request.appendColumn(columnName);
  }

  @Override
  public void dmlBind(BindableRequest bindRequest, EntityBean bean) throws SQLException {

    bindRequest.bind(discValue, sqlType);
  }

}
