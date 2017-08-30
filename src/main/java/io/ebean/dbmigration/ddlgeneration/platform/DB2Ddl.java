package io.ebean.dbmigration.ddlgeneration.platform;

import io.ebean.config.dbplatform.DatabasePlatform;

/**
 * DB2 platform specific DDL.
 */
public class DB2Ddl extends PlatformDdl {

  public DB2Ddl(DatabasePlatform platform) {
    super(platform);
    this.dropTableIfExists = "drop table ";
    this.dropSequenceIfExists = "drop sequence ";
    this.dropConstraintIfExists = "drop constraint";
    this.dropIndexIfExists = "drop index ";
    this.identitySuffix = " generated by default as identity";
  }

}
