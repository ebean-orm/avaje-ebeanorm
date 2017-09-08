package org.tests.query.sqlquery;

import io.ebean.BaseTestCase;
import io.ebean.Ebean;
import io.ebean.SqlQuery;
import io.ebean.SqlRow;
import org.tests.model.basic.Order;
import org.tests.model.basic.ResetBasicData;
import org.ebeantest.LoggedSqlCollector;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class SqlQueryTests extends BaseTestCase {

  @Test
  public void firstRowMaxRows() {

    ResetBasicData.reset();

    SqlQuery sqlQuery = Ebean.createSqlQuery("Select * from o_order");
    sqlQuery.setFirstRow(3);
    sqlQuery.setMaxRows(10);

    LoggedSqlCollector.start();
    List<SqlRow> list = sqlQuery.findList();

    List<String> sql = LoggedSqlCollector.stop();

    if (isSqlServer()) {
      // FIXME: we should order by primary key ALWAYS (not by first column) when no
      // explicit order is specified. In postgres this leads to strange scrolling
      // artifacts.
      assertThat(sql.get(0)).contains("order by 1 offset 3 rows fetch next 10 rows only");
    } else {
      assertThat(sql.get(0)).contains("Select * from o_order limit 10 offset 3; --bind()");
    }
    assertThat(list).isNotEmpty();
  }

  @Test
  public void firstRow() {

    if (isPostgres()) {

      ResetBasicData.reset();

      SqlQuery sqlQuery = Ebean.createSqlQuery("Select * from o_order order by id");
      sqlQuery.setFirstRow(3);

      LoggedSqlCollector.start();
      sqlQuery.findList();
      List<String> sql = LoggedSqlCollector.stop();

      assertThat(sql.get(0)).contains("Select * from o_order order by id offset 3");
    }
  }

  @Test
  public void maxRows() {

    ResetBasicData.reset();

    SqlQuery sqlQuery = Ebean.createSqlQuery("Select * from o_order order by id");
    sqlQuery.setMaxRows(10);

    LoggedSqlCollector.start();
    sqlQuery.findList();
    List<String> sql = LoggedSqlCollector.stop();

    if (isSqlServer()) {
      assertThat(sql.get(0)).contains("Select * from o_order order by id offset 0 rows fetch next 10 rows only;");
    } else {
      assertThat(sql.get(0)).contains("Select * from o_order order by id limit 10");
    }
  }

  @Test
  public void maxRows_withParam() {

    ResetBasicData.reset();

    SqlQuery sqlQuery = Ebean.createSqlQuery("select * from o_order where o_order.id > :id order by id");
    sqlQuery.setParameter("id", 3);
    sqlQuery.setMaxRows(10);

    LoggedSqlCollector.start();
    sqlQuery.findList();
    List<String> sql = LoggedSqlCollector.stop();

    if (isSqlServer()) {
      assertThat(sql.get(0)).contains("select * from o_order where o_order.id > ? order by id offset 0 rows fetch next 10 rows only;");
    } else {
      assertThat(sql.get(0)).contains("select * from o_order where o_order.id > ? order by id limit 10;");
    }
  }

  @Test
  public void findEachMaxRows() {

    ResetBasicData.reset();

    SqlQuery sqlQuery = Ebean.createSqlQuery("Select * from o_order");
    sqlQuery.setMaxRows(10);

    LoggedSqlCollector.start();
    sqlQuery.findEach(bean -> bean.get("id"));
    List<String> sql = LoggedSqlCollector.stop();

    if (isSqlServer()) {
      assertThat(sql.get(0)).contains("offset 0 rows fetch next 10 rows only");
    } else {
      assertThat(sql.get(0)).contains("limit 10");
    }
  }

  @Test
  public void findEach() {

    ResetBasicData.reset();

    int expectedRows = Ebean.find(Order.class).findCount();

    final AtomicInteger count = new AtomicInteger();

    SqlQuery sqlQuery = Ebean.createSqlQuery("select * from o_order");
    sqlQuery.findEach(bean -> count.incrementAndGet());

    assertEquals(expectedRows, count.get());
  }

  @Test
  public void findEachWhile() {

    ResetBasicData.reset();

    final AtomicInteger count = new AtomicInteger();

    SqlQuery sqlQuery = Ebean.createSqlQuery("select * from o_order order by id");
    sqlQuery.findEachWhile(bean -> {
      count.incrementAndGet();
      Integer id = bean.getInteger("id");
      return id < 3;
    });

    assertEquals(3, count.get());
  }

}
