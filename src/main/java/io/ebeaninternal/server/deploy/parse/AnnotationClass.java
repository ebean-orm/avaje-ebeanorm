package io.ebeaninternal.server.deploy.parse;

import io.ebean.annotation.Cache;
import io.ebean.annotation.DbComment;
import io.ebean.annotation.DocStore;
import io.ebean.annotation.Draftable;
import io.ebean.annotation.DraftableElement;
import io.ebean.annotation.History;
import io.ebean.annotation.Index;
import io.ebean.annotation.ReadAudit;
import io.ebean.annotation.UpdateMode;
import io.ebean.annotation.View;
import io.ebean.config.TableName;
import io.ebeaninternal.server.deploy.BeanDescriptor.EntityType;
import io.ebeaninternal.server.deploy.IndexDefinition;
import io.ebeaninternal.server.deploy.InheritInfo;
import io.ebeaninternal.server.deploy.meta.DeployBeanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Read the class level deployment annotations.
 */
public class AnnotationClass extends AnnotationParser {

  private static final Logger logger = LoggerFactory.getLogger(AnnotationClass.class);

  private final String asOfViewSuffix;

  private final String versionsBetweenSuffix;

  private final boolean disableL2Cache;

  /**
   * Create for normal early parse of class level annotations.
   */
  public AnnotationClass(DeployBeanInfo<?> info, boolean validationAnnotations, String asOfViewSuffix, String versionsBetweenSuffix, boolean disableL2Cache) {
    super(info, validationAnnotations);
    this.asOfViewSuffix = asOfViewSuffix;
    this.versionsBetweenSuffix = versionsBetweenSuffix;
    this.disableL2Cache = disableL2Cache;
  }

  /**
   * Create to parse AttributeOverride annotations which is run last
   * after all the properties/fields have been parsed fully.
   */
  public AnnotationClass(DeployBeanInfo<?> info) {
    super(info, false);
    this.asOfViewSuffix = null;
    this.versionsBetweenSuffix = null;
    this.disableL2Cache = false;
  }

  /**
   * Parse any AttributeOverride set on the class.
   */
  public void parseAttributeOverride() {

    Class<?> cls = descriptor.getBeanType();
    AttributeOverride override = AnnotationBase.findAnnotation(cls,AttributeOverride.class);
    if (override != null) {
      String propertyName = override.name();
      Column column = override.column();
      DeployBeanProperty beanProperty = descriptor.getBeanProperty(propertyName);
      if (beanProperty == null) {
        logger.error("AttributeOverride property [" + propertyName + "] not found on " + descriptor.getFullName());
      } else {
        readColumn(column, beanProperty);
      }
    }
  }

  /**
   * Read the class level deployment annotations.
   */
  @Override
  public void parse() {
    read(descriptor.getBeanType());
    setTableName();
  }

  /**
   * Set the table name if it has not already been set.
   */
  private void setTableName() {

    if (descriptor.isBaseTableType()) {
      Class<?> beanType = descriptor.getBeanType();
      InheritInfo inheritInfo = descriptor.getInheritInfo();
      if (inheritInfo != null) {
        beanType = inheritInfo.getRoot().getType();
      }
      // default the TableName using NamingConvention.
      TableName tableName = namingConvention.getTableName(beanType);
      descriptor.setBaseTable(tableName, asOfViewSuffix, versionsBetweenSuffix);
    }
  }

  private void read(Class<?> cls) {

    // maybe doc store only so check for this before @Entity
    DocStore docStore = AnnotationBase.findAnnotation(cls,DocStore.class);
    if (docStore != null) {
      descriptor.readDocStore(docStore);
      descriptor.setEntityType(EntityType.DOC);
      descriptor.setName(cls.getSimpleName());
    }

    Entity entity = AnnotationBase.findAnnotation(cls,Entity.class);
    if (entity != null) {
      descriptor.setEntityType(EntityType.ORM);
      if (entity.name().isEmpty()) {
        descriptor.setName(cls.getSimpleName());
      } else {
        descriptor.setName(entity.name());
      }
    }

    Embeddable embeddable = AnnotationBase.findAnnotation(cls,Embeddable.class);
    if (embeddable != null) {
      descriptor.setEntityType(EntityType.EMBEDDED);
      descriptor.setName("Embeddable:" + cls.getSimpleName());
    }

    Set<Index> indices = AnnotationBase.findAnnotations(cls, Index.class);
    for (Index index: indices) {
      descriptor.addIndex(new IndexDefinition(index.columnNames(), index.name(), index.unique()));
    }

    UniqueConstraint uc = AnnotationBase.findAnnotation(cls,UniqueConstraint.class);
    if (uc != null) {
      descriptor.addIndex(new IndexDefinition(uc.columnNames()));
    }

    View view = AnnotationBase.findAnnotation(cls,View.class);
    if (view != null) {
      descriptor.setView(view.name(), view.dependentTables());
    }
    Table table = AnnotationBase.findAnnotation(cls,Table.class);
    if (table != null) {
      UniqueConstraint[] uniqueConstraints = table.uniqueConstraints();
      for (UniqueConstraint c : uniqueConstraints) {
        descriptor.addIndex(new IndexDefinition(c.columnNames()));
      }
    }

    Draftable draftable = AnnotationBase.findAnnotation(cls,Draftable.class);
    if (draftable != null) {
      descriptor.setDraftable();
    }

    DraftableElement draftableElement = AnnotationBase.findAnnotation(cls,DraftableElement.class);
    if (draftableElement != null) {
      descriptor.setDraftableElement();
    }

    ReadAudit readAudit = AnnotationBase.findAnnotation(cls,ReadAudit.class);
    if (readAudit != null) {
      descriptor.setReadAuditing();
    }

    History history = AnnotationBase.findAnnotation(cls,History.class);
    if (history != null) {
      descriptor.setHistorySupport();
    }

    DbComment comment = AnnotationBase.findAnnotation(cls,DbComment.class);
    if (comment != null) {
      descriptor.setDbComment(comment.value());
    }

    UpdateMode updateMode = AnnotationBase.findAnnotation(cls,UpdateMode.class);
    if (updateMode != null) {
      descriptor.setUpdateChangesOnly(updateMode.updateChangesOnly());
    }

    Cache cache = AnnotationBase.findAnnotation(cls,Cache.class);
    if (cache != null && !disableL2Cache) {
      descriptor.setCache(cache);
    }

    Set<NamedQuery> namedQueries = AnnotationBase.findAnnotations(cls,NamedQuery.class);
    for (NamedQuery namedQuery : namedQueries) {
      descriptor.addNamedQuery(namedQuery.name(), namedQuery.query());
    }
  }

}
