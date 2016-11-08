package com.avaje.tests.query;


import com.avaje.ebean.*;
import com.avaje.tests.model.basic.Customer;
import com.avaje.tests.model.basic.ResetBasicData;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestIContains extends BaseTestCase {

  @Test
  public void testIContains() {

    ResetBasicData.reset();

    // case insensitive
    Query<Customer> query = Ebean.find(Customer.class).where().icontains("name", "Rob").query();

    query.findList();
    String generatedSql = query.getGeneratedSql();

    assertThat(generatedSql).contains("lower(t0.name)");

    // case sensitive
    query = Ebean.find(Customer.class).where().contains("name", "Rob").query();

    query.findList();
    generatedSql = query.getGeneratedSql();

    assertThat(generatedSql).contains(" t0.name ");

    Ebean.find(Customer.class).where().icontains("name", "Rob").findList();
    Ebean.find(Customer.class).where().icontains("name", "Rob").findList();
    Ebean.find(Customer.class).where().icontains("name", "Rob").findList();

    String sql = "select id, status, name from o_customer where lower(name) like :name";
    RawSql parse = RawSqlBuilder.parse(sql).create();

    Ebean.find(Customer.class).setRawSql(parse).setParameter("name", "Jim").findList();
  }

}
