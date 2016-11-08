package com.avaje.tests.basic;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.tests.model.basic.Order;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.StrictAssertions.assertThat;
import static org.junit.Assert.assertEquals;

public class TestInEmpty extends BaseTestCase {

  @Test
  public void test_in_empty() {

    Query<Order> query = Ebean.find(Order.class).where().in("id", new Object[0]).gt("id", 0)
      .query();

    List<Order> list = query.findList();
    assertThat(query.getGeneratedSql()).contains("1=0");
    assertEquals(0, list.size());
  }

  @Test
  public void test_notIn_empty() {

    Query<Order> query = Ebean.find(Order.class).where().notIn("id", new Object[0]).gt("id", 0)
      .query();

    query.findList();
    assertThat(query.getGeneratedSql()).contains("1=1");
  }

}
