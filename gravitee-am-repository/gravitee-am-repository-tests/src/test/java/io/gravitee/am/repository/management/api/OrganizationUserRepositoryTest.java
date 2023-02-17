/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.repository.management.api;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.ZoneOffset.UTC;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OrganizationUserRepositoryTest extends AbstractManagementTest {
    public static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private OrganizationUserRepository organizationUserRepository;

    @Test
    public void testFindByOrganization() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("testOrga");
        organizationUserRepository.create(user).blockingGet();

        // fetch users
        TestSubscriber<User> testSubscriber = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, "testOrga").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindByUserAndSource() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setSource("sourceid");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("testOrga");
        organizationUserRepository.create(user).blockingGet();

        // fetch users
        TestObserver<User> testObserver = organizationUserRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, "testOrga", user.getUsername(), user.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
    }

    @Test
    public void testFindAll() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("testFindByAll");
        organizationUserRepository.create(user).blockingGet();

        // fetch users
        TestObserver<Page<User>> testObserver = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, user.getReferenceId(), 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = organizationUserRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getPassword().equals(user.getPassword()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
        testObserver.assertValue(u -> u.getDynamicRoles().containsAll(user.getDynamicRoles()));
        testObserver.assertValue(u -> u.getEntitlements().containsAll(user.getEntitlements()));
        testObserver.assertValue(u -> u.getEmails().size() == 1);
        testObserver.assertValue(u -> u.getPhoneNumbers().size() == 1);
        testObserver.assertValue(u -> u.getPhotos().size() == 1);
        testObserver.assertValue(u -> u.getIms().size() == 1);
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 1);
        testObserver.assertValue(u -> u.getFactors().size() == 1);
    }

    @Test
    public void testFindByIdIn() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestSubscriber<User> testObserver = organizationUserRepository.findByIdIn(Arrays.asList(userCreated.getId())).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByExternalIdAndSource() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = organizationUserRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId(), userCreated.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getPassword().equals(user.getPassword()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
        testObserver.assertValue(u -> u.getDynamicRoles().containsAll(user.getDynamicRoles()));
        testObserver.assertValue(u -> u.getEntitlements().containsAll(user.getEntitlements()));
        testObserver.assertValue(u -> u.getEmails().size() == 1);
        testObserver.assertValue(u -> u.getPhoneNumbers().size() == 1);
        testObserver.assertValue(u -> u.getPhotos().size() == 1);
        testObserver.assertValue(u -> u.getIms().size() == 1);
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 1);
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getFactors().get(0).getChannel() != null);
        testObserver.assertValue(u -> u.getFactors().get(0).getSecurity() != null);
    }

    @Test
    public void shouldNotFindByUnkownExternalIdAndSource() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = organizationUserRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId()+"unknown", userCreated.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }


    private User buildUser() {
        User user = new User();
        String random = UUID.randomUUID().toString();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("organization"+random);
        user.setUsername("username"+random);
        user.setEmail(random+"@acme.fr");
        user.setAccountLockedAt(new Date());
        user.setAccountLockedUntil(new Date());
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setClient("client"+random);
        user.setCreatedAt(new Date());
        user.setCredentialsNonExpired(true);
        user.setDisplayName("display"+random);
        user.setEnabled(true);
        user.setExternalId("external"+random);
        user.setInternal(false);
        user.setLastName("last"+random);
        user.setLoggedAt(new Date());
        user.setFirstName("first"+random);
        user.setLoginsCount(5l);
        user.setNewsletter(false);
        user.setNickName("nick"+random);
        user.setSource("gravitee");
        user.setPassword("testpassword");

        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue("val"+random);
        user.setEmails(Arrays.asList(attribute));
        user.setPhotos(Arrays.asList(attribute));
        user.setPhoneNumbers(Arrays.asList(attribute));
        user.setIms(Arrays.asList(attribute));

        user.setEntitlements(Arrays.asList("ent"+random));
        user.setRoles(Arrays.asList("role"+random));
        user.setDynamicRoles(Arrays.asList("dynamic_role"+random));

        Address addr = new Address();
        addr.setCountry("fr");
        user.setAddresses(Arrays.asList(addr));

        Certificate certificate = new Certificate();
        certificate.setValue("cert"+random);
        user.setX509Certificates(Arrays.asList(certificate));

        EnrolledFactor fact = new EnrolledFactor();
        fact.setAppId("app"+random);
        fact.setSecurity(new EnrolledFactorSecurity("a", "b", Collections.singletonMap("a", "b")));
        fact.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, "e@e"));
        user.setFactors(Arrays.asList(fact));

        Map<String, Object> info = new HashMap<>();
        info.put(StandardClaims.EMAIL, random+"@info.acme.fr");
        user.setAdditionalInformation(info);
        return user;
    }

    @Test
    public void testFindById_referenceType() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORGANIZATION_ID);
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = organizationUserRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals("testsUsername"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        organizationUserRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("organizationId");
        user.setUsername("testsUsername");
        user.setAdditionalInformation(Collections.singletonMap("email", "johndoe@test.com"));
        TestObserver<User> testObserver = organizationUserRepository.create(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()) && u.getAdditionalInformation().containsKey("email"));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("organizationId");
        user.setUsername("testsUsername");
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // update user
        User updatedUser = new User();
        updatedUser.setReferenceType(ReferenceType.ORGANIZATION);
        updatedUser.setReferenceId("organizationId");
        updatedUser.setId(userCreated.getId());
        updatedUser.setUsername("testUpdatedUsername");

        TestObserver<User> testObserver = organizationUserRepository.update(updatedUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(updatedUser.getUsername()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId("organizationId");
        user.setUsername("testsUsername");
        User userCreated = organizationUserRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = organizationUserRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));

        // delete user
        TestObserver testObserver1 = organizationUserRepository.delete(userCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch user
        organizationUserRepository.findById(userCreated.getId()).test().assertEmpty();
    }

    @Test
    public void testSearch_byUsername_strict() {
        testSearch_strict("testUsername");
    }

    @Test
    public void testSearch_byDisplayName_strict() {
        testSearch_strict("displayName");
    }

    @Test
    public void testSearch_byFirstName_strict() {
        testSearch_strict("firstName");
    }

    @Test
    public void testSearch_byLastName_strict() {
        testSearch_strict("lastName");
    }

    @Test
    public void testSearch_email_strict() {
        testSearch_strict("user.name@mail.com");
    }

    @Test
    public void testSearch_byUsername_wildcard() {
        testSearch_wildcard("testUsername*");
    }

    @Test
    public void testSearch_byDisplayName_wildcard() {
        testSearch_wildcard("displayName*");
    }

    @Test
    public void testSearch_byFirstName_wildcard() {
        testSearch_wildcard("firstName*");
    }

    @Test
    public void testSearch_byLastName_wildcard() {
        testSearch_wildcard("lastName*");
    }

    @Test
    public void testSearch_email_wildcard() {
        testSearch_wildcard("user.name@mail.com*");
    }

    @Test
    public void testSearch_byUsername_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        organizationUserRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.ORGANIZATION);
        user3.setReferenceId(organization);
        user3.setUsername("testUsername3");
        organizationUserRepository.create(user3).blockingGet();

        // fetch user (page 0)
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, "testUsername*", 0, 2).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);
        testObserverP0.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername()) && it.next().getUsername().equals(user2.getUsername());
        });

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, "testUsername*", 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
        testObserverP1.assertValue(users -> users.getData().iterator().next().getUsername().equals(user3.getUsername()));
    }

    @Test
    public void testScimSearch_byDate_paged() {
        final String organization = "organization";
        // create user
        Date now = new Date();
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        user1.setCreatedAt(now);
        user1.setUpdatedAt(now);
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        user2.setCreatedAt(now);
        user2.setUpdatedAt(now);
        organizationUserRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.ORGANIZATION);
        user3.setReferenceId(organization);
        user3.setUsername("testUsername3");
        user3.setCreatedAt(now);
        user3.setUpdatedAt(now);
        organizationUserRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteriaName = new FilterCriteria();
        criteriaName.setFilterName("userName");
        criteriaName.setFilterValue("testUsername");
        criteriaName.setOperator("sw");
        criteriaName.setQuoteFilterValue(true);

        FilterCriteria criteriaDate = new FilterCriteria();
        criteriaDate.setFilterName("meta.created");
        criteriaDate.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(10)));
        criteriaDate.setOperator("gt");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setOperator("and");
        criteria.setFilterComponents(Arrays.asList(criteriaDate, criteriaName));
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byUsername_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        organizationUserRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.ORGANIZATION);
        user3.setReferenceId(organization);
        user3.setUsername("testUsername3");
        organizationUserRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("userName");
        criteria.setFilterValue("testUsername");
        criteria.setOperator("sw");
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byUsername_NotPaged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        organizationUserRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.ORGANIZATION);
        user3.setReferenceId(organization);
        user3.setUsername("testUsername3");
        organizationUserRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("userName");
        criteria.setFilterValue("testUsername");
        criteria.setOperator("sw");
        criteria.setQuoteFilterValue(true);
        final TestSubscriber<User> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValueCount(3);
    }

    @Test
    public void testScimSearch_byGivenName_SW_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "gname2"));
        organizationUserRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.ORGANIZATION);
        user3.setReferenceId(organization);
        user3.setUsername("testUsername3");
        user3.setAdditionalInformation(Collections.singletonMap("given_name", "no"));
        organizationUserRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname");
        criteria.setOperator("sw");
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 1, 1).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byGivenName_EQ_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "gname2"));
        organizationUserRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname1");
        criteria.setOperator("eq");
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));

    }

    @Test
    public void testScimSearch_byGivenName_PR_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        organizationUserRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("");
        criteria.setOperator("pr");
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));

    }

    @Test
    public void testScimSearch_byGivenName_NE_paged() {
        final String organization = "organization";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.ORGANIZATION);
        user1.setReferenceId(organization);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        organizationUserRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setUsername("testUsername2");
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "theother"));
        organizationUserRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname1");
        criteria.setOperator("ne");
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user2.getUsername()));

    }

    private void testSearch_strict(String query) {
        final String organization = "organization";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(organization);
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setDisplayName("displayName");
        user.setUsername("testUsername");
        user.setEmail("user.name@mail.com");
        organizationUserRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setFirstName("firstName2");
        user2.setLastName("lastName2");
        user2.setDisplayName("displayName2");
        user2.setUsername("testUsername2");
        user2.setEmail("user.name@mail.com2");
        organizationUserRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, query, 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
        testObserver.assertValue(users -> users.getData().iterator().next().getUsername().equals(user.getUsername()));

    }

    private void testSearch_wildcard(String query) {
        final String organization = "organization";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(organization);
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setDisplayName("displayName");
        user.setUsername("testUsername");
        user.setEmail("user.name@mail.com");
        organizationUserRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.ORGANIZATION);
        user2.setReferenceId(organization);
        user2.setFirstName("firstName2");
        user2.setLastName("lastName2");
        user2.setDisplayName("displayName2");
        user2.setUsername("testUsername2");
        user2.setEmail("user.name@mail.com2");
        organizationUserRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = organizationUserRepository.search(ReferenceType.ORGANIZATION, organization, query, 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 2);
    }

    @Test
    public void testDeleteByRef() throws TechnicalException {
        final String ORG_1 = "org1";
        final String ORG_2 = "org2";

        // create user
        User user = buildUser();
        user.setReferenceId(ORG_1);
        user.setReferenceType(ReferenceType.ORGANIZATION);
        organizationUserRepository.create(user).blockingGet();

        user = buildUser();
        user.setReferenceId(ORG_1);
        user.setReferenceType(ReferenceType.ORGANIZATION);
        organizationUserRepository.create(user).blockingGet();

        user = buildUser();
        user.setReferenceId(ORG_2);
        user.setReferenceType(ReferenceType.ORGANIZATION);
        organizationUserRepository.create(user).blockingGet();

        final long usersDomain1 = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, ORG_1).count().blockingGet();
        Assert.assertEquals("Org1 should have 2 users", 2, usersDomain1);
        long usersDomain2 = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, ORG_2).count().blockingGet();
        Assert.assertEquals("Org2 should have 1 users", 1, usersDomain2);

        // delete user
        TestObserver testObserver1 = organizationUserRepository.deleteByReference(ReferenceType.ORGANIZATION, ORG_1).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch user
        final TestSubscriber<User> find = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, ORG_1).test();
        find.awaitDone(10, TimeUnit.SECONDS);
        find.assertNoValues();

        usersDomain2 = organizationUserRepository.findAll(ReferenceType.ORGANIZATION, ORG_2).count().blockingGet();
        Assert.assertEquals("Org2 should have 1 users", 1, usersDomain2);

    }
}
