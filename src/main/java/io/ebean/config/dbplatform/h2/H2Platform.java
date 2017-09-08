package io.ebean.config.dbplatform.h2;

import io.ebean.BackgroundExecutor;
import io.ebean.Platform;
import io.ebean.Query;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebean.config.dbplatform.DbType;
import io.ebean.config.dbplatform.IdType;
import io.ebean.config.dbplatform.PlatformIdGenerator;
import io.ebean.config.dbplatform.SqlErrorCodes;
import io.ebean.dbmigration.ddlgeneration.platform.H2Ddl;

import javax.sql.DataSource;

/**
 * H2 specific platform.
 */
public class H2Platform extends DatabasePlatform {

  public H2Platform() {
    super();
    this.platform = Platform.H2;
    this.dbEncrypt = new H2DbEncrypt();
    this.platformDdl = new H2Ddl(this);
    this.historySupport = new H2HistorySupport();
    this.nativeUuidType = true;
    this.dbDefaultValue.setNow("now()");
    this.columnAliasPrefix = null;

    this.exceptionTranslator =
      new SqlErrorCodes()
        .addAcquireLock("50200","HYT00")
        .addDuplicateKey("23001","23505")
        .addDataIntegrity("22001","22003","22012","22018","22025","23000","23002","23003","23502","23503","23506","23507","23513")
        .build();

    this.dbIdentity.setIdType(IdType.IDENTITY);
    this.dbIdentity.setSupportsGetGeneratedKeys(true);
    this.dbIdentity.setSupportsSequence(true);
    this.dbIdentity.setSupportsIdentity(true);

    dbTypeMap.put(DbType.UUID, new DbPlatformType("uuid", false));
  }

  /**
   * Return a H2 specific sequence IdGenerator that supports batch fetching
   * sequence values.
   */
  @Override
  public PlatformIdGenerator createSequenceIdGenerator(BackgroundExecutor be, DataSource ds,
                                                       String seqName, int batchSize) {

    return new H2SequenceIdGenerator(be, ds, seqName, batchSize);
  }

  @Override
  protected String withForUpdate(String sql, Query.ForUpdate forUpdateMode) {
    // NOWAIT and SKIP LOCKED currently not supported with H2
    return sql + " for update";
  }
}
