package io.ebean.dbmigration.ddlgeneration;

import io.ebean.config.ServerConfig;
import io.ebean.dbmigration.ddlgeneration.platform.BaseTableDdl;
import io.ebean.dbmigration.ddlgeneration.platform.PlatformDdl;
import io.ebean.dbmigration.migration.AddColumn;
import io.ebean.dbmigration.migration.AddHistoryTable;
import io.ebean.dbmigration.migration.AddTableComment;
import io.ebean.dbmigration.migration.AlterColumn;
import io.ebean.dbmigration.migration.ChangeSet;
import io.ebean.dbmigration.migration.CreateIndex;
import io.ebean.dbmigration.migration.CreateTable;
import io.ebean.dbmigration.migration.DropColumn;
import io.ebean.dbmigration.migration.DropHistoryTable;
import io.ebean.dbmigration.migration.DropIndex;
import io.ebean.dbmigration.migration.DropTable;

import java.io.IOException;
import java.util.List;

/**
 *
 */
public class BaseDdlHandler implements DdlHandler {

  protected final TableDdl tableDdl;

  public BaseDdlHandler(ServerConfig serverConfig, PlatformDdl platformDdl) {
    this.tableDdl = new BaseTableDdl(serverConfig, platformDdl);
  }

  @Override
  public void generate(DdlWrite writer, ChangeSet changeSet) throws IOException {

    List<Object> changeSetChildren = changeSet.getChangeSetChildren();
    for (Object change : changeSetChildren) {
      if (change instanceof CreateTable) {
        generate(writer, (CreateTable) change);
      }
    }
    for (Object change : changeSetChildren) {
      if (change instanceof CreateTable) {
        // ignore
      } else if (change instanceof DropTable) {
        generate(writer, (DropTable) change);
      } else if (change instanceof AddTableComment) {
        generate(writer, (AddTableComment) change);
      } else if (change instanceof CreateIndex) {
        generate(writer, (CreateIndex) change);
      } else if (change instanceof DropIndex) {
        generate(writer, (DropIndex) change);
      } else if (change instanceof AddColumn) {
        generate(writer, (AddColumn) change);
      } else if (change instanceof DropColumn) {
        generate(writer, (DropColumn) change);
      } else if (change instanceof AlterColumn) {
        generate(writer, (AlterColumn) change);
      } else if (change instanceof AddHistoryTable) {
        generate(writer, (AddHistoryTable) change);
      } else if (change instanceof DropHistoryTable) {
        generate(writer, (DropHistoryTable) change);
      } else {
        throw new IllegalArgumentException("Unsupported change: " + change);
      }
    }
  }

  @Override
  public void generateExtra(DdlWrite write) throws IOException {
    tableDdl.generateExtra(write);
  }

  @Override
  public void generate(DdlWrite writer, CreateTable createTable) throws IOException {
    tableDdl.generate(writer, createTable);
  }

  @Override
  public void generate(DdlWrite writer, DropTable dropTable) throws IOException {
    tableDdl.generate(writer, dropTable);
  }

  @Override
  public void generate(DdlWrite writer, AddTableComment addTableComment) throws IOException {
    tableDdl.generate(writer, addTableComment);
  }

  @Override
  public void generate(DdlWrite writer, AddColumn addColumn) throws IOException {
    tableDdl.generate(writer, addColumn);
  }

  @Override
  public void generate(DdlWrite writer, DropColumn dropColumn) throws IOException {
    tableDdl.generate(writer, dropColumn);
  }

  @Override
  public void generate(DdlWrite writer, AlterColumn alterColumn) throws IOException {
    tableDdl.generate(writer, alterColumn);
  }

  @Override
  public void generate(DdlWrite writer, AddHistoryTable addHistoryTable) throws IOException {
    tableDdl.generate(writer, addHistoryTable);
  }

  @Override
  public void generate(DdlWrite writer, DropHistoryTable dropHistoryTable) throws IOException {
    tableDdl.generate(writer, dropHistoryTable);
  }

  @Override
  public void generate(DdlWrite writer, CreateIndex createIndex) throws IOException {
    tableDdl.generate(writer, createIndex);
  }

  @Override
  public void generate(DdlWrite writer, DropIndex dropIndex) throws IOException {
    tableDdl.generate(writer, dropIndex);
  }

}
