package org.tests.model.onetoone;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;

@Entity
public class OtoBChild {

  @Id
  @Column(name = "master_id")
  Integer id;

  String child;

  @OneToOne
  @PrimaryKeyJoinColumn(name = "master_id", referencedColumnName = "id")
  OtoBMaster master;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getChild() {
    return child;
  }

  public void setChild(String child) {
    this.child = child;
  }

  public OtoBMaster getMaster() {
    return master;
  }

  public void setMaster(OtoBMaster master) {
    this.master = master;
  }

}
