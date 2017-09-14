package io.ebean.config.dbplatform.oracle;

import io.ebean.BackgroundExecutor;
import io.ebean.DuplicateKeyException;
import io.ebean.Platform;
import io.ebean.Query;
import io.ebean.config.dbplatform.BasicSqlRowNumLimiter;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebean.config.dbplatform.DbType;
import io.ebean.config.dbplatform.IdType;
import io.ebean.config.dbplatform.PlatformIdGenerator;
import io.ebean.config.dbplatform.RownumSqlLimiter;
import io.ebean.config.dbplatform.SqlErrorCodes;
import io.ebean.dbmigration.ddlgeneration.platform.Oracle10Ddl;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Types;

import javax.persistence.PersistenceException;

/**
 * Oracle10 and greater specific platform.
 */
public class OraclePlatform extends DatabasePlatform {

  public OraclePlatform() {
    super();
    this.platform = Platform.ORACLE;
    this.maxTableNameLength = 30;
    this.maxConstraintNameLength = 30;
    this.dbEncrypt = new OracleDbEncrypt();
    this.sqlLimiter = new RownumSqlLimiter();
    this.basicSqlLimiter = new BasicSqlRowNumLimiter();
    this.platformDdl = new Oracle10Ddl(this);
    this.historySupport = new OracleDbHistorySupport();

    // Not using getGeneratedKeys as instead we will
    // batch load sequences which enables JDBC batch execution
    dbIdentity.setSupportsGetGeneratedKeys(false);
    dbIdentity.setIdType(IdType.SEQUENCE);
    dbIdentity.setSupportsSequence(true);

    this.dbDefaultValue.setFalse("0");
    this.dbDefaultValue.setTrue("1");
    this.dbDefaultValue.setNow("current_timestamp");

    this.treatEmptyStringsAsNull = true;

    this.likeClause = "like ? escape '|'";
    this.specialLikeCharacters = new char[] { '%', '_', '|' };
    
    this.openQuote = "\"";
    this.closeQuote = "\"";

    this.exceptionTranslator =
        new SqlErrorCodes()
          .addDataIntegrity("23000")
          .build();
    booleanDbType = Types.INTEGER;
    dbTypeMap.put(DbType.BOOLEAN, new DbPlatformType("number(1) default 0"));

    dbTypeMap.put(DbType.INTEGER, new DbPlatformType("number", 10));
    dbTypeMap.put(DbType.BIGINT, new DbPlatformType("number", 19));
    dbTypeMap.put(DbType.REAL, new DbPlatformType("number", 19, 4));
    dbTypeMap.put(DbType.DOUBLE, new DbPlatformType("number", 19, 4));
    dbTypeMap.put(DbType.SMALLINT, new DbPlatformType("number", 5));
    dbTypeMap.put(DbType.TINYINT, new DbPlatformType("number", 3));
    dbTypeMap.put(DbType.DECIMAL, new DbPlatformType("number", 38));

    dbTypeMap.put(DbType.VARCHAR, new DbPlatformType("varchar2", 255));

    dbTypeMap.put(DbType.LONGVARBINARY, new DbPlatformType("blob"));
    dbTypeMap.put(DbType.LONGVARCHAR, new DbPlatformType("clob"));
    dbTypeMap.put(DbType.VARBINARY, new DbPlatformType("raw", 255));
    dbTypeMap.put(DbType.BINARY, new DbPlatformType("raw", 255));

    dbTypeMap.put(DbType.TIME, new DbPlatformType("timestamp"));
  }

  @Override
  public PlatformIdGenerator createSequenceIdGenerator(BackgroundExecutor be, DataSource ds, String seqName, int batchSize) {

    return new OracleSequenceIdGenerator(be, ds, seqName, batchSize);
  }

  @Override
  protected String withForUpdate(String sql, Query.ForUpdate forUpdateMode) {
    switch (forUpdateMode) {
      case SKIPLOCKED:
        return sql + " for update skip locked";
      case NOWAIT:
        return sql + " for update nowait";
      default:
        return sql + " for update";
    }
  }
  
  @Override
  protected void escapeLikeCharacter(char ch, StringBuilder sb) {
    sb.append('|').append(ch);
  }
  
  @Override
  public PersistenceException translate(String message, SQLException e) {
    String cause = e.getMessage();
    if (cause != null && "23000".equals(e.getSQLState()) && cause.startsWith("ORA-00001: unique constraint ")) { 
      return new DuplicateKeyException(message, e);
    } else {
      return super.translate(message, e);
    }
  }
}
