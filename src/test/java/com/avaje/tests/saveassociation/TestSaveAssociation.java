package com.avaje.tests.saveassociation;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.TSDetail;
import com.avaje.tests.model.basic.TSMaster;
import org.junit.Assert;
import org.junit.Test;

public class TestSaveAssociation extends BaseTestCase {

  @Test
  public void test() {

    TSMaster m0 = new TSMaster();
    m0.setName("master1");

    Ebean.save(m0);

    m0.addDetail(new TSDetail("master1 detail1"));
    m0.addDetail(new TSDetail("master1 detail2"));

    Ebean.save(m0);

    TSMaster m0Check = Ebean.find(TSMaster.class).fetch("details").where().idEq(m0.getId())
      .findUnique();

    Assert.assertEquals(2, m0Check.getDetails().size());

  }

}
