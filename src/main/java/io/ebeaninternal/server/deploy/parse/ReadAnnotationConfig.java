package io.ebeaninternal.server.deploy.parse;

import io.ebean.annotation.Aggregation;
import io.ebean.annotation.Formula;
import io.ebean.annotation.Where;
import io.ebean.config.DatabaseConfig;
import io.ebeaninternal.server.deploy.generatedproperty.GeneratedPropertyFactory;

import javax.persistence.Column;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration used when reading the deployment annotations.
 */
class ReadAnnotationConfig {

  private final GeneratedPropertyFactory generatedPropFactory;
  private final String asOfViewSuffix;
  private final String versionsBetweenSuffix;
  private final boolean disableL2Cache;
  private final boolean eagerFetchLobs;
  private final boolean javaxValidationAnnotations;
  private final boolean jacksonAnnotations;
  private final boolean idGeneratorAutomatic;

  private final Set<Class<?>> metaAnnotations = new HashSet<>();

  ReadAnnotationConfig(GeneratedPropertyFactory generatedPropFactory, String asOfViewSuffix, String versionsBetweenSuffix, DatabaseConfig config) {
    this.generatedPropFactory = generatedPropFactory;
    this.asOfViewSuffix = asOfViewSuffix;
    this.versionsBetweenSuffix = versionsBetweenSuffix;
    this.disableL2Cache = config.isDisableL2Cache();
    this.eagerFetchLobs = config.isEagerFetchLobs();
    this.idGeneratorAutomatic = config.isIdGeneratorAutomatic();
    this.javaxValidationAnnotations = generatedPropFactory.getClassLoadConfig().isJavaxValidationAnnotationsPresent();
    this.jacksonAnnotations = generatedPropFactory.getClassLoadConfig().isJacksonAnnotationsPresent();
    this.metaAnnotations.add(Column.class);
    this.metaAnnotations.add(Formula.class);
    this.metaAnnotations.add(Formula.List.class);
    this.metaAnnotations.add(Where.class);
    this.metaAnnotations.add(Where.List.class);
    this.metaAnnotations.add(Aggregation.class);
  }

  public void addMetaAnnotation(Class<?> annotation) {
    metaAnnotations.add(annotation);
  }

  GeneratedPropertyFactory getGeneratedPropFactory() {
    return generatedPropFactory;
  }

  String getAsOfViewSuffix() {
    return asOfViewSuffix;
  }

  String getVersionsBetweenSuffix() {
    return versionsBetweenSuffix;
  }

  boolean isDisableL2Cache() {
    return disableL2Cache;
  }

  boolean isEagerFetchLobs() {
    return eagerFetchLobs;
  }

  boolean isIdGeneratorAutomatic() {
    return idGeneratorAutomatic;
  }

  boolean isJavaxValidationAnnotations() {
    return javaxValidationAnnotations;
  }

  boolean isJacksonAnnotations() {
    return jacksonAnnotations;
  }

  public Set<Class<?>> getMetaAnnotations() {
    return metaAnnotations;
  }
}
