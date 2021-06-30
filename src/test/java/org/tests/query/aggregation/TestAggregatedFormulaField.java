package org.tests.query.aggregation;

import io.ebean.BaseTestCase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestAggregatedFormulaField extends BaseTestCase {
  private final static String stock = "stock1";
  private static StockProduct stockProduct1;
  private static StockProduct stockProduct2;

  @BeforeClass
  public static void setup() {
    Product product1 = new Product();
    product1.setName("product1");
    product1.setPrice(1);
    product1.save();

    Product product2 = new Product();
    product2.setName("product2");
    product2.setPrice(2);
    product2.save();

    stockProduct1 = new StockProduct();
    stockProduct1.setStock(stock);
    stockProduct1.setQuantity(1);
    stockProduct1.setProduct(product1);
    stockProduct1.save();
    stockProduct1.refresh();

    stockProduct2 = new StockProduct();
    stockProduct2.setStock(stock);
    stockProduct2.setQuantity(2);
    stockProduct2.setQuantityReserved(1);
    stockProduct2.setProduct(product2);
    stockProduct2.save();
    stockProduct2.refresh();
  }

  @Test
  public void aggregateFormulaField() {
    int totalQuantity = stockProduct1.getQuantity() + stockProduct2.getQuantity();
    int reservedQuantity = stockProduct1.getQuantityReserved() + stockProduct2.getQuantityReserved();
    Object availableQuantitySum = StockProduct.find.query()
      .select("SUM(availableQuantity)")
      .where().eq("stock", stock)
      .findSingleAttribute();
    Assert.assertEquals(totalQuantity - reservedQuantity, availableQuantitySum);
  }

  @Test
  public void verifyFormulaFieldWithJoin() {
    Assert.assertEquals(stockProduct1.getQuantity() * stockProduct1.getProduct().getPrice(), stockProduct1.getTotalPrice());
    Assert.assertEquals(stockProduct2.getQuantity() * stockProduct2.getProduct().getPrice(), stockProduct2.getTotalPrice());
  }

  @Test
  public void aggregateFormulaFieldWithJoin() {
    int stockValue = stockProduct1.getTotalPrice() + stockProduct2.getTotalPrice();
    Object totalPriceSum = StockProduct.find.query()
      .select("SUM(totalPrice)")
      .where().eq("stock", stock)
      .findSingleAttribute();
    Assert.assertEquals(stockValue, totalPriceSum);
  }
}
