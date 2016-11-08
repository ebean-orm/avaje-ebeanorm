package com.avaje.tests.text.json;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.BeanState;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.bean.EntityBean;
import com.avaje.ebean.text.json.JsonContext;
import com.avaje.ebean.text.json.JsonWriteOptions;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.tests.model.basic.Customer;
import com.avaje.tests.model.basic.Order;
import com.avaje.tests.model.basic.Product;
import com.avaje.tests.model.basic.ResetBasicData;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class TestTextJsonReferenceBean extends BaseTestCase {

  @Test
  public void test() throws IOException {

    ResetBasicData.reset();

    SpiEbeanServer server = (SpiEbeanServer) Ebean.getServer(null);

    JsonContext jsonContext = Ebean.json();

    Product product = Ebean.getReference(Product.class, 1);

    BeanState beanState0 = Ebean.getBeanState(product);
    if (!beanState0.isReference()) {
      // got a cached value from beanCache

    } else {

      String jsonString = jsonContext.toJson(product);

      Product refProd = jsonContext.toBean(Product.class, jsonString);

      BeanDescriptor<Product> prodDesc = server.getBeanDescriptor(Product.class);
      EntityBean eb = (EntityBean) refProd;
      prodDesc.isReference(eb._ebean_getIntercept());

      BeanState beanState = Ebean.getBeanState(refProd);
      Assert.assertTrue(beanState.isNew());

      String name = refProd.getName();
      Assert.assertNull(name);

      // Set to be 'loaded' to invoke lazy loading
      beanState.setLoaded();
      String name2 = refProd.getName();
      Assert.assertNotNull(name2);

    }

    List<Order> orders = Ebean.find(Order.class)
      // .setUseCache(false)
      .select("status, orderDate, shipDate, customer").fetch("details", "*")
      // .fetch("details.product","id")
      .order().asc("id").findList();

    Order order = orders.get(0);

    JsonWriteOptions options = JsonWriteOptions.parsePath("*,details(id,orderQty,product(id))");

    String jsonOrder = jsonContext.toJson(order, options);

    Order o2 = jsonContext.toBean(Order.class, jsonOrder);
    Customer customer = o2.getCustomer();

    BeanDescriptor<Customer> custDesc = server.getBeanDescriptor(Customer.class);

    Assert.assertTrue(custDesc.isReference(((EntityBean) customer)._ebean_getIntercept()));


  }

}
