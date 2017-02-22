package io.ebeaninternal.server.deploy;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import io.ebean.EbeanServer;
import io.ebean.Query;
import io.ebean.Transaction;
import io.ebean.bean.BeanCollection;
import io.ebean.bean.BeanCollectionAdd;
import io.ebean.bean.BeanCollectionLoader;
import io.ebean.bean.EntityBean;
import io.ebean.common.BeanSet;
import io.ebeaninternal.server.text.json.WriteJson;

/**
 * Helper specifically for dealing with Sets.
 */
public final class BeanSetHelp<T> implements BeanCollectionHelp<T> {

  private final BeanPropertyAssocMany<T> many;
  private final BeanDescriptor<T> targetDescriptor;
  private final String propertyName;
  private BeanCollectionLoader loader;

  /**
   * When attached to a specific many property.
   */
  public BeanSetHelp(BeanPropertyAssocMany<T> many) {
    this.many = many;
    this.targetDescriptor = many.getTargetDescriptor();
    this.propertyName = many.getName();
  }

  /**
   * For a query that returns a set.
   */
  public BeanSetHelp() {
    this.many = null;
    this.targetDescriptor = null;
    this.propertyName = null;
  }

  @Override
  public void setLoader(BeanCollectionLoader loader) {
    this.loader = loader;
  }

  @Override
  public BeanCollectionAdd getBeanCollectionAdd(Object bc, String mapKey) {
    if (bc instanceof BeanSet<?>) {
      BeanSet<?> beanSet = (BeanSet<?>) bc;
      if (beanSet.getActualSet() == null) {
        beanSet.setActualSet(new LinkedHashSet<>());
      }
      return beanSet;

    } else {
      throw new RuntimeException("Unhandled type " + bc);
    }
  }

  @Override
  public void add(BeanCollection<?> collection, EntityBean bean, boolean withCheck) {
    if (withCheck) {
      collection.internalAddWithCheck(bean);
    } else {
      collection.internalAdd(bean);
    }
  }

  @Override
  public BeanCollection<T> createEmptyNoParent() {
    return new BeanSet<>();
  }

  @Override
  public BeanCollection<T> createEmpty(EntityBean ownerBean) {
    BeanSet<T> beanSet = new BeanSet<>(loader, ownerBean, propertyName);
    if (many != null) {
      beanSet.setModifyListening(many.getModifyListenMode());
    }
    return beanSet;
  }

  @Override
  public BeanCollection<T> createReference(EntityBean parentBean) {

    BeanSet<T> beanSet = new BeanSet<>(loader, parentBean, propertyName);
    beanSet.setModifyListening(many.getModifyListenMode());
    return beanSet;
  }

  @Override
  public void refresh(EbeanServer server, Query<?> query, Transaction t, EntityBean parentBean) {

    BeanSet<?> newBeanSet = (BeanSet<?>) server.findSet(query, t);
    refresh(newBeanSet, parentBean);
  }

  @Override
  public void refresh(BeanCollection<?> bc, EntityBean parentBean) {

    BeanSet<?> newBeanSet = (BeanSet<?>) bc;

    Set<?> current = (Set<?>) many.getValue(parentBean);

    newBeanSet.setModifyListening(many.getModifyListenMode());
    if (current == null) {
      // the currentList is null?  Not really expecting this...
      many.setValue(parentBean, newBeanSet);

    } else if (current instanceof BeanSet<?>) {
      // normally this case, replace just the underlying list
      BeanSet<?> currentBeanSet = (BeanSet<?>) current;
      currentBeanSet.setActualSet(newBeanSet.getActualSet());
      currentBeanSet.setModifyListening(many.getModifyListenMode());

    } else {
      // replace the entire set
      many.setValue(parentBean, newBeanSet);
    }
  }

  @Override
  public void jsonWrite(WriteJson ctx, String name, Object collection, boolean explicitInclude) throws IOException {

    Set<?> set;
    if (collection instanceof BeanCollection<?>) {
      BeanSet<?> bc = (BeanSet<?>) collection;
      if (!bc.isPopulated()) {
        if (explicitInclude) {
          // invoke lazy loading as collection
          // is explicitly included in the output
          bc.size();
        } else {
          return;
        }
      }
      set = bc.getActualSet();
    } else {
      set = (Set<?>) collection;
    }

    if (!set.isEmpty() || ctx.isIncludeEmpty()) {
      ctx.beginAssocMany(name);
      for (Object bean : set) {
        targetDescriptor.jsonWrite(ctx, (EntityBean) bean);
      }
      ctx.endAssocMany();
    }
  }
}
