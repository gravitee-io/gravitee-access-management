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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.jdbc.common.dialect.AbstractDialectHelper;
import io.gravitee.am.repository.jdbc.management.AbstractManagementJdbcTest;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.time.ZoneOffset.UTC;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcUserRepositoryTest extends AbstractManagementJdbcTest {

    public static final String ORGANIZATION_ID = "orga#1";
    @Autowired
    private UserRepository userRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testDomain");
        userRepository.create(user).blockingGet();

        // fetch users
        TestObserver<Set<User>> testObserver = userRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 1);
    }

    @Test
    public void testFindAll() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testFindByAll");
        userRepository.create(user).blockingGet();

        // fetch users
        TestObserver<Page<User>> testObserver = userRepository.findAll(ReferenceType.DOMAIN, user.getReferenceId(), 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
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
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<List<User>> testObserver = userRepository.findByIdIn(Arrays.asList(userCreated.getId())).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.size() == 1);
    }

    @Test
    public void shouldFindByExternalIdAndSource() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId(), userCreated.getSource()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
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
    public void shouldNotFindByUnkownExternalIdAndSource() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId()+"unknown", userCreated.getSource()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }


    private User buildUser() {
        User user = new User();
        String random = UUID.randomUUID().toString();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain"+random);
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
        user.setSource("test");

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

        Address addr = new Address();
        addr.setCountry("fr");
        user.setAddresses(Arrays.asList(addr));

        Certificate certificate = new Certificate();
        certificate.setValue("cert"+random);
        user.setX509Certificates(Arrays.asList(certificate));

        EnrolledFactor fact = new EnrolledFactor();
        fact.setAppId("app"+random);
        fact.setSecurity(new EnrolledFactorSecurity("a", "b"));
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
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, userCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals("testsUsername"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        userRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        user.setAdditionalInformation(Collections.singletonMap("email", "johndoe@test.com"));
        TestObserver<User> testObserver = userRepository.create(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()) && u.getAdditionalInformation().containsKey("email"));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        User userCreated = userRepository.create(user).blockingGet();

        // update user
        User updatedUser = new User();
        updatedUser.setReferenceType(ReferenceType.DOMAIN);
        updatedUser.setReferenceId("domainId");
        updatedUser.setId(userCreated.getId());
        updatedUser.setUsername("testUpdatedUsername");

        TestObserver<User> testObserver = userRepository.update(updatedUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(updatedUser.getUsername()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));

        // delete user
        TestObserver testObserver1 = userRepository.delete(userCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch user
        userRepository.findById(userCreated.getId()).test().assertEmpty();
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
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setUsername("testUsername3");
        userRepository.create(user3).blockingGet();

        // fetch user (page 0)
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, "testUsername*", 0, 2).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);
        testObserverP0.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername()) && it.next().getUsername().equals(user2.getUsername());
        });

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, "testUsername*", 1, 2).test();
        testObserverP1.awaitTerminalEvent();

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
        testObserverP1.assertValue(users -> users.getData().iterator().next().getUsername().equals(user3.getUsername()));
    }

    @Test
    public void testScimSearch_byDate_paged() {
        final String domain = "domain";
        // create user
        Date now = new Date();
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setCreatedAt(now);
        user1.setUpdatedAt(now);
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setCreatedAt(now);
        user2.setUpdatedAt(now);
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setUsername("testUsername3");
        user3.setCreatedAt(now);
        user3.setUpdatedAt(now);
        userRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteriaName = new FilterCriteria();
        criteriaName.setFilterName("userName");
        criteriaName.setFilterValue("testUsername");
        criteriaName.setOperator("sw");

        FilterCriteria criteriaDate = new FilterCriteria();
        criteriaDate.setFilterName("meta.created");
        criteriaDate.setFilterValue(AbstractDialectHelper.UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(10)));
        criteriaDate.setOperator("gt");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setOperator("and");
        criteria.setFilterComponents(Arrays.asList(criteriaDate, criteriaName));
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 2).test();
        testObserverP1.awaitTerminalEvent();

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byUsername_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setUsername("testUsername3");
        userRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("userName");
        criteria.setFilterValue("testUsername");
        criteria.setOperator("sw");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 2).test();
        testObserverP1.awaitTerminalEvent();

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byGivenName_SW_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "gname2"));
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setUsername("testUsername3");
        user3.setAdditionalInformation(Collections.singletonMap("given_name", "no"));
        userRepository.create(user3).blockingGet();

        // fetch user (page 0)
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname");
        criteria.setOperator("sw");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 1).test();
        testObserverP1.awaitTerminalEvent();

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byGivenName_EQ_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "gname2"));
        userRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname1");
        criteria.setOperator("eq");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));

    }

    @Test
    public void testScimSearch_byGivenName_PR_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("");
        criteria.setOperator("pr");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));

    }

    @Test
    public void testScimSearch_byGivenName_NE_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap("given_name", "gname1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setAdditionalInformation(Collections.singletonMap("given_name", "theother"));
        userRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("name.givenName");
        criteria.setFilterValue("gname1");
        criteria.setOperator("ne");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user2.getUsername()));

    }

    @Test
    public void testFindByDomainAndEmail() throws TechnicalException {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setEmail("test@Test.com");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<List<User>> testObserver = userRepository.findByDomainAndEmail(domain, "test@test.com", true).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 1);
    }

    @Test
    public void testFindByDomainAndEmailWithStandardClaim() throws TechnicalException {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setAdditionalInformation(Collections.singletonMap(StandardClaims.EMAIL, "test@Test.com"));// one UPPER case letter
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<List<User>> testObserver = userRepository.findByDomainAndEmail(domain, "test@test.com", false).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 2);
    }

    private void testSearch_strict(String query) {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setDisplayName("displayName");
        user.setUsername("testUsername");
        user.setEmail("user.name@mail.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setFirstName("firstName2");
        user2.setLastName("lastName2");
        user2.setDisplayName("displayName2");
        user2.setUsername("testUsername2");
        user2.setEmail("user.name@mail.com2");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = userRepository.search(ReferenceType.DOMAIN, domain, query, 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
        testObserver.assertValue(users -> users.getData().iterator().next().getUsername().equals(user.getUsername()));

    }

    private void testSearch_wildcard(String query) {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setDisplayName("displayName");
        user.setUsername("testUsername");
        user.setEmail("user.name@mail.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setFirstName("firstName2");
        user2.setLastName("lastName2");
        user2.setDisplayName("displayName2");
        user2.setUsername("testUsername2");
        user2.setEmail("user.name@mail.com2");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = userRepository.search(ReferenceType.DOMAIN, domain, query, 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 2);
    }


    @Test
    public void testStat_UserRegistration() throws TechnicalException {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setPreRegistration(true);
        user.setRegistrationCompleted(true);
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setPreRegistration(true);
        user2.setRegistrationCompleted(false);
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setPreRegistration(false);
        userRepository.create(user3).blockingGet();

        // fetch user
        AnalyticsQuery query = new AnalyticsQuery();
        query.setField(Field.USER_REGISTRATION);
        query.setDomain(domain);
        TestObserver<Map<Object, Object>> testObserver = userRepository.statistics(query).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 2);
        testObserver.assertValue(users -> users.get("total").equals(Long.valueOf(2)));
        testObserver.assertValue(users -> users.get("completed").equals(Long.valueOf(1)));
    }


    @Test
    public void testStat_StatusRepartition() throws TechnicalException {
        final String domain = "domain_status";
        // enabled used
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEnabled(true);
        userRepository.create(user).blockingGet();

        // disabled used
        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setEnabled(false);
        userRepository.create(user2).blockingGet();

        // locked used
        User user3 = new User();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setAccountNonLocked(false);
        userRepository.create(user3).blockingGet();

        // expired locked user ==> so active one
        User user4 = new User();
        user4.setReferenceType(ReferenceType.DOMAIN);
        user4.setReferenceId(domain);
        user4.setAccountNonLocked(false);
        user4.setAccountLockedUntil(new Date(Instant.now().minusSeconds(60).toEpochMilli()));
        userRepository.create(user4).blockingGet();

        // inactive user
        User user5 = new User();
        user5.setReferenceType(ReferenceType.DOMAIN);
        user5.setReferenceId(domain);
        user5.setLoggedAt(new Date(Instant.now().minus(91, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user5).blockingGet();

        // fetch user
        AnalyticsQuery query = new AnalyticsQuery();
        query.setField(Field.USER_STATUS);
        query.setDomain(domain);
        TestObserver<Map<Object, Object>> testObserver = userRepository.statistics(query).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 4);
        testObserver.assertValue(users -> users.get("active").equals(Long.valueOf(2)));
        testObserver.assertValue(users -> users.get("inactive").equals(Long.valueOf(1)));
        testObserver.assertValue(users -> users.get("disabled").equals(Long.valueOf(1)));
        testObserver.assertValue(users -> users.get("locked").equals(Long.valueOf(1)));
    }

}