package com.avaje.ebeaninternal.server.text.json;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.text.json.JsonContext;
import com.avaje.ebean.text.json.JsonReadBeanVisitor;
import com.avaje.ebean.text.json.JsonReadOptions;
import com.avaje.tests.model.basic.Address;
import com.avaje.tests.model.basic.Contact;
import com.avaje.tests.model.basic.Customer;
import com.avaje.tests.model.basic.ResetBasicData;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestTextJsonBeanReadVisitor extends BaseTestCase {


  @Test
  public void test() {

    ResetBasicData.reset();

    List<Customer> list = Ebean.find(Customer.class)
      .select("id, name, status, shippingAddress")
      .fetch("billingAddress", "line1, city")
      .fetch("billingAddress.country", "*")
      .fetch("contacts", "firstName,email")
      .order().desc("id")
      .findList();

    JsonContext json = Ebean.json();

    JsonReadOptions options = new JsonReadOptions();
    options.addRootVisitor(new CVisitor());
    options.addVisitor("contacts", new ContactVisitor());
    options.addVisitor("billingAddress", new AVisitor());
    options.addVisitor("shippingAddress", new ASVisitor());

    String s = json.toJson(list);

    List<Customer> mList = json.toList(Customer.class, s, options);

    StringReader reader = new StringReader(s);
    List<Customer> mList2 = json.toList(Customer.class, reader);

    assertEquals(mList.size(), mList2.size());
  }

  private static class CVisitor implements JsonReadBeanVisitor<Customer> {

    @Override
    public void visit(Customer bean, Map<String, Object> unmapped) {
      bean.getId();
    }
  }

  private static class AVisitor implements JsonReadBeanVisitor<Address> {

    public void visit(Address bean, Map<String, Object> unmapped) {
      bean.getId();
    }
  }

  private static class ASVisitor implements JsonReadBeanVisitor<Address> {

    public void visit(Address bean, Map<String, Object> unmapped) {
      bean.getId();
    }
  }

  private static class ContactVisitor implements JsonReadBeanVisitor<Contact> {

    public void visit(Contact bean, Map<String, Object> unmapped) {
      bean.getId();
    }
  }
}
