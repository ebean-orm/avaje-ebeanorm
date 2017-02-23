package org.tests.cache;

import io.ebean.BaseTestCase;
import io.ebean.Ebean;
import io.ebean.cache.ServerCache;
import io.ebean.cache.ServerCacheManager;
import io.ebeaninternal.server.cache.CachedManyIds;
import org.tests.model.basic.Contact;
import org.tests.model.basic.Country;
import org.tests.model.basic.Customer;
import org.tests.model.basic.OCachedBean;
import org.tests.model.basic.ResetBasicData;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestCacheCollectionIds extends BaseTestCase {

  ServerCacheManager cacheManager = Ebean.getServerCacheManager();

  @Test
  public void test() {

    ResetBasicData.reset();
    awaitL2Cache();

    ServerCache custCache = cacheManager.getBeanCache(Customer.class);
    ServerCache custManyIdsCache = cacheManager.getCollectionIdsCache(Customer.class, "contacts");

    // cacheManager.setCaching(Customer.class, true);
    // cacheManager.setCaching(Contact.class, true);

    custCache.clear();
    custManyIdsCache.clear();

    List<Customer> list = Ebean.find(Customer.class).setAutoTune(false).setLoadBeanCache(true)
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
    awaitL2Cache();

    int currentNumContacts2 = fetchCustomer(customer.getId());
    Assert.assertEquals(currentNumContacts + 1, currentNumContacts2);

  }

  private int fetchCustomer(Integer id) {

    Customer customer2 = Ebean.find(Customer.class, id);

    List<Contact> contacts2 = customer2.getContacts();

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

    OCachedBean cachedBean = new OCachedBean();
    cachedBean.setName("hello");
    cachedBean.getCountries().add(Ebean.find(Country.class, "NZ"));
    cachedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    // used to just load the cache - trigger loading
    OCachedBean dummyToLoad = Ebean.find(OCachedBean.class, cachedBean.getId());
    dummyToLoad.getCountries().size();

    ServerCache cachedBeanCountriesCache = cacheManager.getCollectionIdsCache(OCachedBean.class, "countries");
    CachedManyIds cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(cachedBean.getId());

    // confirm the starting data and cache entry
    Assert.assertEquals(2, dummyToLoad.getCountries().size());
    Assert.assertEquals(2, cachedManyIds.getIdList().size());


    // act
    OCachedBean loadedBean = Ebean.find(OCachedBean.class, cachedBean.getId());
    loadedBean.getCountries().clear();
    loadedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(loadedBean);
    awaitL2Cache();

    // Get the data to assert/check against
    OCachedBean result = Ebean.find(OCachedBean.class, cachedBean.getId());
    cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(result.getId());

    // assert that data and cache both show correct data
    Assert.assertEquals(1, result.getCountries().size());
    Assert.assertEquals(1, cachedManyIds.getIdList().size());
    Assert.assertFalse(cachedManyIds.getIdList().contains("NZ"));
    Assert.assertTrue(cachedManyIds.getIdList().contains("AU"));
  }


  /**
   * When updating a ManyToMany relations also the collection cache must be updated.
   * Alternate to above test where in this case the bean is dirty - loadedBean.setName("goodbye");.
   */
  @Test
  public void testUpdatingCollectionCacheForManyToManyRelationsWithUpdatedBean() {
    // arrange
    ResetBasicData.reset();

    OCachedBean cachedBean = new OCachedBean();
    cachedBean.setName("hello");
    cachedBean.getCountries().add(Ebean.find(Country.class, "NZ"));
    cachedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    // used to just load the cache - trigger loading
    OCachedBean dummyToLoad = Ebean.find(OCachedBean.class, cachedBean.getId());
    dummyToLoad.getCountries().size();

    ServerCache cachedBeanCountriesCache = cacheManager.getCollectionIdsCache(OCachedBean.class, "countries");
    CachedManyIds cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(cachedBean.getId());

    // confirm the starting data and cache entry
    Assert.assertEquals(2, dummyToLoad.getCountries().size());
    Assert.assertEquals(2, cachedManyIds.getIdList().size());


    // act - this time update the name property so the bean is dirty
    OCachedBean loadedBean = Ebean.find(OCachedBean.class, cachedBean.getId());
    loadedBean.setName("goodbye");
    loadedBean.getCountries().clear();
    loadedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(loadedBean);
    awaitL2Cache();

    // Get the data to assert/check against
    OCachedBean result = Ebean.find(OCachedBean.class, cachedBean.getId());
    cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(result.getId());

    // assert that data and cache both show correct data
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

    OCachedBean cachedBean = new OCachedBean();
    cachedBean.setName("cachedBeanTest");
    cachedBean.getCountries().add(Ebean.find(Country.class, "NZ"));
    cachedBean.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.save(cachedBean);

    // clear the cache
    ServerCache cachedBeanCountriesCache = cacheManager.getCollectionIdsCache(OCachedBean.class, "countries");
    cachedBeanCountriesCache.clear();
    Assert.assertEquals(0, cachedBeanCountriesCache.size());

    // load the cache
    OCachedBean dummyLoad = Ebean.find(OCachedBean.class, cachedBean.getId());
    List<Country> dummyCountries = dummyLoad.getCountries();
    Assert.assertEquals(2, dummyCountries.size());

    // assert that the cache contains the expected entry
    Assert.assertEquals("countries cache now loaded with 1 entry", 1, cachedBeanCountriesCache.size());
    CachedManyIds dummyEntry = (CachedManyIds) cachedBeanCountriesCache.get(dummyLoad.getId());
    Assert.assertNotNull(dummyEntry);
    Assert.assertEquals("2 ids in the entry", 2, dummyEntry.getIdList().size());
    Assert.assertTrue(dummyEntry.getIdList().contains("NZ"));
    Assert.assertTrue(dummyEntry.getIdList().contains("AU"));


    // act - this should invalidate our cache entry
    OCachedBean update = new OCachedBean();
    update.setId(cachedBean.getId());
    update.setName("modified");
    update.getCountries().add(Ebean.find(Country.class, "AU"));

    Ebean.update(update);
    awaitL2Cache();

    Assert.assertEquals("countries entry still there (but updated)", 1, cachedBeanCountriesCache.size());

    CachedManyIds cachedManyIds = (CachedManyIds) cachedBeanCountriesCache.get(update.getId());

    // assert cache updated
    Assert.assertEquals(1, cachedManyIds.getIdList().size());
    Assert.assertFalse(cachedManyIds.getIdList().contains("NZ"));
    Assert.assertTrue(cachedManyIds.getIdList().contains("AU"));

    // assert countries good
    OCachedBean result = Ebean.find(OCachedBean.class, cachedBean.getId());
    Assert.assertEquals(1, result.getCountries().size());

  }

  @Test
  public void testUpdating_noChange() throws InterruptedException {

    ResetBasicData.reset();

    Ebean.beginTransaction();
    try {

      OCachedBean cachedBean = new OCachedBean();
      cachedBean.setName("helloForUpdate");
      Ebean.save(cachedBean);

      cachedBean = Ebean.find(OCachedBean.class, cachedBean.getId());

      cachedBean.setName("mod");
      ArrayList<Country> list = new ArrayList<>();
      list.add(Ebean.find(Country.class, "NZ"));
      cachedBean.setCountries(list);
      Ebean.save(cachedBean);


      cachedBean.setName("mod2");
      Ebean.save(cachedBean);

      Ebean.commitTransaction();
    } finally {
      Ebean.endTransaction();
    }


    Thread.sleep(2000);
  }
}
