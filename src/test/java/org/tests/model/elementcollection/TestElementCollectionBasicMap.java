package org.tests.model.elementcollection;

import io.ebean.BaseTestCase;
import io.ebean.Ebean;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestElementCollectionBasicMap extends BaseTestCase {

  @Test
  public void test() {

    EcmPerson person = new EcmPerson("Fiona021");
    person.getPhoneNumbers().put("home","021 1234");
    person.getPhoneNumbers().put("work","021 4321");
    Ebean.save(person);


    EcmPerson person1 = new EcmPerson("Fiona09");
    person1.getPhoneNumbers().put("home","09 1234");
    person1.getPhoneNumbers().put("work","09 4321");
    person1.getPhoneNumbers().put("mob","09 9876");
    Ebean.save(person1);

    List<EcmPerson> found =
      Ebean.find(EcmPerson.class).where()
        .startsWith("name", "Fiona0")
        .order().asc("id")
        .findList();

    Map<String,String> phoneNumbers0 = found.get(0).getPhoneNumbers();
    Map<String,String> phoneNumbers1 = found.get(1).getPhoneNumbers();
    phoneNumbers0.size();

    assertThat(phoneNumbers0).containsValues("021 1234", "021 4321");
    assertThat(phoneNumbers0.get("work")).isEqualTo("021 4321");
    assertThat(phoneNumbers1).containsValues("09 1234", "09 4321", "09 9876");
    assertThat(phoneNumbers1.get("mob")).isEqualTo("09 9876");


    List<EcmPerson> found2 =
      Ebean.find(EcmPerson.class)
        .fetch("phoneNumbers")
        .where()
        .startsWith("name", "Fiona0")
        .order().asc("id")
        .findList();

    assertThat(found2).hasSize(2);
    EcmPerson foundFirst = found2.get(0);
    String asJson = Ebean.json().toJson(foundFirst);

    EcmPerson fromJson = Ebean.json().toBean(EcmPerson.class, asJson);

    assertThat(fromJson.getPhoneNumbers()).containsValues("021 1234", "021 4321");
    assertThat(fromJson.getPhoneNumbers().get("home")).isEqualTo("021 1234");

  }
}
