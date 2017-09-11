package io.ebean.dbmigration.ddlgeneration;

import io.ebean.dbmigration.migration.AddColumn;
import io.ebean.dbmigration.migration.AddHistoryTable;
import io.ebean.dbmigration.migration.AddTableComment;
import io.ebean.dbmigration.migration.AddUniqueConstraint;
import io.ebean.dbmigration.migration.AlterColumn;
import io.ebean.dbmigration.migration.AlterForeignKey;
import io.ebean.dbmigration.migration.ChangeSet;
import io.ebean.dbmigration.migration.CreateIndex;
import io.ebean.dbmigration.migration.CreateTable;
import io.ebean.dbmigration.migration.DropColumn;
import io.ebean.dbmigration.migration.DropHistoryTable;
import io.ebean.dbmigration.migration.DropIndex;
import io.ebean.dbmigration.migration.DropTable;

import java.io.IOException;

/**
 * DDL generation interface.
 */
public interface DdlHandler {

  void generate(DdlWrite writer, ChangeSet changeSet) throws IOException;

  void generate(DdlWrite writer, CreateTable createTable) throws IOException;

  void generate(DdlWrite writer, DropTable dropTable) throws IOException;

  void generate(DdlWrite writer, AddTableComment addTableComment) throws IOException;

  void generate(DdlWrite writer, AddColumn addColumn) throws IOException;

  void generate(DdlWrite writer, DropColumn dropColumn) throws IOException;

  void generate(DdlWrite writer, AlterColumn alterColumn) throws IOException;

  void generate(DdlWrite writer, AddHistoryTable addHistoryTable) throws IOException;

  void generate(DdlWrite writer, DropHistoryTable dropHistoryTable) throws IOException;

  void generate(DdlWrite writer, CreateIndex createIndex) throws IOException;

  void generate(DdlWrite writer, DropIndex dropIndex) throws IOException;

  void generate(DdlWrite writer, AddUniqueConstraint constraint) throws IOException;
  
  void generate(DdlWrite writer, AlterForeignKey alterForeignKey) throws IOException;
  
  void generateExtra(DdlWrite write) throws IOException;
}
