package org.tests.basic;

import io.ebean.BaseTestCase;
import io.ebean.Ebean;
import io.ebean.FutureIds;
import io.ebean.Query;
import org.tests.model.basic.Order;
import org.tests.model.basic.ResetBasicData;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class TestFetchId extends BaseTestCase {

  @Test
  public void testFetchId() throws InterruptedException, ExecutionException {

    ResetBasicData.reset();

    Query<Order> query = Ebean.find(Order.class)
      .setAutoTune(false)
      .fetch("details")
      .where().gt("id", 1)
      .gt("details.id", 0)
      .orderBy("orderDate")
      .query();

    List<Object> ids = query.findIds();
    assertThat(ids).isNotEmpty();

    FutureIds<Order> futureIds = query.findFutureIds();

    // wait for all the id's to be fetched
    List<Object> idList = futureIds.get();
    assertThat(idList).isNotEmpty();
  }
}
