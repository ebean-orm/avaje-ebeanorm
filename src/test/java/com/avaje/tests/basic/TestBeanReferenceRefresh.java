package com.avaje.tests.basic;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.cache.ServerCache;
import com.avaje.tests.model.basic.Customer;
import com.avaje.tests.model.basic.Order;
import com.avaje.tests.model.basic.Order.Status;
import com.avaje.tests.model.basic.ResetBasicData;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Date;

public class TestBeanReferenceRefresh extends BaseTestCase {

  @Test
  public void testMe() {

    ResetBasicData.reset();

    ServerCache beanCache = Ebean.getServerCacheManager().getBeanCache(Order.class);
    beanCache.clear();

    Order order = Ebean.getReference(Order.class, 1);

    Assert.assertTrue(Ebean.getBeanState(order).isReference());

    // invoke lazy loading
    Date orderDate = order.getOrderDate();
    Assert.assertNotNull(orderDate);

    Customer customer = order.getCustomer();
    Assert.assertNotNull(customer);

    Assert.assertFalse(Ebean.getBeanState(order).isReference());
    Assert.assertNotNull(order.getStatus());
    Assert.assertNotNull(order.getDetails());
    Assert.assertNull(Ebean.getBeanState(order).getLoadedProps());

    Status status = order.getStatus();
    Assert.assertTrue(status != Order.Status.SHIPPED);
    order.setStatus(Order.Status.SHIPPED);
    Ebean.refresh(order);

    Status statusRefresh = order.getStatus();
    Assert.assertEquals(status, statusRefresh);

  }


}
