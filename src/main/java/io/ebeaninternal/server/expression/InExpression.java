package io.ebeaninternal.server.expression;

import io.ebean.bean.EntityBean;
import io.ebean.event.BeanQueryRequest;
import io.ebeaninternal.api.SpiExpression;
import io.ebeaninternal.api.SpiExpressionRequest;
import io.ebeaninternal.server.el.ElPropertyValue;
import io.ebeaninternal.server.persist.MultiValueWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class InExpression extends AbstractExpression {

  private final boolean not;

  private final Collection<?> sourceValues;

  private Object[] bindValues;

  private boolean containsNull;
  private boolean multiValueSupported;

  InExpression(String propertyName, Collection<?> sourceValues, boolean not) {
    super(propertyName);
    this.sourceValues = sourceValues;
    this.not = not;
  }

  InExpression(String propertyName, Object[] array, boolean not) {
    super(propertyName);
    this.sourceValues = Arrays.asList(array);
    this.not = not;
  }

  private void prepareBindValues() {
    Set<Object> vals = new HashSet<>(sourceValues.size());
    for (Object sourceValue : sourceValues) {
      NamedParamHelp.valueAdd(vals, sourceValue);
    }
    containsNull = vals.remove(null);
    bindValues = vals.toArray();
  }

  @Override
  public void prepareExpression(BeanQueryRequest<?> request) {
    prepareBindValues();
    if (bindValues.length > 0) {
      multiValueSupported = request.isMultiValueSupported((bindValues[0]).getClass());
    }
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    prepareBindValues();
    context.writeIn(propName, bindValues, containsNull, not);
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {

    ElPropertyValue prop = getElProp(request);
    if (prop != null && !prop.isAssocId()) {
      prop = null;
    }

    if (prop == null) {
      if (bindValues.length > 0) {
        // if we have no property, we wrap them in a multi value wrapper.
        // later the binder will decide, which bind strategy to use.
        request.addBindValue(new MultiValueWrapper(Arrays.asList(bindValues)));
      }
    } else {
      List<Object> idList = new ArrayList<>();
      for (Object bindValue : bindValues) {
        // extract the id values from the bean
        Object[] ids = prop.getAssocIdValues((EntityBean) bindValue);
        if (ids != null) {
          Collections.addAll(idList, ids);
        }
      }
      if (!idList.isEmpty()) {
        request.addBindValue(new MultiValueWrapper(idList));
      }
    }
  }

  @Override
  public void addSql(SpiExpressionRequest request) {

    if (bindValues.length == 0) {
      if (containsNull) {
        String expr = not ? " is not null" : " is null";
        request.append(propName).append(expr);
      } else {
        String expr = not ? "1=1" : "1=0";
        request.append(expr);
      }
      return;
    }

    ElPropertyValue prop = getElProp(request);
    if (prop != null && !prop.isAssocId()) {
      prop = null;
    }

    String realPropName = propName;
    if (containsNull != not) {
      request.append("(");
    }
    if (prop != null) {
      realPropName = prop.getAssocIdInExpr(propName);
      request.append(realPropName);
      String inClause = prop.getAssocIdInValueExpr(not, bindValues.length);
      request.append(inClause);

    } else {
      request.append(propName);
      request.appendInExpression(not, bindValues);
    }
    // if we perform an "in" query (not = false) and we want "null's",
    // we must append an additional OR statement.
    // if we perform a "not in" query (not = true) and we have not nulls
    // in our values list, we must include nulls.
    // Consider, the db has the values 1,2,3,4,null:
    // in(1,2)          => 1,2
    // in(1,2,null)     => 1,2,null  (normal SQL would return 1,2 only)
    // not in(1,2)      => 3,4,null  (normal SQL would return 3,4 only)
    // not in(1,2,null) => 3,4       (normal SQL would return 3,4 only)
    if (containsNull != not) {
      request.append("or ").append(realPropName).append(" is null) ");
    }
  }

  /**
   * Based on the number of values in the in clause.
   */
  @Override
  public void queryPlanHash(StringBuilder builder) {
    if (not) {
      builder.append("NotIn[");
    } else {
      builder.append("In[");
    }
    builder.append(propName);
    builder.append(" ?");
    if (!multiValueSupported) {
      // query plan specific to the number of parameters in the IN clause
      builder.append(bindValues.length);
    }
    if (containsNull) {
      builder.append(",null");
    }
    builder.append("]");
  }

  @Override
  public int queryBindHash() {
    int hc = 92821;
    for (Object bindValue : bindValues) {
      hc = 92821 * hc + bindValue.hashCode();
    }
    return hc;
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    InExpression that = (InExpression) other;
    if (this.bindValues.length != that.bindValues.length) {
      return false;
    }
    for (int i = 0; i < bindValues.length; i++) {
      if (!bindValues[i].equals(that.bindValues[i])) {
        return false;
      }
    }
    return true;
  }
}
