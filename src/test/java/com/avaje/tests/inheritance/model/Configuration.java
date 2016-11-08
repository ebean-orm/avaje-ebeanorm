package com.avaje.tests.inheritance.model;

import com.avaje.ebean.annotation.ChangeLog;

import javax.persistence.*;

@ChangeLog
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public class Configuration extends AbstractBaseClass {
  @Id
  @Column(name = "id")
  private Integer id;


  @ManyToOne
  private Configurations configurations;


  public Configuration() {
    super();
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Configurations getConfigurations() {
    return configurations;
  }

  public void setConfigurations(Configurations configurations) {
    this.configurations = configurations;
  }
}
