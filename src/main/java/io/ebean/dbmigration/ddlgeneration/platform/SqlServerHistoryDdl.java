package io.ebean.dbmigration.ddlgeneration.platform;

import io.ebean.config.ServerConfig;
import io.ebean.dbmigration.ddlgeneration.DdlBuffer;
import io.ebean.dbmigration.ddlgeneration.DdlWrite;
import io.ebean.dbmigration.migration.AddHistoryTable;
import io.ebean.dbmigration.migration.DropHistoryTable;
import io.ebean.dbmigration.model.MTable;

import java.io.IOException;

/**
 * @author Vilmos Nagy  <vilmos.nagy@outlook.com>
 */
public class SqlServerHistoryDdl implements PlatformHistoryDdl {

  private String systemPeriodStart;
  private String systemPeriodEnd;
  private PlatformDdl platformDdl;

  @Override
  public void configure(ServerConfig serverConfig, PlatformDdl platformDdl) {
    this.systemPeriodStart = serverConfig.getAsOfSysPeriod() + "From";
    this.systemPeriodEnd = serverConfig.getAsOfSysPeriod() + "To";
    this.platformDdl = platformDdl;
  }

  @Override
  public void createWithHistory(DdlWrite writer, MTable table) throws IOException {
    String baseTable = table.getName();
    enableSystemVersioning(writer, baseTable);
  }

  private void enableSystemVersioning(DdlWrite writer, String baseTable) throws IOException {
    DdlBuffer apply = writer.applyHistory();
    apply.append("alter table ").append(baseTable).newLine()
      .append("    add ").append(systemPeriodStart).append(" datetime2 GENERATED ALWAYS AS ROW START NOT NULL DEFAULT SYSUTCDATETIME(),").newLine()
      .append("        ").append(systemPeriodEnd).append("   datetime2 GENERATED ALWAYS AS ROW END   NOT NULL DEFAULT '9999-12-31T23:59:59.9999999',").newLine()
      .append("period for system_time (").append(systemPeriodStart).append(", ").append(systemPeriodEnd).append(")").endOfStatement();

    String historyTable = baseTable + "_history"; // history must contain schema, otherwise you'll get
    // Setting SYSTEM_VERSIONING to ON failed because history table 'xxx_history' is not specified in two-part name format.
    if (historyTable.indexOf('.') == -1) {
      historyTable = "dbo." +historyTable; // so add the default schema, if none was specified.
    }
    apply.append("alter table ").append(baseTable).append(" set (system_versioning = on (history_table=").append(historyTable).append("))").endOfStatement();
    
    DdlBuffer drop = writer.dropAll();
    drop.append("IF OBJECT_ID('").append(baseTable).append("', 'U') IS NOT NULL alter table ").append(baseTable).append(" set (system_versioning = off)").endOfStatement();
    drop.append("IF OBJECT_ID('").append(baseTable).append("_history', 'U') IS NOT NULL drop table ").append(baseTable).append("_history").endOfStatement();
  }

  @Override
  public void dropHistoryTable(DdlWrite writer, DropHistoryTable dropHistoryTable) throws IOException {
    String baseTable = dropHistoryTable.getBaseTable();
    DdlBuffer apply = writer.applyHistory();
    apply.append("-- dropping history support for ").append(baseTable).endOfStatement();
    // drop default constraints
    
    apply.append(platformDdl.alterColumnDefaultValue(baseTable, systemPeriodStart, DdlHelp.DROP_DEFAULT)).endOfStatement();
    apply.append(platformDdl.alterColumnDefaultValue(baseTable, systemPeriodEnd, DdlHelp.DROP_DEFAULT)).endOfStatement();
    // switch of versioning & period
    apply.append("alter table ").append(baseTable).append(" set (system_versioning = off)").endOfStatement();
    apply.append("alter table ").append(baseTable).append(" drop period for system_time").endOfStatement();
    // now drop tables & columns
    apply.append("alter table ").append(baseTable).append(" drop column ").append(systemPeriodStart).endOfStatement();
    apply.append("alter table ").append(baseTable).append(" drop column ").append(systemPeriodEnd).endOfStatement();
    apply.append("IF OBJECT_ID('").append(baseTable).append("_history', 'U') IS NOT NULL drop table ").append(baseTable).append("_history").endOfStatement();
    apply.end();
  }

  @Override
  public void addHistoryTable(DdlWrite writer, AddHistoryTable addHistoryTable) throws IOException {
    String baseTable = addHistoryTable.getBaseTable();
    enableSystemVersioning(writer, baseTable);
  }

  @Override
  public void updateTriggers(DdlWrite write, HistoryTableUpdate baseTable) throws IOException {
    // SQL Server 2016 does not need triggers
  }
}
