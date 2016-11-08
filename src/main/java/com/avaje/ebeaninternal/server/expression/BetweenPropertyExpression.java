package com.avaje.ebeaninternal.server.expression;

import com.avaje.ebeaninternal.api.*;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.el.ElPropertyDeploy;
import com.avaje.ebeaninternal.server.query.SplitName;

import java.io.IOException;

/**
 * Between expression where a value is between two properties.
 */
class BetweenPropertyExpression extends NonPrepareExpression {

  private static final String BETWEEN = " between ";

  private final String lowProperty;
  private final String highProperty;
  private final Object value;

  BetweenPropertyExpression(String lowProperty, String highProperty, Object value) {
    this.lowProperty = lowProperty;
    this.highProperty = highProperty;
    this.value = value;
  }

  protected String name(String propName) {
    return propName;
  }

  private Object val() {
    return NamedParamHelp.value(value);
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    context.startBoolMust();
    context.writeSimple(Op.LT_EQ, lowProperty, val());
    context.writeSimple(Op.GT_EQ, highProperty, val());
    context.endBool();
  }

  @Override
  public String nestedPath(BeanDescriptor<?> desc) {
    ElPropertyDeploy elProp = desc.getElPropertyDeploy(name(lowProperty));
    if (elProp != null && elProp.containsMany()) {
      // assumes highProperty is also nested property which seems reasonable
      return SplitName.begin(lowProperty);
    }
    return null;
  }

  @Override
  public void containsMany(BeanDescriptor<?> desc, ManyWhereJoins manyWhereJoin) {

    ElPropertyDeploy elProp = desc.getElPropertyDeploy(name(lowProperty));
    if (elProp != null && elProp.containsMany()) {
      manyWhereJoin.add(elProp);
    }

    elProp = desc.getElPropertyDeploy(name(highProperty));
    if (elProp != null && elProp.containsMany()) {
      manyWhereJoin.add(elProp);
    }
  }

  @Override
  public void validate(SpiExpressionValidation validation) {
    validation.validate(lowProperty);
    validation.validate(highProperty);
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {
    request.addBindValue(val());
  }

  @Override
  public void addSql(SpiExpressionRequest request) {

    request.append(" ? ").append(BETWEEN).append(name(lowProperty)).append(" and ").append(name(highProperty));
  }

  @Override
  public void queryPlanHash(HashQueryPlanBuilder builder) {
    builder.add(BetweenPropertyExpression.class).add(lowProperty).add(highProperty);
    builder.bind(1);
  }

  @Override
  public int queryBindHash() {
    return val().hashCode();
  }

  @Override
  public boolean isSameByPlan(SpiExpression other) {
    if (!(other instanceof BetweenPropertyExpression)) {
      return false;
    }

    BetweenPropertyExpression that = (BetweenPropertyExpression) other;
    return lowProperty.equals(that.lowProperty)
      && highProperty.equals(that.highProperty);
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    BetweenPropertyExpression that = (BetweenPropertyExpression) other;
    return val().equals(that.val());
  }
}
