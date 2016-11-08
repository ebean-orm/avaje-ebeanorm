package com.avaje.tests.basic.one2one;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import org.junit.Assert;
import org.junit.Test;

public class TestOne2OneBookingInvoice extends BaseTestCase {

  @Test
  public void test() {

    Booking b = new Booking(3000L);

    Invoice ai = new Invoice();
    Invoice ci = new Invoice();

    ai.setBooking(b);
    ci.setBooking(b);

    b.setAgentInvoice(ai);
    b.setClientInvoice(ci);

    Ebean.save(b);

    Invoice invoice = Ebean.find(Invoice.class, ai.getId());
    Assert.assertEquals(b.getId(), invoice.getBooking().getId());

    Booking b1 = Ebean.find(Booking.class, b.getId());

    Invoice ai1 = b1.getAgentInvoice();
    Assert.assertNotNull(ai1);

    Booking b2 = ai1.getBooking();
    Assert.assertNotNull(b2);
    Assert.assertEquals(b1.getId(), b2.getId());
    Assert.assertSame(b1, b2);

    Invoice ci1 = b1.getClientInvoice();
    Booking b3 = ci1.getBooking();
    Assert.assertNotNull(b3);
    Assert.assertEquals(b1.getId(), b2.getId());
    Assert.assertSame(b1, b2);

  }
}
