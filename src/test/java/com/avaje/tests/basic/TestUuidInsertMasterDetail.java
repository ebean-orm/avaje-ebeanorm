package com.avaje.tests.basic;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.UUOne;
import com.avaje.tests.model.basic.UUTwo;
import org.junit.Test;

import java.util.ArrayList;

public class TestUuidInsertMasterDetail extends BaseTestCase {

  @Test
  public void testInsert() {

    UUTwo two = new UUTwo();
    two.setName("something");

    ArrayList<UUTwo> list = new ArrayList<>();
    list.add(two);

    UUOne one = new UUOne();
    one.setName("some one");
    one.setComments(list);

    Ebean.save(one);

    UUOne oneB = Ebean.find(UUOne.class, one.getId());

    UUTwo twoB = new UUTwo();
    twoB.setName("another something");
    oneB.getComments().add(twoB);

    Ebean.save(oneB);
  }

  public void testNullFK() {

    UUTwo two = new UUTwo();
    two.setName("something");
    Ebean.save(two);
  }

}
