package io.ebeaninternal.server.query;

import io.ebean.databind.ScalarType;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.api.SpiTransaction;
import io.ebeaninternal.server.core.OrmQueryRequest;
import io.ebeaninternal.server.deploy.BeanDescriptor;
import io.ebeaninternal.server.type.RsetDataReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base compiled query request for single attribute queries.
 */
class CQueryFetchSingleAttribute {

  private static final Logger logger = LoggerFactory.getLogger(CQueryFetchSingleAttribute.class);

  /**
   * The overall find request wrapper object.
   */
  private final OrmQueryRequest<?> request;

  private final BeanDescriptor<?> desc;

  private final SpiQuery<?> query;

  /**
   * Where clause predicates.
   */
  private final CQueryPredicates predicates;

  /**
   * The final sql that is generated.
   */
  private final String sql;

  private RsetDataReader dataReader;

  /**
   * The statement used to create the resultSet.
   */
  private PreparedStatement pstmt;

  private String bindLog;

  private long executionTimeMicros;

  private int rowCount;

  private final ScalarType<?> scalarType;

  /**
   * Create the Sql select based on the request.
   */
  CQueryFetchSingleAttribute(OrmQueryRequest<?> request, CQueryPredicates predicates, CQueryPlan plan) {
    this.request = request;
    this.query = request.getQuery();
    this.sql = plan.getSql();
    this.desc = request.getBeanDescriptor();
    this.predicates = predicates;
    this.scalarType = plan.getSingleAttributeScalarType();

    query.setGeneratedSql(sql);
  }

  /**
   * Return a summary description of this query.
   */
  protected String getSummary() {
    StringBuilder sb = new StringBuilder(80);
    sb.append("FindAttr exeMicros[").append(executionTimeMicros)
      .append("] rows[").append(rowCount)
      .append("] type[").append(desc.getName())
      .append("] predicates[").append(predicates.getLogWhereSql())
      .append("] bind[").append(bindLog).append("]");

    return sb.toString();
  }

  /**
   * Execute the query returning the row count.
   */
  protected List<Object> findList() throws SQLException {

    long startNano = System.nanoTime();
    try {
      prepareExecute();

      List<Object> result = new ArrayList<>();
      while (dataReader.next()) {
        result.add(scalarType.read(dataReader));
        dataReader.resetColumnPosition();
        rowCount++;
      }

      executionTimeMicros = (System.nanoTime() - startNano) / 1000L;
      request.slowQueryCheck(executionTimeMicros, rowCount);
      return result;

    } finally {
      close();
    }
  }

  /**
   * Return the bind log.
   */
  protected String getBindLog() {
    return bindLog;
  }

  /**
   * Return the generated sql.
   */
  protected String getGeneratedSql() {
    return sql;
  }

  private void prepareExecute() throws SQLException {

    SpiTransaction t = request.getTransaction();
    Connection conn = t.getInternalConnection();
    pstmt = conn.prepareStatement(sql);

    if (query.getBufferFetchSizeHint() > 0) {
      pstmt.setFetchSize(query.getBufferFetchSizeHint());
    }
    if (query.getTimeout() > 0) {
      pstmt.setQueryTimeout(query.getTimeout());
    }

    bindLog = predicates.bind(pstmt, conn);
    dataReader = new RsetDataReader(request.getDataTimeZone(), pstmt.executeQuery());
  }

  /**
   * Close the resources.
   * <p>
   * The jdbc resultSet and statement need to be closed. Its important that
   * this method is called.
   * </p>
   */
  private void close() {
    try {
      if (dataReader != null) {
        dataReader.close();
        dataReader = null;
      }
    } catch (SQLException e) {
      logger.error("Error closing DataReader", e);
    }
    try {
      if (pstmt != null) {
        pstmt.close();
        pstmt = null;
      }
    } catch (SQLException e) {
      logger.error("Error closing PreparedStatement", e);
    }
  }

}
