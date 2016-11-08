package com.avaje.ebeanservice.docstore.api.support;

import com.avaje.ebean.FetchPath;
import com.avaje.ebean.Query;
import com.avaje.ebean.annotation.DocStore;
import com.avaje.ebean.annotation.DocStoreMode;
import com.avaje.ebean.plugin.BeanType;
import com.avaje.ebean.text.PathProperties;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.core.PersistRequest;
import com.avaje.ebeaninternal.server.core.PersistRequestBean;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.deploy.BeanProperty;
import com.avaje.ebeaninternal.server.deploy.InheritInfo;
import com.avaje.ebeaninternal.server.deploy.meta.DeployBeanDescriptor;
import com.avaje.ebeanservice.docstore.api.DocStoreBeanAdapter;
import com.avaje.ebeanservice.docstore.api.DocStoreUpdateContext;
import com.avaje.ebeanservice.docstore.api.DocStoreUpdates;
import com.avaje.ebeanservice.docstore.api.mapping.DocMappingBuilder;
import com.avaje.ebeanservice.docstore.api.mapping.DocumentMapping;

import java.io.IOException;
import java.util.*;

/**
 * Base implementation for much of DocStoreBeanAdapter.
 */
public abstract class DocStoreBeanBaseAdapter<T> implements DocStoreBeanAdapter<T> {

  protected final SpiEbeanServer server;

  /**
   * The associated BeanDescriptor.
   */
  protected final BeanDescriptor<T> desc;

  /**
   * The type of index.
   */
  protected final boolean mapped;

  /**
   * Identifier used in the queue system to identify the index.
   */
  protected final String queueId;

  /**
   * ElasticSearch index type.
   */
  protected final String indexType;

  /**
   * ElasticSearch index name.
   */
  protected final String indexName;

  /**
   * Doc store deployment annotation.
   */
  private final DocStore docStore;

  /**
   * Behavior on insert.
   */
  protected final DocStoreMode insert;

  /**
   * Behavior on update.
   */
  protected DocStoreMode update;

  /**
   * Behavior on delete.
   */
  protected final DocStoreMode delete;

  /**
   * List of embedded paths from other documents that include this document type.
   * As such an update to this doc type means that those embedded documents need to be updated.
   */
  protected final List<DocStoreEmbeddedInvalidation> embeddedInvalidation = new ArrayList<>();

  protected final PathProperties pathProps;

  /**
   * Map of properties to 'raw' properties.
   */
  protected Map<String, String> sortableMap;

  /**
   * Nested path properties defining the doc structure for indexing.
   */
  protected DocStructure docStructure;

  protected DocumentMapping documentMapping;

  private boolean registerPaths;

  public DocStoreBeanBaseAdapter(BeanDescriptor<T> desc, DeployBeanDescriptor<T> deploy) {

    this.desc = desc;
    this.server = desc.getEbeanServer();
    this.mapped = deploy.isDocStoreMapped();
    this.pathProps = deploy.getDocStorePathProperties();
    this.docStore = deploy.getDocStore();
    this.queueId = derive(desc, deploy.getDocStoreQueueId());
    this.indexName = derive(desc, deploy.getDocStoreIndexName());
    this.indexType = derive(desc, deploy.getDocStoreIndexType());
    this.insert = deploy.getDocStoreInsertEvent();
    this.update = deploy.getDocStoreUpdateEvent();
    this.delete = deploy.getDocStoreDeleteEvent();
  }

  @Override
  public boolean hasEmbeddedInvalidation() {
    return !embeddedInvalidation.isEmpty();
  }

  @Override
  public DocumentMapping createDocMapping() {

    if (documentMapping != null) {
      return documentMapping;
    }

    if (!mapped) return null;

    this.docStructure = derivePathProperties(pathProps);

    DocMappingBuilder mappingBuilder = new DocMappingBuilder(docStructure.doc(), docStore);
    desc.docStoreMapping(mappingBuilder, null);
    mappingBuilder.applyMapping();

    sortableMap = mappingBuilder.collectSortable();
    docStructure.prepareMany(desc);
    documentMapping = mappingBuilder.create(queueId, indexName, indexType);
    return documentMapping;
  }

  @Override
  public String getIndexType() {
    return indexType;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public void applyPath(Query<T> query) {
    query.apply(docStructure.doc());
  }

  @Override
  public String rawProperty(String property) {

    String rawProperty = sortableMap.get(property);
    return rawProperty == null ? property : rawProperty;
  }

  /**
   * Register invalidation paths for embedded documents.
   */
  @Override
  public void registerPaths() {
    if (mapped && !registerPaths) {
      Collection<PathProperties.Props> pathProps = docStructure.doc().getPathProps();
      for (PathProperties.Props pathProp : pathProps) {
        String path = pathProp.getPath();
        if (path != null) {
          BeanDescriptor<?> targetDesc = desc.getBeanDescriptor(path);
          String idName = targetDesc.getIdProperty().getName();
          String fullPath = path + "." + idName;
          targetDesc.docStoreAdapter().registerInvalidationPath(desc.getDocStoreQueueId(), fullPath, pathProp.getProperties());
        }
      }
      registerPaths = true;
    }
  }

  /**
   * Register a doc store invalidation listener for the given bean type, path and properties.
   */
  @Override
  public void registerInvalidationPath(String queueId, String path, Set<String> properties) {

    if (!mapped) {
      if (update == DocStoreMode.IGNORE) {
        // bean type not mapped but is included as nested document
        // in a doc store index so we need to update
        update = DocStoreMode.UPDATE;
      }
    }
    embeddedInvalidation.add(getEmbeddedInvalidation(queueId, path, properties));
  }

  /**
   * Return the DsInvalidationListener based on the properties, path.
   */
  protected DocStoreEmbeddedInvalidation getEmbeddedInvalidation(String queueId, String path, Set<String> properties) {

    if (properties.contains("*")) {
      return new DocStoreEmbeddedInvalidation(queueId, path);
    } else {
      return new DocStoreEmbeddedInvalidationProperties(queueId, path, getPropertyPositions(properties));
    }
  }

  /**
   * Return the property names as property index positions.
   */
  protected int[] getPropertyPositions(Set<String> properties) {
    List<Integer> posList = new ArrayList<>();
    for (String property : properties) {
      BeanProperty prop = desc.getBeanProperty(property);
      if (prop != null) {
        posList.add(prop.getPropertyIndex());
      }
    }
    int[] pos = new int[posList.size()];
    for (int i = 0; i < pos.length; i++) {
      pos[i] = posList.get(i);
    }
    return pos;
  }

  @Override
  public void updateEmbedded(PersistRequestBean<T> request, DocStoreUpdates docStoreUpdates) {
    for (int i = 0; i < embeddedInvalidation.size(); i++) {
      embeddedInvalidation.get(i).embeddedInvalidate(request, docStoreUpdates);
    }
  }

  /**
   * Return the pathProperties which defines the JSON document to index.
   * This can add derived/embedded/nested parts to the document.
   */
  protected DocStructure derivePathProperties(PathProperties pathProps) {

    boolean includeByDefault = (pathProps == null);
    if (pathProps == null) {
      pathProps = new PathProperties();
    }

    return getDocStructure(pathProps, includeByDefault);
  }

  protected DocStructure getDocStructure(PathProperties pathProps, final boolean includeByDefault) {

    final DocStructure docStructure = new DocStructure(pathProps);

    BeanProperty[] properties = desc.propertiesNonTransient();
    for (int i = 0; i < properties.length; i++) {
      properties[i].docStoreInclude(includeByDefault, docStructure);
    }

    InheritInfo inheritInfo = desc.getInheritInfo();
    if (inheritInfo != null) {
      inheritInfo.visitChildren(inheritInfo1 -> {
        for (BeanProperty localProperty : inheritInfo1.localProperties()) {
          localProperty.docStoreInclude(includeByDefault, docStructure);
        }
      });
    }

    return docStructure;
  }

  public FetchPath getEmbedded(String path) {
    return docStructure.getEmbedded(path);
  }

  public FetchPath getEmbeddedManyRoot(String path) {
    return docStructure.getEmbeddedManyRoot(path);
  }

  @Override
  public boolean isMapped() {
    return mapped;
  }

  @Override
  public String getQueueId() {
    return queueId;
  }

  @Override
  public DocStoreMode getMode(PersistRequest.Type persistType, DocStoreMode txnMode) {

    if (txnMode == null) {
      return getMode(persistType);
    } else if (txnMode == DocStoreMode.IGNORE) {
      return DocStoreMode.IGNORE;
    }
    return mapped ? txnMode : getMode(persistType);
  }

  private DocStoreMode getMode(PersistRequest.Type persistType) {
    switch (persistType) {
      case INSERT:
        return insert;
      case UPDATE:
        return update;
      case DELETE:
        return delete;
      default:
        return DocStoreMode.IGNORE;
    }
  }

  /**
   * Return the supplied value or default to the bean name lower case.
   */
  protected String derive(BeanType<?> desc, String suppliedValue) {
    return (suppliedValue != null && !suppliedValue.isEmpty()) ? suppliedValue : desc.getName().toLowerCase();
  }

  @Override
  public abstract void deleteById(Object idValue, DocStoreUpdateContext txn) throws IOException;

  @Override
  public abstract void index(Object idValue, T entityBean, DocStoreUpdateContext txn) throws IOException;

  @Override
  public abstract void insert(Object idValue, PersistRequestBean<T> persistRequest, DocStoreUpdateContext txn) throws IOException;

  @Override
  public abstract void update(Object idValue, PersistRequestBean<T> persistRequest, DocStoreUpdateContext txn) throws IOException;

  @Override
  public abstract void updateEmbedded(Object idValue, String embeddedProperty, String embeddedRawContent, DocStoreUpdateContext txn) throws IOException;

}
