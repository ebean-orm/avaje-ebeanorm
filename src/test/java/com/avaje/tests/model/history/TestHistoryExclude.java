package com.avaje.tests.model.history;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import org.junit.Test;

import java.sql.Timestamp;

import static org.assertj.core.api.StrictAssertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class TestHistoryExclude extends BaseTestCase {

  private HeLink link;

  private void prepare() {
    if (link == null) {
      HeDoc docA = new HeDoc("doca");
      HeDoc docB = new HeDoc("docb");
      docA.save();
      docB.save();

      link = new HeLink("some", "link");
      link.getDocs().add(docA);
      link.getDocs().add(docB);
      link.save();
    }
  }

  @Test
  public void testLazyLoad() {

    prepare();

    HeLink linkFound = Ebean.find(HeLink.class, link.getId());
    linkFound.getDocs().size();
  }

  @Test
  public void testAsOfThenLazy() {

    assumeFalse("Skipping test because history not yet supported for MS SQL Server.",
        isMsSqlServer());
    
    prepare();

    HeLink linkFound = Ebean.find(HeLink.class)
      .asOf(new Timestamp(System.currentTimeMillis()))
      .setId(link.getId())
      .findUnique();

    assertThat(linkFound.getDocs().size()).isEqualTo(2);
  }
}
