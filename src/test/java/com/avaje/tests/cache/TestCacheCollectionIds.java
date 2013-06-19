package com.avaje.tests.cache;

import java.util.List;

import com.avaje.ebeaninternal.server.cache.CachedManyIds;
import com.avaje.tests.model.basic.*;
import org.junit.Assert;
import org.junit.Test;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.cache.ServerCache;

public class TestCacheCollectionIds extends BaseTestCase {

  @Test
  public void test() {

    ResetBasicData.reset();

    ServerCache custCache = Ebean.getServerCacheManager().getBeanCache(Customer.class);
    ServerCache contactCache = Ebean.getServerCacheManager().getBeanCache(Contact.class);
    ServerCache custManyIdsCache = Ebean.getServerCacheManager().getCollectionIdsCache(
        Customer.class, "contacts");

    // Ebean.getServerCacheManager().setCaching(Customer.class, true);
    // Ebean.getServerCacheManager().setCaching(Contact.class, true);

    custCache.clear();
    custManyIdsCache.clear();

    List<Customer> list = Ebean.find(Customer.class).setAutofetch(false).setLoadBeanCache(true)
        .order().asc("id").findList();

    Assert.assertTrue(list.size() > 1);
    // Assert.assertEquals(list.size(),
    // custCache.getStatistics(false).getSize());

    Customer customer = list.get(0);
    List<Contact> contacts = customer.getContacts();
    // Assert.assertEquals(0, custManyIdsCache.getStatistics(false).getSize());
    contacts.size();
    Assert.assertTrue(contacts.size() > 1);
    // Assert.assertEquals(1, custManyIdsCache.getStatistics(false).getSize());
    // Assert.assertEquals(0,
    // custManyIdsCache.getStatistics(false).getHitCount());

    fetchCustomer(customer.getId());
    // Assert.assertEquals(1,
    // custManyIdsCache.getStatistics(false).getHitCount());

    fetchCustomer(customer.getId());
    // Assert.assertEquals(2,
    // custManyIdsCache.getStatistics(false).getHitCount());

    int currentNumContacts = fetchCustomer(customer.getId());
    // Assert.assertEquals(3,
    // custManyIdsCache.getStatistics(false).getHitCount());

    Contact newContact = ResetBasicData.createContact("Check", "CollIds");
    newContact.setCustomer(customer);

    Ebean.save(newContact);

    int currentNumContacts2 = fetchCustomer(customer.getId());
    Assert.assertEquals(currentNumContacts + 1, currentNumContacts2);

    System.out.println("custCache:" + custCache.getStatistics(false));
    System.out.println("contactCache:" + contactCache.getStatistics(false));
    System.out.println("custManyIdsCache:" + custManyIdsCache.getStatistics(false));

  }

  private int fetchCustomer(Integer id) {

    Customer customer2 = Ebean.find(Customer.class).setId(id)
    // .setUseCache(true)
        .findUnique();

    List<Contact> contacts2 = customer2.getContacts();
    contacts2.size();
    for (Contact contact : contacts2) {
      contact.getFirstName();
      contact.getEmail();
    }
    return contacts2.size();
  }

  /**
   * When updating a ManyToMany relations also the collection cache must be updated.
   */
  @Test
  public void testUpdatingCollectionCacheForManyToManyRelations() {
    // arrange
    ResetBasicData.reset();

    CachedBean cachedBean = new CachedBean();
    cachedBean.getCountries().add(Ebean.find(Country.class, "NZ"));
    cachedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    // act
    CachedBean loadedBean = Ebean.find(CachedBean.class, cachedBean.getId());
    loadedBean.getCountries().clear();
    loadedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    CachedBean result = Ebean.find(CachedBean.class, cachedBean.getId());
    ServerCache cachedBeanCountriesCache = Ebean.getServerCacheManager().getCollectionIdsCache(CachedBean.class, "countries");
    CachedManyIds cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(result.getId());


    // assert
    Assert.assertEquals(1, result.getCountries().size());
    Assert.assertEquals(1, cachedManyIds.getIdList().size());
    Assert.assertFalse(cachedManyIds.getIdList().contains("NZ"));
    Assert.assertTrue(cachedManyIds.getIdList().contains("AU"));
  }

  /**
   * When updating a ManyToMany relations also the collection cache must be updated.
   */
  @Test
  public void testUpdatingCollectionCacheForManyToManyRelationsWithinStatelessUpdate() {
    // arrange
    ResetBasicData.reset();

    CachedBean cachedBean = new CachedBean();
    cachedBean.getCountries().add(Ebean.find(Country.class, "NZ"));
    cachedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    // act
    CachedBean update = new CachedBean();
    update.setId(cachedBean.getId());
    update.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.update(cachedBean);

    CachedBean result = Ebean.find(CachedBean.class, cachedBean.getId());
    ServerCache cachedBeanCountriesCache = Ebean.getServerCacheManager().getCollectionIdsCache(CachedBean.class, "countries");
    CachedManyIds cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(result.getId());


    // assert
    Assert.assertEquals(1, result.getCountries().size());
    Assert.assertEquals(1, cachedManyIds.getIdList().size());
    Assert.assertFalse(cachedManyIds.getIdList().contains("NZ"));
    Assert.assertTrue(cachedManyIds.getIdList().contains("AU"));
  }
}
