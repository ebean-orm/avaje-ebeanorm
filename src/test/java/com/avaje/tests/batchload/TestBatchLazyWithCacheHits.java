package com.avaje.tests.batchload;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.cache.ServerCache;
import com.avaje.ebean.cache.ServerCacheManager;
import com.avaje.ebean.cache.ServerCacheStatistics;
import com.avaje.tests.model.basic.UUOne;
import org.avaje.ebeantest.LoggedSqlCollector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestBatchLazyWithCacheHits extends BaseTestCase {

  private UUOne insert(String name) {
    UUOne one = new UUOne();
    one.setName("testBLWCH" + name);
    Ebean.save(one);
    return one;
  }

  @Test
  public void testOnCacheHit() {

    ArrayList<UUOne> inserted = new ArrayList<>();
    String[] names = "A,B,C,D,E,F,G,H,I,J".split(",");
    for (int i = 0; i < names.length; i++) {
      inserted.add(insert(names[i]));
    }

    ServerCacheManager serverCacheManager = Ebean.getDefaultServer().getServerCacheManager();
    ServerCache beanCache = serverCacheManager.getBeanCache(UUOne.class);
    beanCache.clear();

    UUOne b = Ebean.find(UUOne.class, inserted.get(1).getId());
    assertNotNull(b);

    UUOne b2 = Ebean.find(UUOne.class, inserted.get(1).getId());
    assertNotNull(b2);
    ServerCacheStatistics statistics = beanCache.getStatistics(true);
    assertEquals(statistics.getHitCount(), 1);

    UUOne c = Ebean.find(UUOne.class)
      .where().idEq(inserted.get(2).getId())
      .findUnique();
    assertNotNull(c);

    UUOne c2 = Ebean.find(UUOne.class)
      .where().idEq(inserted.get(2).getId())
      .findUnique();
    assertNotNull(c2);
    statistics = beanCache.getStatistics(true);
    assertEquals(statistics.getHitCount(), 1);

    LoggedSqlCollector.start();

    List<UUOne> list = Ebean.find(UUOne.class)
      //.setDefaultLazyLoadBatchSize(5)
      .select("id")
      .where().startsWith("name", "testBLWCH")
      .order("name")
      .findList();

    for (UUOne uuOne : list) {
      uuOne.getName();
    }
    list.get(0).getName();

    List<String> sql = LoggedSqlCollector.stop();
    System.out.println("sql:" + sql);

    // batch lazy loading into cache
    assertThat(sql).hasSize(2);
    assertThat(sql.get(0)).contains("from uuone t0 where t0.name like ?");
    assertThat(sql.get(1)).contains("from uuone t0 where t0.id in (?,");

    statistics = beanCache.getStatistics(true);
    assertThat(statistics.getSize()).isGreaterThan(3);
  }

}
