package com.avaje.ebean.dbmigration.migration;

import javax.xml.bind.annotation.*;


/**
 * <p>Java class for anonymous complex type.
 * <p>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://ebean-orm.github.io/xml/ns/dbmigration}apply"/>
 *         &lt;element ref="{http://ebean-orm.github.io/xml/ns/dbmigration}rollback"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
  "apply",
  "rollback"
})
@XmlRootElement(name = "sql")
public class Sql {

  @XmlElement(required = true)
  protected Apply apply;
  @XmlElement(required = true)
  protected Rollback rollback;

  /**
   * Gets the value of the apply property.
   *
   * @return possible object is
   * {@link Apply }
   */
  public Apply getApply() {
    return apply;
  }

  /**
   * Sets the value of the apply property.
   *
   * @param value allowed object is
   *              {@link Apply }
   */
  public void setApply(Apply value) {
    this.apply = value;
  }

  /**
   * Gets the value of the rollback property.
   *
   * @return possible object is
   * {@link Rollback }
   */
  public Rollback getRollback() {
    return rollback;
  }

  /**
   * Sets the value of the rollback property.
   *
   * @param value allowed object is
   *              {@link Rollback }
   */
  public void setRollback(Rollback value) {
    this.rollback = value;
  }

}
