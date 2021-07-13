package org.tests.query;

import io.ebean.BaseTestCase;
import io.ebean.DB;
import io.ebean.Query;
import org.junit.Test;
import org.tests.model.basic.CKeyParent;
import org.tests.model.basic.Order;
import org.tests.model.basic.OrderDetail;
import org.tests.model.basic.OrderShipment;
import org.tests.model.basic.ResetBasicData;
import org.tests.model.basic.Vehicle;
import org.tests.model.basic.VehicleDriver;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSubQuery extends BaseTestCase {

  @Test
  public void testId() {

    ResetBasicData.reset();

    List<Integer> productIds = new ArrayList<>();
    productIds.add(3);
    productIds.add(4);
    productIds.add(5);

    Query<Order> sq = DB.find(Order.class).select("id").where()
      .in("details.product.id", productIds).query();

    DB.find(Order.class).where().in("id", sq).findList();
  }

  @Test
  public void test_IsIn() {

    ResetBasicData.reset();

    List<Integer> productIds = new ArrayList<>();
    productIds.add(3);

    Query<Order> sq = DB.createQuery(Order.class).select("id").where()
      .isIn("details.product.id", productIds).query();

    DB.find(Order.class).where().isIn("id", sq).findList();
  }

  /**
   * Testcase, that discovered, that DefaultOrmQuery.setDefaultSelectClause is set on subQueries with fetch path.
   * Also checks, that SqlTreeBuilder does not read id on Many2One props.
   */
  @Test
  public void test_IsInWithFetchSubQuery1() {

    List<Integer> productIds = new ArrayList<>();
    productIds.add(3);

    Query<OrderDetail> sq = DB.createQuery(OrderDetail.class).fetch("order", "id").where()
        .isIn("product.id", productIds).query();

    // execute the subQuery as copy (generatedSQL must be part of original query)
    Query<OrderDetail> debugSq = sq.copy();
    debugSq.findSingleAttribute();
    assertThat(debugSq.getGeneratedSql()).isEqualTo(
        "select t1.id from o_order_detail t0 join o_order t1 on t1.id = t0.order_id where t0.product_id in (?)");

    Query<Order> query = DB.find(Order.class).select("shipDate").where().isIn("id", sq).query();
    query.findSingleAttribute();

    assertThat(query.getGeneratedSql())
        .isEqualTo("select t0.ship_date from o_order t0 where  (t0.id) in (" + debugSq.getGeneratedSql() + ")");
  }

  /**
   * Test checks, that DefaultOrmQuery.markQueryJoins handles subQuery correct.
   */
  @Test
  public void test_IsInWithFetchSubQuery2() {

    Query<OrderDetail> sq = DB.createQuery(OrderDetail.class).fetch("order.customer", "anniversary").where()
        .eq("order.customer.name", "Roland")
        .query().setDistinct(true);

    // execute the subQuery as copy (generatedSQL must be part of original query)
    Query<OrderDetail> debugSq = sq.copy();
    debugSq.findSingleAttribute();

    Query<Order> query = DB.find(Order.class).select("status").where().isIn("shipDate", sq).query();
    query.findSingleAttribute();
    assertThat(query.getGeneratedSql())
        .isEqualTo("select t0.status from o_order t0 where  (t0.ship_date) in (" + debugSq.getGeneratedSql() + ")");
  }

  /**
   * Checks, that SqlTreeBuilder does not read id on One2Many props.
   */
  @Test
  public void test_IsInWithFetchSubQuery3() {

    List<Integer> productIds = new ArrayList<>();
    productIds.add(3);

    Query<OrderDetail> sq = DB.createQuery(OrderDetail.class).fetch("order.shipments", "id").where()
        .isIn("product.id", productIds).query();

    // execute the subQuery as copy (generatedSQL must be part of original query)
    Query<OrderDetail> debugSq = sq.copy();
    debugSq.findSingleAttribute();

    Query<OrderShipment> query = DB.find(OrderShipment.class).select("shipTime").where().isIn("id", sq).query();
    query.findSingleAttribute();

    assertThat(query.getGeneratedSql())
        .isEqualTo("select t0.ship_time from or_order_ship t0 where  (t0.id) in (" + debugSq.getGeneratedSql() + ")");
  }
  
  public void testCompositeKey() {
    ResetBasicData.reset();

    Query<CKeyParent> sq = DB.createQuery(CKeyParent.class).select("id.oneKey")
      .setAutoTune(false).where().query();

    Query<CKeyParent> pq = DB.find(CKeyParent.class).where().in("id.oneKey", sq).query();

    pq.findList();

    String sql = pq.getGeneratedSql();

    String golden = "(t0.one_key) in (select t0.one_key from ckey_parent t0) ";

    assertThat(sql).contains(golden);
  }

  /**
   * show that ebean is not using the correct table name in the subquery (sq)
   *
   * public void testInheritance1() { ResetBasicData.reset();
   *
   * Query<Vehicle> sq = Ebean.createQuery(Vehicle.class) .select("id")
   * .setAutoTune(false) .where() .query();
   *
   * Query<VehicleDriver> pq = Ebean.find(VehicleDriver.class)
   * .where().in("vehicle.id", sq) .query();
   *
   * pq.findList();
   *
   * String sql = pq.getGeneratedSql(); System.err.println(sql);
   *
   * String golden = "(t0.vehicle_id) in (select t0.id from t0.vehicle t0)"; if
   * (sql.indexOf(golden) < 0) { System.out.println("failed sql:"+sql);
   * fail("golden string not found"); }
   *
   * }
   */

  /**
   * show that ebean is adding the discriminator to the list of columns in the
   * subquery
   */
  public void testInheritance2() {
    ResetBasicData.reset();

    Query<VehicleDriver> sq = DB.createQuery(VehicleDriver.class).select("vehicle")
      .setAutoTune(false).where().query();

    Query<Vehicle> pq = DB.find(Vehicle.class).where().in("id", sq).query();

    pq.findList();

    String sql = pq.getGeneratedSql();

    // TODO: If, after bugfixing, the system still join against vehicle I do not
    // know now, in our case, it is not necessary if not
    // using it in the where clause
    String golden = "(t0.id) in (select t0.vehicle_id from vehicle_driver t0 left join vehicle t1 on t1.id = t0.vehicle_id )";
    assertThat(sql).contains(golden);

  }

  /**
   * show that ebean is adding the discriminator to the list of columns in the
   * subquery. Second test to make sure that joining is still possible after
   * bugfixing testInheritance2.
   */
  public void testInheritance3() {
    ResetBasicData.reset();

    Query<VehicleDriver> sq = DB.createQuery(VehicleDriver.class).select("vehicle")
      .setAutoTune(false).where().eq("vehicle.licenseNumber", "abc").query();

    Query<Vehicle> pq = DB.find(Vehicle.class).where().in("id", sq).query();

    pq.findList();

    String sql = pq.getGeneratedSql();
    String golden = "(t0.id) in (select t0.vehicle_id from vehicle_driver t0 left join vehicle t1 on t1.id = t0.vehicle_id  where t1.license_number = ? )";
    assertThat(sql).contains(golden);
  }

  /**
   * show that ebean is using the wrong column (from the vehicle_driver table
   * instead of vehicle) for the selected column in the subquery. In contrast to
   * testInheritance2+3 this test forces ebean to "drill down" to the key of the
   * relation.
   */
  public void testInheritance4() {
    ResetBasicData.reset();

    Query<VehicleDriver> sq = DB.createQuery(VehicleDriver.class).select("vehicle.id")
      .setAutoTune(false).where().query();

    Query<Vehicle> pq = DB.find(Vehicle.class).where().in("id", sq).query();

    pq.findList();

    String sql = pq.getGeneratedSql();

    String golden = "(t0.id) in (select t0.vehicle_id from vehicle_driver t0 left join vehicle t1 on t1.id = t0.vehicle_id )";
    assertThat(sql).contains(golden);
  }
}
