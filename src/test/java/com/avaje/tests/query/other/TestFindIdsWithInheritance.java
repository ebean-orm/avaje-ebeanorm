package com.avaje.tests.query.other;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.Truck;
import com.avaje.tests.model.basic.Vehicle;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestFindIdsWithInheritance extends BaseTestCase {

  @Test
  public void testQuery() {

    Truck truck = new Truck();
    truck.setLicenseNumber("TK123");

    Ebean.save(truck);

    List<Integer> ids = Ebean.find(Vehicle.class).findIds();
    Assert.assertNotNull(ids);

    Ebean.delete(truck);

  }

}
