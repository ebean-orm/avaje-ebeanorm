package io.ebeanservice.docstore.api.support;

import io.ebean.event.BeanPersistRequest;
import io.ebeanservice.docstore.api.DocStoreUpdates;

/**
 * Checks if a persist request means an embedded/nested object in another document needs updating.
 */
public class DocStoreEmbeddedInvalidation {

  protected final String queueId;

  protected final String path;

  public DocStoreEmbeddedInvalidation(String queueId, String path) {
    this.queueId = queueId;
    this.path = path;
  }

  public void embeddedInvalidate(BeanPersistRequest<?> request, DocStoreUpdates docStoreUpdates) {
    docStoreUpdates.addNested(queueId, path, request.getBeanId());
  }
}
