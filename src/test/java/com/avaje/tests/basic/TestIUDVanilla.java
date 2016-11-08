package com.avaje.tests.basic;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.EBasicVer;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;

public class TestIUDVanilla extends BaseTestCase {

  @Test
  public void test() {

    EBasicVer e0 = new EBasicVer("vanilla");

    Ebean.save(e0);

    Assert.assertNotNull(e0.getId());
    Assert.assertNotNull(e0.getLastUpdate());

    Timestamp lastUpdate0 = e0.getLastUpdate();

    e0.setName("modified");
    Ebean.save(e0);

    Timestamp lastUpdate1 = e0.getLastUpdate();
    Assert.assertNotNull(lastUpdate1);
    Assert.assertNotSame(lastUpdate0, lastUpdate1);

    EBasicVer e2 = Ebean.getServer(null).createEntityBean(EBasicVer.class);

    e2.setId(e0.getId());
    e2.setLastUpdate(lastUpdate1);

    e2.setName("forcedUpdate");
    Ebean.update(e2);

    EBasicVer e3 = new EBasicVer("ModNoOCC");
    e3.setId(e0.getId());

    Ebean.update(e3);

    e3.setName("ModAgain");
    e3.setDescription("Banana");

    Ebean.update(e3);

  }
}
