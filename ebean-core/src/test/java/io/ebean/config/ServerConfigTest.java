package io.ebean.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ebean.annotation.PersistBatch;
import io.ebean.config.dbplatform.IdType;
import io.ebean.datasource.DataSourceConfig;
import org.junit.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerConfigTest {

  @Test
  public void testLoadFromEbeanProperties() {

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.loadFromProperties();

    assertEquals(PersistBatch.NONE, serverConfig.getPersistBatch());
    assertNotNull(serverConfig.getProperties());
  }

  @Test
  public void evalPropertiesInput() {

    String home = System.getenv("HOME");

    Properties props = new Properties();
    props.setProperty("ddl.initSql", "${HOME}/initSql");

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.loadFromProperties(props);

    String ddlInitSql = serverConfig.getDdlInitSql();
    assertThat(ddlInitSql).isEqualTo(home+"/initSql");
  }

  @Test
  public void testLoadWithProperties() {

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setPersistBatch(PersistBatch.NONE);
    serverConfig.setPersistBatchOnCascade(PersistBatch.NONE);
    serverConfig.setAutoReadOnlyDataSource(false);
    serverConfig.setReadOnlyDataSource(null);
    serverConfig.setReadOnlyDataSourceConfig(new DataSourceConfig());

    Properties props = new Properties();
    props.setProperty("persistBatch", "ALL");
    props.setProperty("persistBatchOnCascade", "ALL");
    props.setProperty("dbuuid", "binary");
    props.setProperty("jdbcFetchSizeFindEach", "42");
    props.setProperty("jdbcFetchSizeFindList", "43");
    props.setProperty("backgroundExecutorShutdownSecs", "98");
    props.setProperty("backgroundExecutorSchedulePoolSize", "4");
    props.setProperty("dbOffline", "true");
    props.setProperty("jsonDateTime", "MILLIS");
    props.setProperty("jsonDate", "MILLIS");
    props.setProperty("jsonDirtyByDefault", "true");
    props.setProperty("autoReadOnlyDataSource", "true");
    props.setProperty("disableL2Cache", "true");
    props.setProperty("notifyL2CacheInForeground", "true");
    props.setProperty("idType", "SEQUENCE");
    props.setProperty("mappingLocations", "classpath:/foo;bar");
    props.setProperty("namingConvention", "io.ebean.config.MatchingNamingConvention");
    props.setProperty("idGeneratorAutomatic", "true");
    props.setProperty("enabledL2Regions", "r0,users,orgs");
    props.setProperty("caseSensitiveCollation", "false");
    props.setProperty("loadModuleInfo", "true");
    props.setProperty("forUpdateNoKey", "true");
    props.setProperty("defaultServer", "false");

    props.setProperty("queryPlan.enable", "true");
    props.setProperty("queryPlan.thresholdMicros", "10000");
    props.setProperty("queryPlan.capture", "true");
    props.setProperty("queryPlan.capturePeriodSecs", "42");
    props.setProperty("queryPlan.captureMaxTimeMillis", "560");
    props.setProperty("queryPlan.captureMaxCount", "7");

    serverConfig.loadFromProperties(props);

    assertFalse(serverConfig.isDefaultServer());
    assertTrue(serverConfig.isDisableL2Cache());
    assertTrue(serverConfig.isNotifyL2CacheInForeground());
    assertTrue(serverConfig.isDbOffline());
    assertTrue(serverConfig.isAutoReadOnlyDataSource());
    assertTrue(serverConfig.isAutoLoadModuleInfo());

    assertTrue(serverConfig.isIdGeneratorAutomatic());
    assertFalse(serverConfig.getPlatformConfig().isCaseSensitiveCollation());
    assertTrue(serverConfig.getPlatformConfig().isForUpdateNoKey());

    assertThat(serverConfig.getNamingConvention()).isInstanceOf(MatchingNamingConvention.class);

    assertEquals(IdType.SEQUENCE, serverConfig.getIdType());
    assertEquals(PersistBatch.ALL, serverConfig.getPersistBatch());
    assertEquals(PersistBatch.ALL, serverConfig.getPersistBatchOnCascade());
    assertEquals(PlatformConfig.DbUuid.BINARY, serverConfig.getPlatformConfig().getDbUuid());
    assertEquals(JsonConfig.DateTime.MILLIS, serverConfig.getJsonDateTime());
    assertEquals(JsonConfig.Date.MILLIS, serverConfig.getJsonDate());
    assertTrue(serverConfig.isJsonDirtyByDefault());
    serverConfig.setJsonDirtyByDefault(false);
    assertFalse(serverConfig.isJsonDirtyByDefault());

    assertEquals("r0,users,orgs", serverConfig.getEnabledL2Regions());

    assertEquals(42, serverConfig.getJdbcFetchSizeFindEach());
    assertEquals(43, serverConfig.getJdbcFetchSizeFindList());
    assertEquals(4, serverConfig.getBackgroundExecutorSchedulePoolSize());
    assertEquals(98, serverConfig.getBackgroundExecutorShutdownSecs());

    assertTrue(serverConfig.isQueryPlanEnable());
    assertEquals(10000, serverConfig.getQueryPlanThresholdMicros());
    assertTrue(serverConfig.isQueryPlanCapture());
    assertEquals(42, serverConfig.getQueryPlanCapturePeriodSecs());
    assertEquals(560, serverConfig.getQueryPlanCaptureMaxTimeMillis());
    assertEquals(7, serverConfig.getQueryPlanCaptureMaxCount());

    assertThat(serverConfig.getMappingLocations()).containsExactly("classpath:/foo","bar");

    serverConfig.setPersistBatch(PersistBatch.NONE);
    serverConfig.setPersistBatchOnCascade(PersistBatch.NONE);

    Properties props1 = new Properties();
    props1.setProperty("ebean.persistBatch", "ALL");
    props1.setProperty("ebean.persistBatchOnCascade", "ALL");

    serverConfig.setNotifyL2CacheInForeground(true);
    serverConfig.setDisableL2Cache(true);
    props1.setProperty("ebean.disableL2Cache", "false");
    props1.setProperty("ebean.notifyL2CacheInForeground", "false");

    serverConfig.loadFromProperties(props1);
    assertFalse(serverConfig.isDisableL2Cache());
    assertFalse(serverConfig.isNotifyL2CacheInForeground());

    assertEquals(PersistBatch.ALL, serverConfig.getPersistBatch());
    assertEquals(PersistBatch.ALL, serverConfig.getPersistBatchOnCascade());

    serverConfig.setEnabledL2Regions("r0,orgs");
    assertEquals("r0,orgs", serverConfig.getEnabledL2Regions());
  }

  @Test
  public void test_defaults() {

    ServerConfig serverConfig = new ServerConfig();
    assertTrue(serverConfig.isIdGeneratorAutomatic());
    assertTrue(serverConfig.isDefaultServer());
    assertFalse(serverConfig.isAutoPersistUpdates());

    serverConfig.setIdGeneratorAutomatic(false);
    assertFalse(serverConfig.isIdGeneratorAutomatic());
    assertEquals(JsonConfig.DateTime.ISO8601, serverConfig.getJsonDateTime());
    assertEquals(JsonConfig.Date.ISO8601, serverConfig.getJsonDate());
    assertFalse(serverConfig.isJsonDirtyByDefault());
    assertTrue(serverConfig.getPlatformConfig().isCaseSensitiveCollation());
    assertTrue(serverConfig.isAutoLoadModuleInfo());

    assertFalse(serverConfig.isQueryPlanEnable());
    assertEquals(Long.MAX_VALUE, serverConfig.getQueryPlanThresholdMicros());
    assertFalse(serverConfig.isQueryPlanCapture());
    assertEquals(600, serverConfig.getQueryPlanCapturePeriodSecs());
    assertEquals(10000L, serverConfig.getQueryPlanCaptureMaxTimeMillis());
    assertEquals(10, serverConfig.getQueryPlanCaptureMaxCount());

    serverConfig.setLoadModuleInfo(false);
    assertFalse(serverConfig.isAutoLoadModuleInfo());
    serverConfig.setAutoPersistUpdates(true);
    assertTrue(serverConfig.isAutoPersistUpdates());
  }

  @Test
  public void test_putServiceObject() {

    ObjectMapper objectMapper = new ObjectMapper();

    ServerConfig config = new ServerConfig();
    config.putServiceObject(objectMapper);

    ObjectMapper mapper0 = config.getServiceObject(ObjectMapper.class);
    ObjectMapper mapper1 = (ObjectMapper)config.getServiceObject("objectMapper");

    assertThat(objectMapper).isSameAs(mapper0);
    assertThat(objectMapper).isSameAs(mapper1);
  }
}
