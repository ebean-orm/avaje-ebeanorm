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
 *       &lt;attribute name="baseTable" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "addHistoryTable")
public class AddHistoryTable {

  @XmlAttribute(name = "baseTable", required = true)
  protected String baseTable;

  /**
   * Gets the value of the baseTable property.
   *
   * @return possible object is
   * {@link String }
   */
  public String getBaseTable() {
    return baseTable;
  }

  /**
   * Sets the value of the baseTable property.
   *
   * @param value allowed object is
   *              {@link String }
   */
  public void setBaseTable(String value) {
    this.baseTable = value;
  }

}
