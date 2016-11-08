package com.avaje.tests.basic.type;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.tests.model.basic.TOne;
import org.junit.Test;

public class TestQueryBooleanProperty extends BaseTestCase {

  @Test
  public void test() {

    // when run in MySql is test for BUG 323
    Ebean.find(TOne.class).where().eq("active", true).findList();
  }
}
