package com.avaje.ebeaninternal.server.persist.dmlbind;

import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.deploy.BeanPropertyAssocOne;
import com.avaje.ebeaninternal.server.persist.dml.DmlMode;

import java.util.List;

/**
 * A factory that builds Bindable for BeanPropertyAssocOne properties.
 */
public class FactoryAssocOnes {

  public FactoryAssocOnes() {
  }

  /**
   * Add foreign key columns from associated one beans.
   */
  public void create(List<Bindable> list, BeanDescriptor<?> desc, DmlMode mode) {

    BeanPropertyAssocOne<?>[] ones = desc.propertiesOneImported();

    for (int i = 0; i < ones.length; i++) {
      if (!ones[i].isImportedPrimaryKey()) {
        switch (mode) {
          case INSERT:
            if (!ones[i].isInsertable()) {
              continue;
            }
            break;
          case UPDATE:
            if (!ones[i].isUpdateable()) {
              continue;
            }
            break;
        }
        list.add(new BindableAssocOne(ones[i]));
      }
    }
  }
}
