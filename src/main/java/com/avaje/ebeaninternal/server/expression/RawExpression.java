package com.avaje.ebeaninternal.server.expression;

import com.avaje.ebeaninternal.api.*;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;

import java.io.IOException;

class RawExpression extends NonPrepareExpression {

  private final String sql;

  private final Object[] values;

  RawExpression(String sql, Object[] values) {
    this.sql = sql;
    this.values = values;
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    context.writeRaw(sql, values);
  }

  @Override
  public String nestedPath(BeanDescriptor<?> desc) {
    return null;
  }

  @Override
  public void containsMany(BeanDescriptor<?> desc, ManyWhereJoins manyWhereJoin) {

  }

  @Override
  public void validate(SpiExpressionValidation validation) {
    // always ignored
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {
    if (values != null) {
      for (int i = 0; i < values.length; i++) {
        request.addBindValue(values[i]);
      }
    }
  }

  @Override
  public void addSql(SpiExpressionRequest request) {
    request.append(sql);
  }

  /**
   * Based on the sql.
   */
  @Override
  public void queryPlanHash(HashQueryPlanBuilder builder) {
    builder.add(RawExpression.class).add(sql);
  }

  @Override
  public int queryBindHash() {
    return sql.hashCode();
  }

  @Override
  public boolean isSameByPlan(SpiExpression other) {
    if (!(other instanceof RawExpression)) {
      return false;
    }
    RawExpression that = (RawExpression) other;
    return sql.equals(that.sql);
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    if (!(other instanceof RawExpression)) {
      return false;
    }

    RawExpression that = (RawExpression) other;
    if (values.length != that.values.length) {
      return false;
    }
    for (int i = 0; i < values.length; i++) {
      if (!Same.sameByValue(values[i], that.values[i])) {
        return false;
      }
    }
    return true;
  }
}
