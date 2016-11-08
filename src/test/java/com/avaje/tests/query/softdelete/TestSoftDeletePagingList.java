package com.avaje.tests.query.softdelete;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.PagedList;
import com.avaje.tests.model.onetoone.album.Cover;
import org.avaje.ebeantest.LoggedSqlCollector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSoftDeletePagingList extends BaseTestCase {

  @Test
  public void test() {

    List<Cover> list = new ArrayList<>();
    list.add(new Cover("SoftDelPaged-1"));
    list.add(new Cover("SoftDelPaged-2"));
    list.add(new Cover("SoftDelPaged-3"));

    Ebean.saveAll(list);
    Ebean.delete(list.get(1));

    LoggedSqlCollector.start();

    PagedList<Cover> pagedList = Ebean.find(Cover.class)
      .where().startsWith("s3Url", "SoftDelPaged-")
      .setMaxRows(10)
      .findPagedList();

    int totalRowCount = pagedList.getTotalCount();
    List<Cover> resultList = pagedList.getList();

    List<String> sql = LoggedSqlCollector.stop();

    assertThat(resultList).hasSize(2);
    assertThat(totalRowCount).isEqualTo(2);

    assertThat(sql).hasSize(2);
    assertThat(sql.get(0)).contains("select count(*) from cover t0 where t0.s3url like");
    if (isPlatformBooleanNative()) {
      assertThat(sql.get(0)).contains("and coalesce(t0.deleted,false)=false; --bind(SoftDelPaged-%)");
    } else {
      assertThat(sql.get(0)).contains("and coalesce(t0.deleted,0)=0; --bind(SoftDelPaged-%)");
    }

    assertThat(sql.get(1)).contains("where t0.s3url like ");
    if (isPlatformBooleanNative()) {
      assertThat(sql.get(1)).contains("and coalesce(t0.deleted,false)=false order by t0.id");
    } else {
      assertThat(sql.get(1)).contains("and coalesce(t0.deleted,0)=0 order by t0.id");
    }
  }
}
