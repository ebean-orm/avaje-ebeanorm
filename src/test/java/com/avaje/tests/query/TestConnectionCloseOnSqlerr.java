package com.avaje.tests.query;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.tests.model.basic.Customer;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectionCloseOnSqlerr extends BaseTestCase {

  static boolean runManuallyNow = true;

  @Test
  public void test() {

    if (runManuallyNow) {
      return;
    }

    try {
      for (int i = 0; i < 100; i++) {
        try {
          Query<Customer> q0 = Ebean.find(Customer.class).where().icontains("namexxx", "Rob")
            .query();

          q0.findList();
        } catch (Exception e) {
          if (e.getMessage().contains("Unsuccessfully waited")) {
            Assert.fail("No connections found while only one thread is running. (after " + i + " queries)");
          } else {
            e.printStackTrace();
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  @Test
  public void testTransactional() {

    if (runManuallyNow) {
      return;
    }

    try {
      for (int i = 0; i < 100; i++) {
        try {
          Ebean.beginTransaction();
          Query<Customer> q0 = Ebean.find(Customer.class).where().icontains("namexxx", "Rob")
            .query();

          q0.findList();
          Ebean.commitTransaction();
        } catch (Exception e) {
          if (e.getMessage().contains("Unsuccessfully waited")) {
            Assert.fail("No connections found while only one thread is running. (after " + i + " queries)");
          } else {
            e.printStackTrace();
          }
        } finally {
          if (Ebean.currentTransaction().isActive()) {
            Ebean.rollbackTransaction();
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
