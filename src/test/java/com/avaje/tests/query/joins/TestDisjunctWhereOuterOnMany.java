package com.avaje.tests.query.joins;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.tests.model.basic.UUOne;
import com.avaje.tests.model.basic.UUTwo;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestDisjunctWhereOuterOnMany extends BaseTestCase {

  @Test
  public void test() {

    Ebean.deleteAll(Ebean.find(UUTwo.class).select("id").findList());
    Ebean.deleteAll(Ebean.find(UUOne.class).select("id").findList());

    // setup
    UUOne master1 = new UUOne();
    master1.setName("testDisjOuter_1_name");

    UUTwo detail1 = new UUTwo();
    detail1.setMaster(master1);
    detail1.setName("testDisjOuter_CHILD_1");

    UUOne master2 = new UUOne();
    master2.setName("testDisjOuter_2_name");

    Ebean.save(master1);
    Ebean.save(detail1);
    Ebean.save(master2);


    // Have outer join so that "testDisjOuter_2_name" is found
    Query<UUOne> query = Ebean.find(UUOne.class)
      .where().disjunction()
      .eq("name", "testDisjOuter_2_name")
      .eq("comments.name", "testDisjOuter_CHILD_1")
      .endJunction()
      .query();

    List<UUOne> list = query.findList();
    int rowCount = query.findCount();

    // select distinct t0.id c0, t0.name c1
    // from uuone t0
    // join uutwo u1 on u1.master_id = t0.id
    // left join uutwo t1 on t1.master_id = t0.id
    // where (t0.name = ?  or u1.name = ? ) ;
    // --bind(testDisjOuter_2_name,testDisjOuter_CHILD_1)

    Assert.assertEquals(2, list.size());
    Assert.assertEquals(2, rowCount);

    String expectedSql = "select distinct t0.id, t0.name from uuone t0 left join uutwo u1 on u1.master_id = t0.id  where (t0.name = ?  or u1.name = ? ) ";
    Assert.assertEquals(expectedSql, sqlOf(query, 1));

  }

}
