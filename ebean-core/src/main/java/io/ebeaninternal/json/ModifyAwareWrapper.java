package io.ebeaninternal.json;

import io.ebean.ModifyAwareType;

/**
 * Wrapper interface for ModifyAwareList/Set/Map.
 */
public interface ModifyAwareWrapper extends ModifyAwareType {

  Object unwrap();

}
