package org.tests.json;

import io.ebean.BaseTestCase;
import io.ebean.DB;
import io.ebean.annotation.ForPlatform;
import io.ebean.annotation.Platform;
import io.ebean.text.TextException;
import org.ebeantest.LoggedSqlCollector;
import org.junit.Test;
import org.tests.model.json.EBasicJsonList;
import org.tests.model.json.PlainBean;

import javax.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestDbJson_List extends BaseTestCase {

  private final EBasicJsonList bean = new EBasicJsonList();

  private EBasicJsonList found;

  @Test
  public void insert() {

    bean.setName("stuff");

    bean.getTags().add("one");
    bean.getTags().add("two");

    bean.getFlags().add(42L);
    bean.getFlags().add(43L);
    bean.getFlags().add(44L);

    bean.setPlainBean(new PlainBean("plain", 52));

    List<PlainBean> beanList = new ArrayList<>();
    beanList.add(new PlainBean("one", 1));
    beanList.add(new PlainBean("two", 2));
    bean.setBeanList(beanList);


    Set<PlainBean> beanSet = new LinkedHashSet<>();
    beanSet.add(new PlainBean("A", 1));
    beanSet.add(new PlainBean("B", 2));
    bean.setBeanSet(beanSet);

    bean.getBeanMap().put("key0", new PlainBean("k0", 90));
    bean.getBeanMap().put("key1", new PlainBean("k1", 91));

    DB.save(bean);

    found = DB.find(EBasicJsonList.class, bean.getId());

    assertThat(found.getTags()).containsExactly("one", "two");
    assertTrue(found.getFlags().contains(42L));
    assertTrue(found.getFlags().contains(43L));
    assertTrue(found.getFlags().contains(44L));
    assertThat(found.getBeanList()).hasSize(2);
    assertThat(found.getBeanSet()).hasSize(2);
    assertThat(found.getBeanMap()).hasSize(2);
    assertThat(found.getPlainBean().getName()).isEqualTo("plain");
    assertThat(found.getPlainBean().getAlong()).isEqualTo(52);

    json_parse_format();
    update_when_notDirty();
    update_when_dirty();
    update_when_dirty_flags();
    update_when_dirty_SetListMap();
  }

  //@Test//(dependsOnMethods = "insert")
  public void json_parse_format() {

    String asJson = DB.json().toJson(found);
    assertThat(asJson).contains("\"tags\":[\"one\",\"two\"]");
    assertThat(asJson).contains("\"flags\":[42,43,44]");
    assertThat(asJson).contains("\"plainBean\":{\"name\":\"plain\"");
    assertThat(asJson).contains("\"beanSet\":[");
    assertThat(asJson).contains("\"beanList\":[");
    assertThat(asJson).contains("\"beanMap\":{");
    assertThat(asJson).contains("\"id\":");

    EBasicJsonList fromJson = DB.json().toBean(EBasicJsonList.class, asJson);
    assertEquals(found.getId(), fromJson.getId());
    assertEquals(found.getId(), fromJson.getId());
    assertEquals(found.getName(), fromJson.getName());
    assertThat(fromJson.getTags()).containsExactly("one", "two");
    assertTrue(fromJson.getFlags().contains(42L));
    assertTrue(fromJson.getFlags().contains(43L));
    assertTrue(fromJson.getFlags().contains(44L));

    assertThat(fromJson.getBeanSet()).hasSize(2);
    assertThat(fromJson.getBeanList()).hasSize(2);
    assertThat(fromJson.getBeanMap()).hasSize(2);
  }

  //@Test//(dependsOnMethods = "insert")
  public void update_when_notDirty() {

    found.setName("mod");
    LoggedSqlCollector.start();
    DB.save(found);
    List<String> sql = LoggedSqlCollector.stop();

    // we don't update the phone numbers (as they are not dirty)
    assertSql(sql.get(0)).contains("update ebasic_json_list set name=?, version=? where");
  }

  public void update_when_dirty() {

    //found.setName("modAgain");
    found.getTags().add("three");

    LoggedSqlCollector.start();
    DB.save(found);
    List<String> sql = LoggedSqlCollector.stop();

    // we don't update the phone numbers (as they are not dirty)
    assertSql(sql.get(0)).contains("update ebasic_json_list set tags=?, version=? where id=? and version=?");
  }

  public void update_when_dirty_flags() {

    //found.setName("modAgain");
    found.getFlags().remove(42L);

    LoggedSqlCollector.start();
    DB.save(found);
    List<String> sql = LoggedSqlCollector.stop();

    // we don't update the phone numbers (as they are not dirty)
    assertSql(sql.get(0)).contains("update ebasic_json_list set flags=?, version=? where id=? and version=?;");
  }

  public void update_when_dirty_SetListMap() {

    //found.setName("modAgain");
    found.getBeanSet().clear();
    found.getBeanList().clear();
    found.getBeanMap().remove("key0");

    LoggedSqlCollector.start();
    DB.save(found);
    List<String> sql = LoggedSqlCollector.stop();

    // we don't update the phone numbers (as they are not dirty)
    assertSql(sql.get(0)).contains("update ebasic_json_list set beans=?, bean_list=?, bean_map=?, version=? where id=? and version=?");
  }

  @Test
  public void insert_fetch_when_null() {

    EBasicJsonList bean = new EBasicJsonList();
    bean.setName("leave some nulls");
    bean.setFlags(null);
    bean.setTags(null);
    bean.setBeanMap(null);

    DB.save(bean);

    EBasicJsonList found = DB.find(EBasicJsonList.class, bean.getId());

    assertNull(found.getPlainBean());

    String asJson = DB.json().toJson(found);
    assertNotNull(asJson);
  }

  @ForPlatform(Platform.H2)
  @Test
  public void find_corrupt_json_using_setAllowLoadErrors() {

    EBasicJsonList bean = new EBasicJsonList();

    PlainBean plainBean = new PlainBean();
    plainBean.setName("Blubb");
    bean.getBeanMap().put("bla", plainBean);

    DB.save(bean);

    // set some invalid JSON content into DB
    DB.sqlUpdate("update ebasic_json_list set bean_map=? where id=?")
      .setParameters("blabla", bean.getId())
      .execute();

    try {
      // a normal query fails due to invalid JSON content
      DB.find(EBasicJsonList.class)
        .setId(bean.getId())
        .findOne();

      // never get here
      assertTrue(false);

    } catch (PersistenceException e) {
      // query fails due to error loading invalid JSON content
      assertThat(e.getMessage()).contains("beanMap");
    }

    bean = DB.find(EBasicJsonList.class)
      .setId(bean.getId())
      .setAllowLoadErrors() // allow invalid JSON content
      .findOne();

    Map<String, Exception> errors = server().getBeanState(bean).getLoadErrors();

    assertThat(errors).containsKey("beanMap").hasSize(1);
    assertThat(errors.values().iterator().next())
      .isInstanceOf(TextException.class)
      .hasMessageContaining("blabla");

    DB.delete(bean);
  }
}
