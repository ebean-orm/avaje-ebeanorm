package io.ebeaninternal.server.persist;

import io.ebean.SqlUpdate;
import io.ebean.bean.EntityBean;
import io.ebeaninternal.api.SpiEbeanServer;
import io.ebeaninternal.server.core.PersistRequestBean;
import io.ebeaninternal.server.deploy.BeanCollectionUtil;
import io.ebeaninternal.server.deploy.BeanPropertyAssocMany;

import java.util.Collection;

/**
 * Save details for a simple scalar element collection.
 */
class SaveManySimpleCollection extends SaveManyBase {

  SaveManySimpleCollection(boolean insertedParent, BeanPropertyAssocMany<?> many, EntityBean parentBean, PersistRequestBean<?> request) {
    super(insertedParent, many, parentBean, request);
  }

  @Override
  void save() {

    Collection<?> collection = BeanCollectionUtil.getActualEntries(value);
    if (collection == null) {
      return;
    }

    Object parentId = request.getBeanId();

    SqlUpdate sqlDelete = many.deleteByParentId(parentId, null);

    SpiEbeanServer server = request.getServer();
    server.execute(sqlDelete, transaction);

    transaction.depth(+1);

    String insert = many.insertElementCollection();
    SqlUpdate sqlInsert = server.createSqlUpdate(insert);

    for (Object value : collection) {

      sqlInsert.setParameter(1, parentId);
      sqlInsert.setParameter(2, value);
      server.execute(sqlInsert, transaction);
    }

    transaction.depth(-1);
  }
}
