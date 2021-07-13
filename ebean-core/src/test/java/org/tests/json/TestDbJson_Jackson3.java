package org.tests.json;

import io.ebean.BaseTestCase;
import io.ebean.BeanState;
import io.ebean.DB;
import io.ebeantest.LoggedSql;
import org.junit.Test;
import org.tests.model.json.EBasicJsonJackson3;
import org.tests.model.json.EBasicJsonList;
import org.tests.model.json.PlainBean;
import org.tests.model.json.PlainBeanDirtyAware;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDbJson_Jackson3 extends BaseTestCase {

  @Test
  public void updateIncludesJsonColumn_when_explicit_isMarkedDirty() {

    PlainBeanDirtyAware contentBean = new PlainBeanDirtyAware("a", 42);

    EBasicJsonJackson3 bean = new EBasicJsonJackson3();
    bean.setName("b1");
    bean.setPlainValue(contentBean);

    bean.save();

    final EBasicJsonJackson3 found = DB.find(EBasicJsonJackson3.class, bean.getId());
    found.setName("b1-mod");

    LoggedSql.start();
    found.save();

    List<String> sql = LoggedSql.collect();
    assertThat(sql).hasSize(1);
    assertThat(sql.get(0)).contains("update ebasic_json_jackson3 set name=?, version=? where id=? and version=?");

    found.setName("b1-mod2");
    found.getPlainValue().setName("b");
    found.getPlainValue().setMarkedDirty(true);

    //assertThat(DB.getBeanState(found).getDirtyValues()).isEmpty();
    found.save();

    sql = LoggedSql.stop();
    assertThat(sql).hasSize(1);
    assertThat(sql.get(0)).contains("update ebasic_json_jackson3 set name=?, plain_value=?, version=? where id=? and version=?");

    final EBasicJsonJackson3 found2 = DB.find(EBasicJsonJackson3.class, bean.getId());

    assertThat(found2.getName()).isEqualTo("b1-mod2");
    assertThat(found2.getPlainValue().getName()).isEqualTo("b");
  }

  @Test
  public void updateIncludesJsonColumn_when_loadedAndNotDirtyAware() {

    PlainBean contentBean = new PlainBean("a", 42);
    EBasicJsonList bean = new EBasicJsonList();
    bean.setName("p1");
    bean.setPlainBean(contentBean);
    bean.setBeanList(Arrays.asList(contentBean));

    DB.save(bean);
    
    EBasicJsonList found = DB.find(EBasicJsonList.class, bean.getId());
    // json bean not modified but not aware
    // ideally don't load the json content if we are not going to modify it
    found.setName("p1-mod");

    assertThat(DB.getBeanState(found).getDirtyValues().toString()).isEqualTo("{name=p1-mod,p1}");
    LoggedSql.start();
    DB.save(found);

    List<String> sql = LoggedSql.stop();
    assertThat(sql).hasSize(1);
    assertThat(sql.get(0)).contains("update ebasic_json_list set name=?, version=? where id=?");

    // reload again and modify the plainBean
    found = DB.find(EBasicJsonList.class, bean.getId());
    found.getPlainBean().setName("b");

    assertThat(DB.getBeanState(found).getDirtyValues().toString()).isEqualTo("{plainBean=name:b,name:a}");
    
    LoggedSql.start();
    DB.save(found);

    sql = LoggedSql.stop();
    assertThat(sql).hasSize(1);
    assertThat(sql.get(0)).contains("update ebasic_json_list set plain_bean=?, version=? where id=?");
    
    // if we do not reload, subsequent saves will save only new dirty properties.
    found.getBeanList().add(contentBean);
    assertThat(DB.getBeanState(found).getDirtyValues().toString()).isEqualTo("{beanList=[name:a, name:a],[name:a]}");
    LoggedSql.start();
    DB.save(found);

    sql = LoggedSql.stop();
    assertThat(sql).hasSize(1);
    assertThat(sql.get(0)).contains("update ebasic_json_list set bean_list=?, version=? where id=?");

  }
}
