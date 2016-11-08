package com.avaje.ebeaninternal.server.expression;

import com.avaje.ebeaninternal.api.*;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;

import java.io.IOException;

/**
 * Slightly redundant as Query.setId() ultimately also does the same job.
 */
class IdExpression extends NonPrepareExpression implements SpiExpression {

  private final Object value;

  IdExpression(Object value) {
    this.value = value;
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    context.writeId(value);
  }

  @Override
  public String nestedPath(BeanDescriptor<?> desc) {
    return null;
  }

  /**
   * Always returns false.
   */
  @Override
  public void containsMany(BeanDescriptor<?> desc, ManyWhereJoins manyWhereJoin) {

  }

  @Override
  public void validate(SpiExpressionValidation validation) {
    // always valid
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {

    // 'flatten' EmbeddedId and multiple Id cases
    // into an array of the underlying scalar field values
    DefaultExpressionRequest r = (DefaultExpressionRequest) request;
    Object[] bindIdValues = r.getBeanDescriptor().getBindIdValues(value);
    for (int i = 0; i < bindIdValues.length; i++) {
      request.addBindValue(bindIdValues[i]);
    }
  }

  @Override
  public void addSql(SpiExpressionRequest request) {

    DefaultExpressionRequest r = (DefaultExpressionRequest) request;
    String idSql = r.getBeanDescriptor().getIdBinderIdSql();

    request.append(idSql).append(" ");
  }

  /**
   * No properties so this is just a unique static number.
   */
  @Override
  public void queryPlanHash(HashQueryPlanBuilder builder) {
    builder.add(IdExpression.class);
    builder.bind(1);
  }

  @Override
  public int queryBindHash() {
    return value.hashCode();
  }

  @Override
  public boolean isSameByPlan(SpiExpression other) {
    return other instanceof IdExpression;
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    IdExpression that = (IdExpression) other;
    return value.equals(that.value);
  }
}
