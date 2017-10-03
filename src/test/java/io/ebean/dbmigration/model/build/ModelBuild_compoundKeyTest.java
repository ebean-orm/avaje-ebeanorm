package io.ebean.dbmigration.model.build;


import io.ebean.BaseTestCase;
import io.ebean.EbeanServerFactory;
import io.ebean.config.ServerConfig;
import io.ebean.dbmigration.ddlgeneration.Helper;
import io.ebean.dbmigration.migration.Migration;
import io.ebean.dbmigration.migrationreader.MigrationXmlReader;
import io.ebean.dbmigration.model.CurrentModel;
import io.ebean.dbmigration.model.MTable;
import io.ebean.dbmigration.model.ModelContainer;
import io.ebean.plugin.SpiServer;
import org.tests.model.basic.CKeyAssoc;
import org.tests.model.basic.CKeyDetail;
import org.tests.model.basic.CKeyParent;
import org.tests.model.basic.CKeyParentId;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelBuild_compoundKeyTest extends BaseTestCase {

  private SpiServer getServer() {

    System.setProperty("ebean.ignoreExtraDdl", "true");

    ServerConfig config = new ServerConfig();
    config.setName("h2");
    config.loadFromProperties();
    config.setName("h2other");
    config.setDdlGenerate(false);
    config.setDdlRun(false);
    config.setDefaultServer(false);
    config.setRegister(false);

    config.addClass(CKeyDetail.class);
    config.addClass(CKeyParent.class);
    config.addClass(CKeyAssoc.class);
    config.addClass(CKeyParentId.class);

    return EbeanServerFactory.create(config).getPluginApi();
  }

  @Test
  public void test() throws IOException {

    SpiServer ebeanServer = getServer();

    CurrentModel currentModel = new CurrentModel(ebeanServer);
    ModelContainer model = currentModel.read();

    MTable parent = model.getTable("ckey_parent");
    MTable detail = model.getTable("ckey_detail");

    assertThat(parent).isNotNull();
    assertThat(detail).isNotNull();
    assertThat(parent.primaryKeyColumns()).hasSize(2);
    assertThat(detail.getCompoundKeys()).hasSize(1);

    String apply = Helper.asText(this, "/assert/ModelBuild_compoundKeyTest/apply.sql");

    String createDdl = currentModel.getCreateDdl();
    assertThat(createDdl).isEqualTo(apply);

  }


  @Test
  public void testFromMigration() throws IOException {


    Migration migration = MigrationXmlReader.read("/container/test-compoundkey.xml");

    SpiServer ebeanServer = getServer();
    CurrentModel currentModel = new CurrentModel(ebeanServer);
    currentModel.setChangeSet(migration.getChangeSet().get(0));

    String createDdl = currentModel.getCreateDdl();
    String apply = Helper.asText(this, "/assert/ModelBuild_compoundKeyTest/apply.sql");

    assertThat(createDdl).isEqualTo(apply);
  }
}
