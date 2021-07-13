package io.ebeaninternal.server.query;

import io.ebean.bean.EntityBean;
import io.ebean.bean.EntityBeanIntercept;
import io.ebean.bean.MutableValue;
import io.ebeaninternal.api.SpiQuery.Mode;
import io.ebeaninternal.server.deploy.BeanProperty;
import io.ebeaninternal.server.deploy.DbReadContext;

/**
 * Controls the loading of property data into a bean.
 * <p>
 * Takes into account the differences of lazy loading and
 * partial objects.
 * </p>
 */
public class SqlBeanLoad {

  private final DbReadContext ctx;
  private final EntityBean bean;
  private final EntityBeanIntercept ebi;

  private final Class<?> type;
  private final boolean lazyLoading;
  private final boolean refreshLoading;
  private final boolean rawSql;

  SqlBeanLoad(DbReadContext ctx, Class<?> type, EntityBean bean, Mode queryMode) {

    this.ctx = ctx;
    this.rawSql = ctx.isRawSql();
    this.type = type;
    this.lazyLoading = queryMode == Mode.LAZYLOAD_BEAN;
    this.refreshLoading = queryMode == Mode.REFRESH_BEAN;
    this.bean = bean;
    this.ebi = bean == null ? null : bean._ebean_getIntercept();
  }

  /**
   * Return true if this is a lazy loading.
   */
  public boolean isLazyLoad() {
    return lazyLoading;
  }

  /**
   * Return the DB read context.
   */
  public DbReadContext ctx() {
    return ctx;
  }

  public Object load(BeanProperty prop) {

    if (!rawSql && !prop.isLoadProperty(ctx.isDraftQuery())) {
      return null;
    }

    if ((bean == null)
      || (lazyLoading && ebi.isLoadedProperty(prop.getPropertyIndex()))
      || (type != null && !prop.isAssignableFrom(type))) {

      // ignore this property
      // ... null: bean already in persistence context
      // ... lazyLoading: partial bean that is lazy loading
      // ... type: inheritance and not assignable to this instance

      prop.loadIgnore(ctx);
      return null;
    }

    try {
      Object dbVal = null;
      if (!refreshLoading) {
        if (prop.isMutableScalarType()) {
          MutableValue mutableValue = prop.readMutable(ctx);
          if (mutableValue != null) {
            dbVal = mutableValue.get();
            prop.setMutableOrigValue(bean, mutableValue);
          }
        } else {
          dbVal = prop.read(ctx);
        }
        prop.setValue(bean, dbVal);
      } else {
        dbVal = prop.read(ctx);
        prop.setValueIntercept(bean, dbVal);
      }
      return dbVal;
    } catch (Exception e) {
      bean._ebean_getIntercept().setLoadError(prop.getPropertyIndex(), e);
      ctx.handleLoadError(prop.getFullBeanName(), e);
      return prop.getValue(bean);
    }
  }

  /**
   * Load the given value into the property.
   */
  public void load(BeanProperty target, Object dbVal) {
    if (!refreshLoading) {
      target.setValue(bean, dbVal);
    } else {
      target.setValueIntercept(bean, dbVal);
    }
  }
}
