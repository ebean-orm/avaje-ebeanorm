package com.avaje.tests.query.lazy;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.*;
import org.junit.Test;

import java.util.List;

public class TestQueryDefaultBatchSize extends BaseTestCase {

  @Test
  public void test_findEach() {

    ResetBasicData.reset();

    Ebean.find(Order.class)
      .setLazyLoadBatchSize(2)
      .findEach(bean -> doStuff(bean));
  }

  @Test
  public void test_findEach_withFetch() {

    ResetBasicData.reset();

    Ebean.find(Order.class)
      .fetch("details", "id")
      .fetch("details.product", "sku")
      .fetch("customer")
      .fetch("customer.contacts")
      .setLazyLoadBatchSize(2)
      .findEach(bean -> doStuff(bean));
  }

  @Test
  public void test_findList() {

    ResetBasicData.reset();

    List<Order> orders =
      Ebean.find(Order.class)
        .setLazyLoadBatchSize(2)
        .findList();

    for (Order order : orders) {
      doStuff(order);
    }
  }

  @Test
  public void test_findList_withFetch() {

    ResetBasicData.reset();

    List<Order> orders =
      Ebean.find(Order.class)
        .fetch("details", "id")
        .fetch("details.product", "sku")
        .fetch("customer")
        .fetch("customer.contacts")
        .setLazyLoadBatchSize(2)
        .findList();

    for (Order order : orders) {
      doStuff(order);
    }
  }

  @Test
  public void test_findList_lazyMany() {

    ResetBasicData.reset();

    List<Order> orders = Ebean.find(Order.class)
      .setLazyLoadBatchSize(100)
      .findList();

    for (Order order : orders) {
      List<OrderDetail> details = order.getDetails();
      details.size();
      for (OrderDetail detail : details) {
        Product product = detail.getProduct();
        product.getSku();
      }
    }
  }

  private void doStuff(Order order) {
    // invoke lazy loading
    Customer customer = order.getCustomer();
    order.getId();
    customer.getName();
    customer.getContacts().size();
    order.getDetails().size();
  }
}
