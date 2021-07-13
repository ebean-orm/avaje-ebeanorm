package io.ebean.bean;

import io.ebean.DB;
import io.ebean.Database;
import io.ebean.ValuePair;

import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is the object added to every entity bean using byte code enhancement.
 * <p>
 * This provides the mechanisms to support deferred fetching of reference beans
 * and oldValues generation for concurrency checking.
 * </p>
 */
public final class EntityBeanIntercept implements Serializable {

  private static final long serialVersionUID = -3664031775464862649L;

  private static final int STATE_NEW = 0;
  private static final int STATE_REFERENCE = 1;
  private static final int STATE_LOADED = 2;

  /**
   * Used when a bean is partially filled.
   */
  private static final byte FLAG_LOADED_PROP = 1;
  private static final byte FLAG_CHANGED_PROP = 2;

  /**
   * Flags indicating if a property is a dirty embedded bean. Used to distinguish
   * between an embedded bean being completely overwritten and one of its
   * embedded properties being made dirty.
   */
  private static final byte FLAG_EMBEDDED_DIRTY = 4;
  /**
   * Flags indicating if a property is a dirty embedded bean. Used to distinguish
   * between an embedded bean being completely overwritten and one of its
   * embedded properties being made dirty.
   */
  private static final byte FLAG_ORIG_VALUE_SET = 8;
  
  /**
   * Flags indicating if a property has a mutable value set.
   */
  private static final byte FLAG_MUTABLE_VALUE = 16;
  
  private transient final ReentrantLock lock = new ReentrantLock();
  private transient NodeUsageCollector nodeUsageCollector;
  private transient PersistenceContext persistenceContext;
  private transient BeanLoader beanLoader;
  private transient PreGetterCallback preGetterCallback;

  private String ebeanServerName;
  private boolean deletedFromCollection;

  /**
   * The actual entity bean that 'owns' this intercept.
   */
  private final EntityBean owner;
  private EntityBean embeddedOwner;
  private int embeddedOwnerIndex;
  /**
   * One of NEW, REF, UPD.
   */
  private int state;
  private boolean forceUpdate;
  private boolean readOnly;
  private boolean dirty;
  /**
   * Flag set to disable lazy loading - typically for SQL "report" type entity beans.
   */
  private boolean disableLazyLoad;

  /**
   * Flag set when lazy loading failed due to the underlying bean being deleted in the DB.
   */
  private boolean lazyLoadFailure;
  private boolean fullyLoadedBean;
  private boolean loadedFromCache;
  private final byte[] flags;
  private Object[] origValues;
  private Exception[] loadErrors;
  private int lazyLoadProperty = -1;
  private Object ownerId;
  private int sortOrder;

  /**
   * Create a intercept with a given entity.
   */
  public EntityBeanIntercept(Object ownerBean) {
    this.owner = (EntityBean) ownerBean;
    this.flags = new byte[owner._ebean_getPropertyNames().length];
  }

  /**
   * EXPERIMENTAL - Constructor only for use by serialization frameworks.
   */
  public EntityBeanIntercept() {
    this.owner = null;
    this.flags = null;
  }

  /**
   * Return the 'owning' entity bean.
   */
  public EntityBean getOwner() {
    return owner;
  }

  /**
   * Return the persistenceContext.
   */
  public PersistenceContext getPersistenceContext() {
    return persistenceContext;
  }

  /**
   * Set the persistenceContext.
   */
  public void setPersistenceContext(PersistenceContext persistenceContext) {
    this.persistenceContext = persistenceContext;
  }

  /**
   * Turn on profile collection.
   */
  public void setNodeUsageCollector(NodeUsageCollector usageCollector) {
    this.nodeUsageCollector = usageCollector;
  }

  /**
   * Return the ownerId (IdClass).
   */
  public Object getOwnerId() {
    return ownerId;
  }

  /**
   * Set the ownerId (IdClass).
   */
  public void setOwnerId(Object ownerId) {
    this.ownerId = ownerId;
  }

  /**
   * Return the owning bean for an embedded bean.
   */
  public Object getEmbeddedOwner() {
    return embeddedOwner;
  }

  /**
   * Return the property index (for the parent) of this embedded bean.
   */
  public int getEmbeddedOwnerIndex() {
    return embeddedOwnerIndex;
  }

  /**
   * Clear the getter callback.
   */
  public void clearGetterCallback() {
    this.preGetterCallback = null;
  }

  /**
   * Register the callback to be triggered when getter is called.
   * This is used primarily to automatically flush the JDBC batch.
   */
  public void registerGetterCallback(PreGetterCallback getterCallback) {
    this.preGetterCallback = getterCallback;
  }

  /**
   * Set the embedded beans owning bean.
   */
  public void setEmbeddedOwner(EntityBean parentBean, int embeddedOwnerIndex) {
    this.embeddedOwner = parentBean;
    this.embeddedOwnerIndex = embeddedOwnerIndex;
  }

  /**
   * Set the BeanLoader with PersistenceContext.
   */
  public void setBeanLoader(BeanLoader beanLoader, PersistenceContext ctx) {
    this.beanLoader = beanLoader;
    this.persistenceContext = ctx;
    this.ebeanServerName = beanLoader.getName();
  }

  /**
   * Set the BeanLoader.
   */
  public void setBeanLoader(BeanLoader beanLoader) {
    this.beanLoader = beanLoader;
    this.ebeanServerName = beanLoader.getName();
  }

  public boolean isFullyLoadedBean() {
    return fullyLoadedBean;
  }

  public void setFullyLoadedBean(boolean fullyLoadedBean) {
    this.fullyLoadedBean = fullyLoadedBean;
  }

  /**
   * Check each property to see if the bean is partially loaded.
   */
  public boolean isPartial() {
    for (byte flag : flags) {
      if ((flag & FLAG_LOADED_PROP) == 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return true if this bean has been directly modified (it has oldValues) or
   * if any embedded beans are either new or dirty (and hence need saving).
   */
  public boolean isDirty() {
    return dirty;
  }

  /**
   * Called by an embedded bean onto its owner.
   */
  public void setEmbeddedDirty(int embeddedProperty) {
    this.dirty = true;
    setEmbeddedPropertyDirty(embeddedProperty);
  }

  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  /**
   * Return true if this entity bean is new and not yet saved.
   */
  public boolean isNew() {
    return state == STATE_NEW;
  }

  /**
   * Return true if the entity bean is new or dirty (and should be saved).
   */
  public boolean isNewOrDirty() {
    return isNew() || isDirty();
  }

  /**
   * Return true if only the Id property has been loaded.
   */
  public boolean hasIdOnly(int idIndex) {
    for (int i = 0; i < flags.length; i++) {
      if (i == idIndex) {
        if ((flags[i] & FLAG_LOADED_PROP) == 0) return false;
      } else if ((flags[i] & FLAG_LOADED_PROP) != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Return true if the entity is a reference.
   */
  public boolean isReference() {
    return state == STATE_REFERENCE;
  }

  /**
   * Set this as a reference object.
   */
  public void setReference(int idPos) {
    state = STATE_REFERENCE;
    if (idPos > -1) {
      // For cases where properties are set on constructor
      // set every non Id property to unloaded (for lazy loading)
      for (int i = 0; i < flags.length; i++) {
        if (i != idPos) {
          flags[i] &= ~FLAG_LOADED_PROP;
        }
      }
    }
  }

  /**
   * Set true when the bean has been loaded from L2 bean cache.
   * The effect of this is that we should skip the cache if there
   * is subsequent lazy loading (bean cache partially populated).
   */
  public void setLoadedFromCache(boolean loadedFromCache) {
    this.loadedFromCache = loadedFromCache;
  }

  /**
   * Return true if this bean was loaded from L2 bean cache.
   */
  public boolean isLoadedFromCache() {
    return loadedFromCache;
  }

  /**
   * Return true if the bean should be treated as readOnly. If a setter method
   * is called when it is readOnly an Exception is thrown.
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Set the readOnly status. If readOnly then calls to setter methods through
   * an exception.
   */
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  /**
   * Set the bean to be updated when persisted (for merge).
   */
  public void setForceUpdate(boolean forceUpdate) {
    this.forceUpdate = forceUpdate;
  }

  /**
   * Return true if the entity should be updated.
   */
  public boolean isUpdate() {
    return forceUpdate || state == STATE_LOADED;
  }

  /**
   * Return true if the entity has been loaded.
   */
  public boolean isLoaded() {
    return state == STATE_LOADED;
  }

  /**
   * Set the bean into NEW state.
   */
  public void setNew() {
    this.state = STATE_NEW;
  }

  /**
   * Set the loaded state to true.
   * <p>
   * Calls to setter methods after the bean is loaded can result in
   * 'Old Values' being created.
   * <p>
   * Worth noting that this is also set after a insert/update. By doing so it
   * 'resets' the bean for making further changes and saving again.
   */
  public void setLoaded() {
    this.state = STATE_LOADED;
    this.owner._ebean_setEmbeddedLoaded();
    this.lazyLoadProperty = -1;
    // this.origValues = null; // TODO: Clear only non mutable values
    for (int i = 0; i < flags.length; i++) {
      flags[i] &= ~(FLAG_CHANGED_PROP + FLAG_ORIG_VALUE_SET);
    }
    this.dirty = false;
  }

  /**
   * When finished loading for lazy or refresh on an already partially populated bean.
   */
  public void setLoadedLazy() {
    this.state = STATE_LOADED;
    this.lazyLoadProperty = -1;
  }

  /**
   * Set lazy load failure flag.
   */
  public void setLazyLoadFailure(Object ownerId) {
    this.lazyLoadFailure = true;
    this.ownerId = ownerId;
  }

  /**
   * Return true if the bean is marked as having failed lazy loading.
   */
  public boolean isLazyLoadFailure() {
    return lazyLoadFailure;
  }

  /**
   * Return true if lazy loading is disabled.
   */
  public boolean isDisableLazyLoad() {
    return disableLazyLoad;
  }

  /**
   * Set true to turn off lazy loading.
   */
  public void setDisableLazyLoad(boolean disableLazyLoad) {
    this.disableLazyLoad = disableLazyLoad;
  }

  /**
   * Set the loaded status for the embedded bean.
   */
  public void setEmbeddedLoaded(Object embeddedBean) {
    if (embeddedBean instanceof EntityBean) {
      EntityBean eb = (EntityBean) embeddedBean;
      eb._ebean_getIntercept().setLoaded();
    }
  }

  /**
   * Return true if the embedded bean is new or dirty and hence needs saving.
   */
  public boolean isEmbeddedNewOrDirty(Object embeddedBean) {
    if (embeddedBean == null) {
      // if it was previously set then the owning bean would
      // have oldValues containing the previous embedded bean
      return false;
    }
    if (embeddedBean instanceof EntityBean) {
      return ((EntityBean) embeddedBean)._ebean_getIntercept().isNewOrDirty();
    } else {
      // non-enhanced so must assume it is new and needs to be saved
      return true;
    }
  }

  /**
   * Return the original value that was changed via an update.
   */
  public Object getOrigValue(int propertyIndex) {
    if (origValues == null) {
      return null;
    }
    Object ret = origValues[propertyIndex];
    if (ret instanceof MutableValue) {
      return ((MutableValue) ret).get();
    } else {
      return ret;
    }
  }

  /**
   * Finds the index position of a given property. Returns -1 if the
   * property can not be found.
   */
  public int findProperty(String propertyName) {
    String[] names = owner._ebean_getPropertyNames();
    for (int i = 0; i < names.length; i++) {
      if (names[i].equals(propertyName)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Return the property name for the given property.
   */
  public String getProperty(int propertyIndex) {
    if (propertyIndex == -1) {
      return null;
    }
    return owner._ebean_getPropertyName(propertyIndex);
  }

  /**
   * Return the number of properties.
   */
  public int getPropertyLength() {
    return owner._ebean_getPropertyNames().length;
  }

  /**
   * Set the loaded state of the property given it's name.
   */
  public void setPropertyLoaded(String propertyName, boolean loaded) {
    int position = findProperty(propertyName);
    if (position == -1) {
      throw new IllegalArgumentException("Property " + propertyName + " not found");
    }
    if (loaded) {
      flags[position] |= FLAG_LOADED_PROP;
    } else {
      flags[position] &= ~FLAG_LOADED_PROP;
    }
  }

  /**
   * Set the property to be treated as unloaded. Used for properties initialised in default constructor.
   */
  public void setPropertyUnloaded(int propertyIndex) {
    flags[propertyIndex] &= ~FLAG_LOADED_PROP;
  }

  /**
   * Set the property to be loaded.
   */
  public void setLoadedProperty(int propertyIndex) {
    flags[propertyIndex] |= FLAG_LOADED_PROP;
  }

  /**
   * Set all properties to be loaded (post insert).
   */
  public void setLoadedPropertyAll() {
    for (int i = 0; i < flags.length; i++) {
      flags[i] |= FLAG_LOADED_PROP;
    }
  }

  /**
   * Return true if the property is loaded.
   */
  public boolean isLoadedProperty(int propertyIndex) {
    return (flags[propertyIndex] & FLAG_LOADED_PROP) != 0;
  }

  /**
   * Return true if the property is considered changed.
   */
  public boolean isChangedProperty(int propertyIndex) {
    return (flags[propertyIndex] & FLAG_CHANGED_PROP) != 0;
  }

  /**
   * Return true if the property was changed or if it is embedded and one of its
   * embedded properties is dirty.
   */
  public boolean isDirtyProperty(int propertyIndex) {
    return (flags[propertyIndex] & (FLAG_CHANGED_PROP + FLAG_EMBEDDED_DIRTY)) != 0;
  }

  /**
   * Explicitly mark a property as having been changed.
   */
  public void markPropertyAsChanged(int propertyIndex) {
    setChangedProperty(propertyIndex);
    setDirty(true);
  }

  public void setChangedProperty(int propertyIndex) {
    flags[propertyIndex] |= FLAG_CHANGED_PROP;
  }

  private void setChangeLoaded(int propertyIndex) {
    flags[propertyIndex] |= FLAG_CHANGED_PROP | FLAG_LOADED_PROP;
  }

  /**
   * Set that an embedded bean has had one of its properties changed.
   */
  private void setEmbeddedPropertyDirty(int propertyIndex) {
    flags[propertyIndex] |= FLAG_EMBEDDED_DIRTY;
  }

  public void setOriginalValue(int propertyIndex, Object value) {
    if (origValues == null) {
      origValues = new Object[owner._ebean_getPropertyNames().length];
    }
    if ((flags[propertyIndex] & FLAG_ORIG_VALUE_SET) == 0) {
      flags[propertyIndex] |= FLAG_ORIG_VALUE_SET;
      if (value instanceof MutableValue) {
        flags[propertyIndex] |= FLAG_MUTABLE_VALUE;
      }
      origValues[propertyIndex] = value;
    }
  }

  /**
   * Set old value but force it to be set regardless if it already has a value.
   */
  private void setOriginalValueForce(int propertyIndex, Object value) {
    if (origValues == null) {
      origValues = new Object[owner._ebean_getPropertyNames().length];
    }
    origValues[propertyIndex] = value;
  }

  /**
   * For forced update on a 'New' bean set all the loaded properties to changed.
   */
  public void setNewBeanForUpdate() {
    for (int i = 0; i < flags.length; i++) {
      if ((flags[i] & FLAG_LOADED_PROP) != 0) {
        flags[i] |= FLAG_CHANGED_PROP;
      }
    }
    setDirty(true);
  }

  /**
   * Return the set of property names for a partially loaded bean.
   */
  public Set<String> getLoadedPropertyNames() {
    if (fullyLoadedBean) {
      return null;
    }
    Set<String> props = new LinkedHashSet<>();
    for (int i = 0; i < flags.length; i++) {
      if ((flags[i] & FLAG_LOADED_PROP) != 0) {
        props.add(getProperty(i));
      }
    }
    return props;
  }

  /**
   * Return the array of flags indicating the dirty properties.
   */
  public boolean[] getDirtyProperties() {
    int len = getPropertyLength();
    boolean[] dirties = new boolean[len];
    for (int i = 0; i < len; i++) {
      // this, or an embedded property has been changed - recurse
      dirties[i] = (flags[i] & (FLAG_CHANGED_PROP + FLAG_EMBEDDED_DIRTY)) != 0;
    }
    return dirties;
  }

  /**
   * Return the set of dirty properties.
   */
  public Set<String> getDirtyPropertyNames() {
    Set<String> props = new LinkedHashSet<>();
    addDirtyPropertyNames(props, null);
    return props;
  }

  /**
   * Recursively add dirty properties.
   */
  public void addDirtyPropertyNames(Set<String> props, String prefix) {
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if ((flags[i] & FLAG_CHANGED_PROP) != 0) {
        // the property has been changed on this bean
        props.add((prefix == null ? getProperty(i) : prefix + getProperty(i)));
      } else if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) {
        // an embedded property has been changed - recurse
        EntityBean embeddedBean = (EntityBean) owner._ebean_getField(i);
        embeddedBean._ebean_getIntercept().addDirtyPropertyNames(props, getProperty(i) + ".");
      }
    }
  }

  /**
   * Return true if any of the given property names are dirty.
   */
  public boolean hasDirtyProperty(Set<String> propertyNames) {
    String[] names = owner._ebean_getPropertyNames();
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if ((flags[i] & FLAG_CHANGED_PROP) != 0) {
        if (propertyNames.contains(names[i])) {
          return true;
        }
      } else if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) {
        if (propertyNames.contains(names[i])) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Return a map of dirty properties with their new and old values.
   */
  public Map<String, ValuePair> getDirtyValues() {
    Map<String, ValuePair> dirtyValues = new LinkedHashMap<>();
    addDirtyPropertyValues(dirtyValues, null);
    return dirtyValues;
  }

  /**
   * Recursively add dirty properties.
   */
  public void addDirtyPropertyValues(Map<String, ValuePair> dirtyValues, String prefix) {
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if ((flags[i] & (FLAG_CHANGED_PROP | FLAG_MUTABLE_VALUE)) != 0) {
        // the property has been changed on this bean or may be a mutable value
        String propName = (prefix == null ? getProperty(i) : prefix + getProperty(i));
        Object newVal = owner._ebean_getField(i);
        Object oldVal = origValues[i];
        if (notEqual(oldVal, newVal, false)) {
          if (oldVal instanceof MutableValue) {
            oldVal = ((MutableValue) oldVal).get();
          }
          dirtyValues.put(propName, new ValuePair(newVal, oldVal));
        }
      } else if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) {
        // an embedded property has been changed - recurse
        EntityBean embeddedBean = (EntityBean) owner._ebean_getField(i);
        embeddedBean._ebean_getIntercept().addDirtyPropertyValues(dirtyValues, getProperty(i) + ".");
      }
    }
  }

  /**
   * Recursively add dirty properties.
   */
  public void addDirtyPropertyValues(BeanDiffVisitor visitor) {
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if ((flags[i] & FLAG_CHANGED_PROP) != 0) {
        // the property has been changed on this bean
        Object newVal = owner._ebean_getField(i);
        Object oldVal = origValues[i];
        if (notEqual(oldVal, newVal, false)) {
          visitor.visit(i, newVal, oldVal);
        }
      } else if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) {
        // an embedded property has been changed - recurse
        EntityBean embeddedBean = (EntityBean) owner._ebean_getField(i);
        visitor.visitPush(i);
        embeddedBean._ebean_getIntercept().addDirtyPropertyValues(visitor);
        visitor.visitPop();
      }
    }
  }

  /**
   * Return a dirty property hash taking into account embedded beans.
   */
  public StringBuilder getDirtyPropertyKey() {
    StringBuilder sb = new StringBuilder();
    addDirtyPropertyKey(sb);
    return sb;
  }

  /**
   * Add and return a dirty property hash.
   */
  private void addDirtyPropertyKey(StringBuilder sb) {
    if (sortOrder > 0) {
      sb.append("s,");
    }
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if ((flags[i] & FLAG_CHANGED_PROP) != 0) {
        sb.append(i).append(',');
      } else if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) {
        // an embedded property has been changed - recurse
        EntityBean embeddedBean = (EntityBean) owner._ebean_getField(i);
        sb.append(i).append('[');
        embeddedBean._ebean_getIntercept().addDirtyPropertyKey(sb);
        sb.append(']');
      }
    }
  }

  /**
   * Return a loaded property hash.
   */
  public StringBuilder getLoadedPropertyKey() {
    StringBuilder sb = new StringBuilder();
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      if (isLoadedProperty(i)) {
        sb.append(i).append(',');
      }
    }
    return sb;
  }

  public boolean[] getLoaded() {
    boolean[] ret = new boolean[flags.length];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = (flags[i] & FLAG_LOADED_PROP) != 0;
    }
    return ret;
  }

  /**
   * Return the index of the property that triggered the lazy load.
   */
  public int getLazyLoadPropertyIndex() {
    return lazyLoadProperty;
  }

  /**
   * Return the property that triggered the lazy load.
   */
  public String getLazyLoadProperty() {
    return getProperty(lazyLoadProperty);
  }

  /**
   * Load the bean when it is a reference.
   */
  protected void loadBean(int loadProperty) {
    lock.lock();
    try {
      if (beanLoader == null) {
        final Database database = DB.byName(ebeanServerName);
        if (database == null) {
          throw new PersistenceException("Database [" + ebeanServerName + "] was not found?");
        }
        // For stand alone reference bean or after deserialisation lazy load
        // using the ebeanServer. Synchronise only on the bean.
        loadBeanInternal(loadProperty, database.getPluginApi().beanLoader());
        return;
      }
    } finally {
      lock.unlock();
    }
    final Lock lock = beanLoader.lock();
    try {
      // Lazy loading using LoadBeanContext which supports batch loading
      // Synchronise on the beanLoader (a 'node' of the LoadBeanContext 'tree')
      loadBeanInternal(loadProperty, beanLoader);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Invoke the lazy loading. This method is synchronised externally.
   */
  private void loadBeanInternal(int loadProperty, BeanLoader loader) {
    if ((flags[loadProperty] & FLAG_LOADED_PROP) != 0) {
      // race condition where multiple threads calling preGetter concurrently
      return;
    }
    if (lazyLoadFailure) {
      // failed when batch lazy loaded by another bean in the batch
      throw new EntityNotFoundException("(Lazy) loading failed on type:" + owner.getClass().getName() + " id:" + ownerId + " - Bean has been deleted");
    }
    if (lazyLoadProperty == -1) {
      lazyLoadProperty = loadProperty;
      if (nodeUsageCollector != null) {
        nodeUsageCollector.setLoadProperty(getProperty(lazyLoadProperty));
      }
      loader.loadBean(this);
      if (lazyLoadFailure) {
        // failed when lazy loading this bean
        throw new EntityNotFoundException("Lazy loading failed on type:" + owner.getClass().getName() + " id:" + ownerId + " - Bean has been deleted.");
      }
      // bean should be loaded and intercepting now. setLoaded() has
      // been called by the lazy loading mechanism
    }
  }

  /**
   * Helper method to check if two objects are equal.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected static boolean notEqual(Object obj1, Object obj2, boolean update) {
    if (obj1 == null) {
      return (obj2 != null);
    }
    if (obj2 == null) {
      return true;
    }
    if (obj1 == obj2) {
      return false;
    }
    if (obj1 instanceof BigDecimal) {
      // Use comparable for BigDecimal as equals
      // uses scale in comparison...
      if (obj2 instanceof BigDecimal) {
        Comparable com1 = (Comparable) obj1;
        return (com1.compareTo(obj2) != 0);
      } else {
        return true;
      }
    }
    if (obj1 instanceof URL) {
      // use the string format to determine if dirty
      return !obj1.toString().equals(obj2.toString());
    }
    if (obj1 instanceof MutableValue) {
      return !((MutableValue) obj1).isEqual(obj2, update);
    }
    return !obj1.equals(obj2);
  }

  /**
   * Called when a BeanCollection is initialised automatically.
   */
  public void initialisedMany(int propertyIndex) {
    flags[propertyIndex] |= FLAG_LOADED_PROP;
  }

  private void preGetterCallback(int propertyIndex) {
    PreGetterCallback preGetterCallback = this.preGetterCallback;
    if (preGetterCallback != null) {
      preGetterCallback.preGetterTrigger(propertyIndex);
    }
  }

  /**
   * Called prior to Id property getter.
   */
  public void preGetId() {
    preGetterCallback(-1);
  }

  /**
   * Method that is called prior to a getter method on the actual entity.
   */
  public void preGetter(int propertyIndex) {
    preGetterCallback(propertyIndex);
    if (state == STATE_NEW || disableLazyLoad) {
      return;
    }
    if (!isLoadedProperty(propertyIndex)) {
      loadBean(propertyIndex);
    }
    if (nodeUsageCollector != null) {
      nodeUsageCollector.addUsed(getProperty(propertyIndex));
    }
  }

  /**
   * OneToMany and ManyToMany only set loaded state.
   */
  public void preSetterMany(boolean interceptField, int propertyIndex, Object oldValue, Object newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else {
      if (readOnly) {
        throw new IllegalStateException("This bean is readOnly");
      }
      setChangeLoaded(propertyIndex);
    }
  }

  private void setChangedPropertyValue(int propertyIndex, boolean setDirtyState, Object origValue) {
    if (readOnly) {
      throw new IllegalStateException("This bean is readOnly");
    }
    setChangedProperty(propertyIndex);
    if (setDirtyState) {
      setOriginalValue(propertyIndex, origValue);
      setDirtyStatus();
    }
  }

  private void setDirtyStatus() {
    if (!dirty) {
      dirty = true;
      if (embeddedOwner != null) {
        // Cascade dirty state from Embedded bean to parent bean
        embeddedOwner._ebean_getIntercept().setEmbeddedDirty(embeddedOwnerIndex);
      }
      if (nodeUsageCollector != null) {
        nodeUsageCollector.setModified();
      }
    }
  }

  /**
   * Check to see if the values are not equal. If they are not equal then create
   * the old values for use with ConcurrencyMode.ALL.
   */
  public void preSetter(boolean intercept, int propertyIndex, Object oldValue, Object newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (notEqual(oldValue, newValue, false)) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * Check for primitive boolean.
   */
  public void preSetter(boolean intercept, int propertyIndex, boolean oldValue, boolean newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * Check for primitive int.
   */
  public void preSetter(boolean intercept, int propertyIndex, int oldValue, int newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * long.
   */
  public void preSetter(boolean intercept, int propertyIndex, long oldValue, long newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * double.
   */
  public void preSetter(boolean intercept, int propertyIndex, double oldValue, double newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (Double.compare(oldValue, newValue) != 0) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * float.
   */
  public void preSetter(boolean intercept, int propertyIndex, float oldValue, float newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (Float.compare(oldValue, newValue) != 0) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * short.
   */
  public void preSetter(boolean intercept, int propertyIndex, short oldValue, short newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * char.
   */
  public void preSetter(boolean intercept, int propertyIndex, char oldValue, char newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * byte.
   */
  public void preSetter(boolean intercept, int propertyIndex, byte oldValue, byte newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (oldValue != newValue) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * char[].
   */
  public void preSetter(boolean intercept, int propertyIndex, char[] oldValue, char[] newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (!Arrays.equals(oldValue, newValue)) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * byte[].
   */
  public void preSetter(boolean intercept, int propertyIndex, byte[] oldValue, byte[] newValue) {
    if (state == STATE_NEW) {
      setLoadedProperty(propertyIndex);
    } else if (!Arrays.equals(oldValue, newValue)) {
      setChangedPropertyValue(propertyIndex, intercept, oldValue);
    }
  }

  /**
   * Explicitly set an old value with force (the old value is forced even it is already set).
   * This is used by generatedProperties
   */
  public void setOldValue(int propertyIndex, Object oldValue) {
    setChangedProperty(propertyIndex);
    setOriginalValueForce(propertyIndex, oldValue);
    setDirtyStatus();
  }

  /**
   * Return the sort order value for an order column.
   */
  public int getSortOrder() {
    return sortOrder;
  }

  /**
   * Set the sort order value for an order column.
   */
  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  /**
   * Set if the entity was deleted from a BeanCollection.
   */
  public void setDeletedFromCollection(final boolean deletedFromCollection) {
    this.deletedFromCollection = deletedFromCollection;
  }

  public boolean isOrphanDelete() {
    return deletedFromCollection && !isNew();
  }

  /**
   * Set the load error that happened on this property.
   */
  public void setLoadError(int propertyIndex, Exception t) {
    if (loadErrors == null) {
      loadErrors = new Exception[owner._ebean_getPropertyNames().length];
    }
    loadErrors[propertyIndex] = t;
    flags[propertyIndex] |= FLAG_LOADED_PROP;
  }

  /**
   * Returns the loadErrors.
   */
  public Map<String, Exception> getLoadErrors() {
    if (loadErrors == null) {
      return null;
    }
    Map<String, Exception> ret = null;
    int len = getPropertyLength();
    for (int i = 0; i < len; i++) {
      Exception loadError = loadErrors[i];
      if (loadError != null) {
        if (ret == null) {
          ret = new LinkedHashMap<>();
        }
        ret.put(getProperty(i), loadError);
      }
    }
    return ret;
  }

  public boolean isDirtyValue(int propertyIndex, Object value) {
    if (origValues == null) {
      return true;
    }
    Object oldVal = origValues[propertyIndex];
    return notEqual(oldVal, value, true);
  }
  
  /**
   * toString for debugging only.
   */
  @Override
  public String toString() {
    String[] pnames = owner._ebean_getPropertyNames();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pnames.length; i++) {
      sb.append(i).append(':').append(pnames[i]).append(":");
      if ((flags[i] & FLAG_LOADED_PROP) != 0) sb.append("Loaded,");
      if ((flags[i] & FLAG_CHANGED_PROP) != 0) sb.append("Changed,");
      if ((flags[i] & FLAG_EMBEDDED_DIRTY) != 0) sb.append("EmbeddedDirty,");
      if ((flags[i] & FLAG_ORIG_VALUE_SET) != 0) sb.append("OrigValueSet,");
      if ((flags[i] & FLAG_MUTABLE_VALUE) != 0) sb.append("MutableValue,");
      if (sb.charAt(sb.length()-1) == ',') {
        sb.setLength(sb.length() - 1);
      }
      sb.append('\n');
    }
    
    return sb.toString();
  }
}
