package io.ebeaninternal.server.type;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.ebean.ModifyAwareType;
import io.ebean.config.DatabaseConfig;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebeaninternal.json.ModifyAwareWrapper;

class TypeJsonManager {

  interface DirtyHandler {
    boolean isDirty(Object value, ObjectWriter writer);

    void track(Object value, String json);
  }

  private final boolean postgres;
  private final ObjectMapper objectMapper;
  private final DirtyHandler defaultHandler;
  private final DirtyHandler modifyAwareHandler;

  TypeJsonManager(boolean postgres, Object objectMapper, boolean defaultDirty) {
    this.postgres = postgres;
    this.objectMapper = (ObjectMapper) objectMapper;
    // TODO: make this configurable!
    this.defaultHandler = new ReferenceTrackingHandler(); // DefaultHandler(defaultDirty);
    this.modifyAwareHandler = defaultHandler; // new ModifyAwareHandler();
  }

  ObjectMapper objectMapper() {
    return objectMapper;
  }

  String postgresType(int dbType) {
    if (postgres) {
      switch (dbType) {
        case DbPlatformType.JSON:
          return PostgresHelper.JSON_TYPE;
        case DbPlatformType.JSONB:
          return PostgresHelper.JSONB_TYPE;
      }
    }
    return null;
  }

  /**
   * Return the DirtyHandler to use.
   */
  DirtyHandler dirtyHandler(Class<?> cls, Class<?> rawType) {
    if (!Object.class.equals(cls) || ModifyAwareType.class.isAssignableFrom(rawType)) {
      // Set, List and Map are modify aware
      return modifyAwareHandler;
    }
    return defaultHandler;
  }

  /**
   * Return true if the value should be considered dirty (and included in an update).
   */
  static boolean checkIsDirty(Object value) {
    if (value instanceof ModifyAwareType) {
      return checkModifyAware(value);
    }
    return true;
  }

  private static boolean checkModifyAware(Object value) {
    ModifyAwareType modifyAware = (ModifyAwareType) value;
    if (modifyAware.isMarkedDirty()) {
      // reset the dirty state (consider not dirty after update)
      modifyAware.setMarkedDirty(false);
      return true;
    } else {
      return false;
    }
  }

  static final class ModifyAwareHandler implements DirtyHandler {
    @Override
    public boolean isDirty(Object value, ObjectWriter writer) {
      return checkModifyAware(value);
    }
    
    @Override
    public void track(Object value, String json) {}
  }

  /**
   * Effectively constant based on {@link DatabaseConfig#isJsonDirtyByDefault()}
   */
  static final class DefaultHandler implements DirtyHandler {

    private final boolean dirty;

    DefaultHandler(boolean dirty) {
      this.dirty = dirty;
    }

    @Override
    public boolean isDirty(Object value, ObjectWriter writer) {
      return dirty;
    }
    
    @Override
    public void track(Object value, String json) {}
  }
  
  private static class TrackReference extends WeakReference<Object> {

    private final int hashCode;

    public TrackReference(Object referent) {
      super(referent);
      hashCode = System.identityHashCode(referent);
    }
    
    public TrackReference(Object referent, ReferenceQueue<Object> q) {
      super(referent, q);
      hashCode = System.identityHashCode(referent);
    }
    
    @Override
    public int hashCode() {
      return hashCode;
    }
    @Override
    public boolean equals(Object other) {
      return other instanceof TrackReference && this.get() == ((TrackReference)other).get();
    }
  }
  
  /**
   * Effectively constant based on {@link DatabaseConfig#isJsonDirtyByDefault()}
   */
  static final class ReferenceTrackingHandler implements DirtyHandler {

    private Map<TrackReference, String> originalJson = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    

    @Override
    public boolean isDirty(Object value, ObjectWriter writer) {
      if (value == null) {
        return true;
      }
      if (value instanceof ModifyAwareWrapper) {
        if (((ModifyAwareWrapper) value).isMarkedDirty()) {
          return true;
        }
        value = ((ModifyAwareWrapper) value).unwrap();
      }
      TrackReference key = new TrackReference(value);
      String json = originalJson.get(key);
      if (json == null) {
        return true;
      }
      try {
        return !json.equals(writer.writeValueAsString(value));
      } catch (IOException e) {
        return true; // Checkme: what to do
      }
    }
    
    
    public void track(Object obj, String json) {
      if (obj != null) {
        TrackReference ref = new TrackReference(obj, queue);
        originalJson.put(ref, json);

        while (( ref = (TrackReference) queue.poll()) != null) {
          originalJson.remove(ref);
        }
      }
    }
  }

}
