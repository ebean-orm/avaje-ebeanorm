package io.ebean.config.dbplatform.sqlite;

import io.ebean.annotation.Platform;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebean.config.dbplatform.DbType;
import io.ebean.config.dbplatform.IdType;
import io.ebean.dbmigration.ddlgeneration.platform.SQLiteDdl;

import java.sql.Types;

public class SQLitePlatform extends DatabasePlatform {

  public SQLitePlatform() {
    super();
    this.platform = Platform.SQLITE;
    this.platformDdl = new SQLiteDdl(this);

    this.dbIdentity.setIdType(IdType.IDENTITY);
    this.dbIdentity.setSupportsGetGeneratedKeys(false);
    this.dbIdentity.setSupportsSequence(false);
    this.dbIdentity.setSelectLastInsertedIdTemplate("select last_insert_rowid()");

    this.booleanDbType = Types.INTEGER;
    this.dbDefaultValue.setFalse("0");
    this.dbDefaultValue.setTrue("1");
    this.dbDefaultValue.setNow("CURRENT_TIMESTAMP");

    dbTypeMap.put(DbType.BIT, new DbPlatformType("int default 0"));
    dbTypeMap.put(DbType.BOOLEAN, new DbPlatformType("int default 0"));
    dbTypeMap.put(DbType.BIGINT, new DbPlatformType("integer"));
    dbTypeMap.put(DbType.SMALLINT, new DbPlatformType("integer"));

  }

}
