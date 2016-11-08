package com.avaje.tests.query;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.FetchConfig;
import com.avaje.ebean.Query;
import com.avaje.tests.model.basic.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestQueryFilterManyOnSecondary extends BaseTestCase {

  @Test
  public void testFilterManyWithSimplePredicate() {

    ResetBasicData.reset();

    Query<Customer> query = Ebean.find(Customer.class)
      .fetch("orders", new FetchConfig().query())
      .where().ilike("name", "Rob%").gt("id", 0)
      .filterMany("orders").eq("status", Order.Status.NEW)
      .query();

    List<Customer> list = query.findList();
    for (Customer customer : list) {
      List<Order> orders = customer.getOrders();
      for (Order order : orders) {
        Assert.assertEquals(Order.Status.NEW, order.getStatus());
      }
    }

  }


  @Test
  public void testFilterManyWithPathPredicate() {

    ResetBasicData.reset();

    Query<Order> query = Ebean.find(Order.class)
      .fetch("details", new FetchConfig().query())
      .fetch("details.product", "name")
      .filterMany("details").ilike("product.name", "c%")
      .query();

    List<Order> orders = query.findList();
    for (Order order : orders) {
      List<OrderDetail> details = order.getDetails();
      for (OrderDetail orderDetail : details) {
        Product product = orderDetail.getProduct();
        String name = product.getName();
        Assert.assertTrue(name.startsWith("C"));
      }
    }
  }
}
