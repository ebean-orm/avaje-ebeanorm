package io.ebean.dbmigration.ddlgeneration.platform;

import io.ebean.Ebean;
import io.ebean.config.dbplatform.h2.H2Platform;
import io.ebean.dbmigration.ddlgeneration.DdlWrite;
import io.ebean.dbmigration.model.CurrentModel;
import io.ebean.dbmigration.model.MConfiguration;
import io.ebean.dbmigration.model.ModelContainer;
import io.ebean.plugin.SpiServer;
import org.junit.Test;

import static org.assertj.core.api.StrictAssertions.assertThat;


public class H2HistoryDdlTest {

  @Test
  public void testRegenerateHistoryTriggers() throws Exception {

    SpiServer ebeanServer = Ebean.getDefaultServer().getPluginApi();

    HistoryTableUpdate update = new HistoryTableUpdate("c_user");
    update.add(HistoryTableUpdate.Change.ADD, "one");
    update.add(HistoryTableUpdate.Change.DROP, "two");


    CurrentModel currentModel = new CurrentModel(ebeanServer);
    ModelContainer modelContainer = currentModel.read();
    DdlWrite write = new DdlWrite(new MConfiguration(), modelContainer);

    H2Platform h2Platform = new H2Platform();
    PlatformDdl h2Ddl = h2Platform.getPlatformDdl();
    h2Ddl.configure(ebeanServer.getServerConfig());
    h2Ddl.regenerateHistoryTriggers(write, update);

    assertThat(write.applyHistory().isEmpty()).isFalse();
    assertThat(write.applyHistory().getBuffer()).contains("add one");
    assertThat(write.dropAll().isEmpty()).isTrue();

  }
}
