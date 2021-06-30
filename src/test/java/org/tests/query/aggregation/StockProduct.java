package org.tests.query.aggregation;

import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.Formula;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class StockProduct extends Model {
  @Id
  private int id;
  @ManyToOne
  private Product product;
  private String stock;
  private int quantity;
  private int quantityReserved;
  @Formula(select = "quantity - quantity_reserved")
  private int availableQuantity;
  @Formula(
    select = "${ta}.quantity * total_price_product.price",
    join = "left join product as total_price_product on total_price_product.id = ${ta}.product_id"
  )
  private int totalPrice;

  public static final Finder<Integer, StockProduct> find = new Finder<>(StockProduct.class);

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public Product getProduct() {
    return product;
  }

  public void setProduct(Product product) {
    this.product = product;
  }

  public String getStock() {
    return stock;
  }

  public void setStock(String stock) {
    this.stock = stock;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public int getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(int totalPrice) {
    this.totalPrice = totalPrice;
  }

  public int getQuantityReserved() {
    return quantityReserved;
  }

  public void setQuantityReserved(int quantityReserved) {
    this.quantityReserved = quantityReserved;
  }

  public int getAvailableQuantity() {
    return availableQuantity;
  }

  public void setAvailableQuantity(int availableQuantity) {
    this.availableQuantity = availableQuantity;
  }
}

