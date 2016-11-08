package com.avaje.tests.query;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.Contact;
import com.avaje.tests.model.basic.Order;
import com.avaje.tests.model.basic.ResetBasicData;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestQueryJoinMulipleMany extends BaseTestCase {

  @Test
  public void test() {

    ResetBasicData.reset();

    List<Order> list = Ebean.find(Order.class).fetch("customer").fetch("customer.contacts").where()
      .gt("id", 0).findList();

    Assert.assertNotNull(list);

    for (Order order : list) {
      List<Contact> contacts = order.getCustomer().getContacts();
      contacts.size();
    }

  }
}
