package com.avaje.ebeaninternal.server.expression;

import com.avaje.ebeaninternal.api.HashQueryPlanBuilder;
import com.avaje.ebeaninternal.api.ManyWhereJoins;
import com.avaje.ebeaninternal.api.SpiExpression;
import com.avaje.ebeaninternal.api.SpiExpressionRequest;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.el.ElPropertyValue;
import com.avaje.ebeaninternal.server.query.SplitName;

import java.io.IOException;


/**
 * Null / Not Null expression.
 * <p>
 * Note that for OneToMany/ManyToMany this effectively gets translated into isEmpty()/isNotEmpty().
 * </p>
 */
class NullExpression extends AbstractExpression {

  private final boolean notNull;

  private ElPropertyValue elProperty;

  private boolean assocMany;

  private String propertyPath;

  NullExpression(String propertyName, boolean notNull) {
    super(propertyName);
    this.notNull = notNull;
  }

  @Override
  public void containsMany(BeanDescriptor<?> desc, ManyWhereJoins manyWhereJoin) {
    elProperty = desc.getElGetValue(propName);
    if (elProperty != null && elProperty.isAssocMany()) {
      // it is OneToMany or ManyToMany so going to be treated as isEmpty() expression
      assocMany = true;
      propertyPath = SplitName.split(propName)[0];
      propertyContainsMany(propertyPath, desc, manyWhereJoin);
    } else {
      propertyContainsMany(propName, desc, manyWhereJoin);
    }
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    context.writeExists(notNull, propName);
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {

  }

  @Override
  public void addSql(SpiExpressionRequest request) {

    if (assocMany) {
      // translate to exists subquery
      IsEmptyExpression.isEmptySql(request, elProperty, !notNull, propertyPath);
      return;
    }

    String nullExpr = notNull ? " is not null " : " is null ";
    if (elProperty != null && elProperty.isAssocId()) {
      request.append(elProperty.getAssocIdExpression(propName, nullExpr));
    } else {
      request.append(propName).append(nullExpr);
    }
  }

  @Override
  public boolean isSameByPlan(SpiExpression other) {
    if (!(other instanceof NullExpression)) {
      return false;
    }

    NullExpression that = (NullExpression) other;
    return this.propName.equals(that.propName)
      && this.notNull == that.notNull;
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    // no bind values so always true
    return true;
  }

  /**
   * Based on notNull flag and the propertyName.
   */
  @Override
  public void queryPlanHash(HashQueryPlanBuilder builder) {
    builder.add(NullExpression.class).add(notNull).add(propName);
  }

  @Override
  public int queryBindHash() {
    return (notNull ? 1 : 0);
  }
}
