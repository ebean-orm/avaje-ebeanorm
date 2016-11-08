package com.avaje.ebeaninternal.server.text.json;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.text.json.JsonContext;
import com.avaje.ebean.text.json.JsonWriteBeanVisitor;
import com.avaje.ebean.text.json.JsonWriteOptions;
import com.avaje.ebean.text.json.JsonWriter;
import com.avaje.tests.model.basic.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonWriteVisitor extends BaseTestCase {

  @Test
  public void test() {

    ResetBasicData.reset();

    List<Customer> list = Ebean.find(Customer.class)
      .select("id, name, status")
      .fetch("billingAddress", "line1, city")
      .fetch("billingAddress.country", "*")
      .fetch("contacts", "firstName,email")
      .order().desc("id")
      .findList();

    JsonContext json = Ebean.json();

    JsonWriteOptions options = new JsonWriteOptions();
    options.setRootPathVisitor(new CustomerVisitor());
    options.setPathVisitor("billingAddress", new BillingVisitor());
    options.setPathVisitor("billingAddress.country", new CountryVisitor());
    options.setPathVisitor("contacts", new ContactVisitor());

    String jsonContent = json.toJson(list, options);

    assertThat(jsonContent).contains("customerExtra");
    assertThat(jsonContent).contains("contactExtra");
    assertThat(jsonContent).contains("billingExtra");
    assertThat(jsonContent).contains("countryExtra");
  }

  class CustomerVisitor implements JsonWriteBeanVisitor<Customer> {

    @Override
    public void visit(Customer bean, JsonWriter jsonWriter) {
      jsonWriter.writeStringField("customerExtra", "extra4");
      jsonWriter.writeStartObject("startObj");
      jsonWriter.writeBooleanField("yesno", true);
      jsonWriter.writeEndObject();

      jsonWriter.writeFieldName("secObj");
      jsonWriter.writeStartObject();
      jsonWriter.writeBooleanField("yesno", false);
      jsonWriter.writeEndObject();

      jsonWriter.writeStartArray("startList");
      jsonWriter.writeBoolean(true);
      jsonWriter.writeString("one");
      jsonWriter.writeNumber(1);
      jsonWriter.writeNumber(2L);
      jsonWriter.writeNumber(new BigDecimal("34.45"));
      jsonWriter.writeNull();
      jsonWriter.writeEndArray();

      jsonWriter.writeFieldName("secondList");
      jsonWriter.writeStartArray();
      jsonWriter.writeNumber(3L);
      jsonWriter.writeRawValue("\"rawVal\"");
      jsonWriter.writeEndArray();

      jsonWriter.writeRaw(",\"rawField\":{\"ra\":42}");

    }
  }

  class ContactVisitor implements JsonWriteBeanVisitor<Contact> {

    @Override
    public void visit(Contact bean, JsonWriter jsonWriter) {
      jsonWriter.writeStringField("contactExtra", "extra3");
    }
  }

  class BillingVisitor implements JsonWriteBeanVisitor<Address> {

    @Override
    public void visit(Address bean, JsonWriter jsonWriter) {
      jsonWriter.writeStringField("billingExtra", "extra1");
    }
  }

  class CountryVisitor implements JsonWriteBeanVisitor<Country> {

    @Override
    public void visit(Country bean, JsonWriter jsonWriter) {
      jsonWriter.writeStringField("countryExtra", "extra2");
    }
  }
}
