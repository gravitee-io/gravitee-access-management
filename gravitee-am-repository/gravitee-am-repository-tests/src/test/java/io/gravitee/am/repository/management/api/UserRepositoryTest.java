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

import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.analytics.AnalyticsQuery;
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
import org.junit.Assume;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.ZoneOffset.UTC;
import static java.util.List.of;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserRepositoryTest extends AbstractManagementTest {
    public static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String ORGANIZATION_ID = "orga#1";
    public static final String CUSTOM_ADDITIONAL_FIELD = "custom";
    @Autowired
    private UserRepository userRepository;

    @Test
    public void testFindByDomain() {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testDomain");
        userRepository.create(user).blockingGet();

        // fetch users
        TestSubscriber<User> testSubscriber = userRepository.findAll(ReferenceType.DOMAIN, "testDomain").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindByUserAndSource() {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setSource("sourceid");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testDomain");
        userRepository.create(user).blockingGet();

        // fetch users
        TestObserver<User> testObserver = userRepository.findByUsernameAndSource(ReferenceType.DOMAIN, "testDomain", user.getUsername(), user.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
    }

    @Test
    public void testFindByUserAndSourceAndLinkedIDP() {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setSource("sourceid");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testDomain");

        userRepository.create(user).blockingGet();

        // create linkUser
        User linkUser = new User();
        linkUser.setId(UUID.randomUUID().toString());
        linkUser.setUsername("testsUsernamelinked");
        linkUser.setSource("sourceidlinked");
        linkUser.setReferenceType(ReferenceType.DOMAIN);
        linkUser.setReferenceId("testDomain");
        UserIdentity identity = getUserIdentity();
        identity.setUserId(linkUser.getId());
        identity.setProviderId(UUID.randomUUID().toString());
        linkUser.setIdentities(of(identity));
        userRepository.create(linkUser).blockingGet();

        // fetch users using linked identity provider id
        TestObserver<User> testObserver = userRepository.findByUsernameAndSource(ReferenceType.DOMAIN, "testDomain", identity.getUsername(), identity.getProviderId(), true).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(linkUser.getUsername()));

        // fetch users using direct source id
        testObserver = userRepository.findByUsernameAndSource(ReferenceType.DOMAIN, "testDomain", user.getUsername(), user.getSource(), true).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
    }

    @Test
    public void testFindAll() {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("testFindByAll");
        userRepository.create(user).blockingGet();

        // fetch users
        TestObserver<Page<User>> testObserver = userRepository.findAll(ReferenceType.DOMAIN, user.getReferenceId(), 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);

        // fetch users
        testObserver = userRepository.findAllScim(ReferenceType.DOMAIN, user.getReferenceId(), 0, 10).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testFindAll_Paging() throws TechnicalException {
        final var REFID = "testFindByAll";
        // create user
        final List<User> users = IntStream.range(0, 10).mapToObj(i -> {
                    User user = new User();
                    user.setUsername(String.format("testsUsername%02d", i));
                    user.setReferenceType(ReferenceType.DOMAIN);
                    user.setReferenceId(REFID);
                    return user;
                }).map(user -> {
                    userRepository.create(user).blockingGet();
                    return user;
                })
                .toList();

        // fetch users multiple time to ensure order is constant
        for (int i = 0; i < 10; i++) {
            TestObserver<Page<User>> testObserver = userRepository.findAll(ReferenceType.DOMAIN, REFID, 0, 10).test();
            testObserver.awaitDone(10, TimeUnit.SECONDS);

            testObserver.assertComplete();
            testObserver.assertNoErrors();
            testObserver.assertValue(result -> result.getData().size() == 10);
            testObserver.assertValue(result -> result.getData().stream().map(User::getUsername).collect(Collectors.joining(","))
                    .equals(users.stream().map(User::getUsername).collect(Collectors.joining(","))));
        }

        // fetch users with size inferior to max result multiple time to ensure order is constant
        for (int i = 0; i < 10; i++) {
            TestObserver<Page<User>> testFirstPageObserver = userRepository.findAll(ReferenceType.DOMAIN, REFID, 0, 2).test();
            testFirstPageObserver.awaitDone(10, TimeUnit.SECONDS);

            testFirstPageObserver.assertComplete();
            testFirstPageObserver.assertNoErrors();
            testFirstPageObserver.assertValue(result -> result.getData().size() == 2);
            testFirstPageObserver.assertValue(result -> result.getData().stream().map(User::getUsername).collect(Collectors.joining(","))
                    .equals(users.subList(0,2).stream().map(User::getUsername).collect(Collectors.joining(","))));
        }

        // fetch users with size inferior to max result multiple time to ensure order is constant
        for (int i = 0; i < 10; i++) {
            TestObserver<Page<User>> testObserverNextPage = userRepository.findAll(ReferenceType.DOMAIN, REFID, 1, 2).test();
            testObserverNextPage.awaitDone(10, TimeUnit.SECONDS);

            testObserverNextPage.assertComplete();
            testObserverNextPage.assertNoErrors();
            testObserverNextPage.assertValue(result -> result.getData().size() == 2);
            testObserverNextPage.assertValue(result -> result.getData().stream().map(User::getUsername).collect(Collectors.joining(","))
                    .equals(users.subList(2,4).stream().map(User::getUsername).collect(Collectors.joining(","))));
        }
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
        testObserver.assertValue(u -> u.getDynamicRoles().containsAll(user.getDynamicRoles()));
        testObserver.assertValue(u -> u.getEntitlements().containsAll(user.getEntitlements()));
        testObserver.assertValue(u -> u.getEmails().size() == 1);
        testObserver.assertValue(u -> u.getPhoneNumbers().size() == 1);
        testObserver.assertValue(u -> u.getPhotos().size() == 1);
        testObserver.assertValue(u -> u.getIms().size() == 1);
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().get(StandardClaims.LOCALE).equals(userCreated.getAdditionalInformation().get(StandardClaims.LOCALE)));
        testObserver.assertValue(u -> u.getPreferredLanguage() == null);
    }

    @Test
    public void testGetPreferredLanguage() {
        // create user
        User user = buildUser();
        user.setPreferredLanguage("fr");
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getPreferredLanguage().equals(userCreated.getPreferredLanguage()));
        testObserver.assertValue(u -> u.getAdditionalInformation().get(StandardClaims.LOCALE).equals(userCreated.getAdditionalInformation().get(StandardClaims.LOCALE)));
    }

    @Test
    public void testFindByIdIn() {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestSubscriber<User> testObserver = userRepository.findByIdIn(Arrays.asList(userCreated.getId())).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByExternalIdAndSource() {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId(), userCreated.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(user.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(user.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(user.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(user.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(user.getExternalId()));
        testObserver.assertValue(u -> u.getRoles().containsAll(user.getRoles()));
        testObserver.assertValue(u -> u.getDynamicRoles().containsAll(user.getDynamicRoles()));
        testObserver.assertValue(u -> u.getEntitlements().containsAll(user.getEntitlements()));
        testObserver.assertValue(u -> u.getEmails().size() == 1);
        testObserver.assertValue(u -> u.getPhoneNumbers().size() == 1);
        testObserver.assertValue(u -> u.getPhotos().size() == 1);
        testObserver.assertValue(u -> u.getIms().size() == 1);
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getLastPasswordReset() != null);
        testObserver.assertValue(u -> u.getFactors().get(0).getChannel() != null);
        testObserver.assertValue(u -> u.getFactors().get(0).getSecurity() != null);
    }

    @Test
    public void shouldNotFindByUnkownExternalIdAndSource() {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findByExternalIdAndSource(userCreated.getReferenceType(), userCreated.getReferenceId(), userCreated.getExternalId()+"unknown", userCreated.getSource()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldUpdate_AllProfile() {
        // create user
        User user = buildUser();
        user.setIdentities(of(getUserIdentity()));
        User userCreated = userRepository.create(user).blockingGet();

        User userUpdated = buildUser();
        UserIdentity userIdentity2 = getUserIdentity();
        userIdentity2.setAdditionalInformation(Map.of("key", "valueUpdated"));
        userUpdated.setIdentities(of(userIdentity2));
        userUpdated.setId(user.getId());


        Address addr = new Address();
        addr.setCountry("gb");
        userUpdated.setAddresses(Arrays.asList(addr));
        userUpdated.setRegistrationCompleted(!userCreated.isRegistrationCompleted());

        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue("updated_val");
        userUpdated.setEmails(Arrays.asList(attribute));
        userUpdated.setPhotos(Arrays.asList(attribute));
        userUpdated.setPhoneNumbers(Arrays.asList(attribute));
        userUpdated.setIms(Arrays.asList(attribute));

        userUpdated.setEntitlements(Arrays.asList("updated_ent"));
        userUpdated.setRoles(Arrays.asList("updated_role"));
        userUpdated.setDynamicRoles(Arrays.asList("updated_dynamic_role"));
        userUpdated.setDynamicGroups(Arrays.asList("updated_dynamic_group"));

        final CommonUserRepository.UpdateActions actions = CommonUserRepository.UpdateActions.build(userCreated, userUpdated);
        Assert.assertTrue(actions.updateAddresses());
        Assert.assertTrue(actions.updateAttributes());
        Assert.assertTrue(actions.updateEntitlements());
        Assert.assertTrue(actions.updateRole());
        Assert.assertTrue(actions.updateDynamicRole());
        Assert.assertTrue(actions.updateDynamicGroup());
        Assert.assertTrue(actions.updateIdentities());

        TestObserver<User> testObserver = userRepository.update(userUpdated, actions).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // perform a find to check the DB content
        testObserver = userRepository.findById(user.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(u -> u.getUsername().equals(userUpdated.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(userUpdated.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(userUpdated.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(userUpdated.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(userUpdated.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(userUpdated.getExternalId()));
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getAdditionalInformation().get(StandardClaims.EMAIL).equals(userUpdated.getAdditionalInformation().get(StandardClaims.EMAIL)));
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getFactors().get(0).getAppId().equals(userUpdated.getFactors().get(0).getAppId()));
        testObserver.assertValue(u -> u.getLastPasswordReset() != null);
        testObserver.assertValue(u -> u.isRegistrationCompleted() == userUpdated.isRegistrationCompleted());
        // Should have changed too
        testObserver.assertValue(u -> Objects.equals(u.getRoles(), userUpdated.getRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicRoles(), userUpdated.getDynamicRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicGroups(), userUpdated.getDynamicGroups()));
        testObserver.assertValue(u -> Objects.equals(u.getEntitlements(), userUpdated.getEntitlements()));
        testObserver.assertValue(u -> Objects.equals(u.getEmails(), userUpdated.getEmails()));
        testObserver.assertValue(u -> Objects.equals(u.getPhoneNumbers(), userUpdated.getPhoneNumbers()));
        testObserver.assertValue(u -> Objects.equals(u.getPhotos(), userUpdated.getPhotos()));
        testObserver.assertValue(u -> Objects.equals(u.getIms(), userUpdated.getIms()));
        testObserver.assertValue(u -> Objects.equals(u.getAddresses(), userUpdated.getAddresses()));
    }

    @Test
    public void shouldUpdate_ProfileOnly() {
        Assume.assumeTrue(userRepository.getClass().getSimpleName().equals("JdbcUserRepository"));

        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        User userUpdated = buildUser();
        userUpdated.setIdentities(of(getUserIdentity()));
        userUpdated.setId(user.getId());
        userUpdated.setRegistrationCompleted(!userCreated.isRegistrationCompleted());


        Address addr = new Address();
        addr.setCountry("gb");
        userUpdated.setAddresses(Arrays.asList(addr));

        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue("updated_val");
        userUpdated.setEmails(Arrays.asList(attribute));
        userUpdated.setPhotos(Arrays.asList(attribute));
        userUpdated.setPhoneNumbers(Arrays.asList(attribute));
        userUpdated.setIms(Arrays.asList(attribute));

        userUpdated.setEntitlements(Arrays.asList("updated_ent"));
        userUpdated.setRoles(Arrays.asList("updated_role"));
        userUpdated.setDynamicRoles(Arrays.asList("updated_dynamic_role"));
        userUpdated.setDynamicGroups(Arrays.asList("updated_dynamic_group"));

        // compare with same object to consider addr, attr, roles... sa unchanged
        final CommonUserRepository.UpdateActions actions = CommonUserRepository.UpdateActions.none();
        Assert.assertFalse(actions.updateAddresses());
        Assert.assertFalse(actions.updateAttributes());
        Assert.assertFalse(actions.updateEntitlements());
        Assert.assertFalse(actions.updateRole());
        Assert.assertFalse(actions.updateDynamicRole());
        Assert.assertFalse(actions.updateDynamicGroup());
        Assert.assertFalse(actions.updateIdentities());


        TestObserver<User> testObserver = userRepository.update(userUpdated, actions).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // perform a find to check the DB content
        testObserver = userRepository.findById(user.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(u -> u.getUsername().equals(userUpdated.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(userUpdated.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(userUpdated.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(userUpdated.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(userUpdated.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(userUpdated.getExternalId()));
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getAdditionalInformation().get(StandardClaims.EMAIL).equals(userUpdated.getAdditionalInformation().get(StandardClaims.EMAIL)));
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getFactors().get(0).getAppId().equals(userUpdated.getFactors().get(0).getAppId()));
        testObserver.assertValue(u -> u.getLastPasswordReset() != null);
        testObserver.assertValue(u -> u.isRegistrationCompleted() == userUpdated.isRegistrationCompleted());
        // Shouldn't have changed
        testObserver.assertValue(u -> Objects.equals(u.getRoles(), user.getRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicRoles(), user.getDynamicRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicGroups(), user.getDynamicGroups()));
        testObserver.assertValue(u -> Objects.equals(u.getIdentities(), Optional.ofNullable(user.getIdentities()).orElse(of())));
        testObserver.assertValue(u -> Objects.equals(u.getEntitlements(), user.getEntitlements()));
        testObserver.assertValue(u -> Objects.equals(u.getEmails(), user.getEmails()));
        testObserver.assertValue(u -> Objects.equals(u.getPhoneNumbers(), user.getPhoneNumbers()));
        testObserver.assertValue(u -> Objects.equals(u.getPhotos(), user.getPhotos()));
        testObserver.assertValue(u -> Objects.equals(u.getIms(), user.getIms()));
        testObserver.assertValue(u -> Objects.equals(u.getAddresses(), user.getAddresses()));
    }

    @Test
    public void shouldUpdate_GroupOnly() {
        Assume.assumeTrue(userRepository.getClass().getSimpleName().equals("JdbcUserRepository"));

        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        User userUpdated = buildUser();
        userUpdated.setIdentities(of(getUserIdentity()));
        userUpdated.setId(user.getId());
        userUpdated.setRegistrationCompleted(!userCreated.isRegistrationCompleted());


        Address addr = new Address();
        addr.setCountry("gb");
        userUpdated.setAddresses(Arrays.asList(addr));

        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue("updated_val");
        userUpdated.setEmails(Arrays.asList(attribute));
        userUpdated.setPhotos(Arrays.asList(attribute));
        userUpdated.setPhoneNumbers(Arrays.asList(attribute));
        userUpdated.setIms(Arrays.asList(attribute));

        userUpdated.setEntitlements(Arrays.asList("updated_ent"));
        userUpdated.setRoles(Arrays.asList("updated_role"));
        userUpdated.setDynamicRoles(Arrays.asList("updated_dynamic_role"));
        userUpdated.setDynamicGroups(Arrays.asList("updated_dynamic_group"));

        // compare with same object to consider addr, attr, roles... sa unchanged
        final CommonUserRepository.UpdateActions actions = CommonUserRepository.UpdateActions.none().updateDynamicGroup(true);
        Assert.assertFalse(actions.updateAddresses());
        Assert.assertFalse(actions.updateAttributes());
        Assert.assertFalse(actions.updateEntitlements());
        Assert.assertFalse(actions.updateRole());
        Assert.assertFalse(actions.updateDynamicRole());
        Assert.assertFalse(actions.updateIdentities());
        Assert.assertTrue(actions.updateDynamicGroup());


        TestObserver<User> testObserver = userRepository.update(userUpdated, actions).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // perform a find to check the DB content
        testObserver = userRepository.findById(user.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(u -> u.getUsername().equals(userUpdated.getUsername()));
        testObserver.assertValue(u -> u.getDisplayName().equals(userUpdated.getDisplayName()));
        testObserver.assertValue(u -> u.getNickName().equals(userUpdated.getNickName()));
        testObserver.assertValue(u -> u.getFirstName().equals(userUpdated.getFirstName()));
        testObserver.assertValue(u -> u.getEmail().equals(userUpdated.getEmail()));
        testObserver.assertValue(u -> u.getExternalId().equals(userUpdated.getExternalId()));
        testObserver.assertValue(u -> u.getX509Certificates().size() == 1);
        testObserver.assertValue(u -> u.getAdditionalInformation().size() == 3);
        testObserver.assertValue(u -> u.getAdditionalInformation().get(StandardClaims.EMAIL).equals(userUpdated.getAdditionalInformation().get(StandardClaims.EMAIL)));
        testObserver.assertValue(u -> u.getFactors().size() == 1);
        testObserver.assertValue(u -> u.getFactors().get(0).getAppId().equals(userUpdated.getFactors().get(0).getAppId()));
        testObserver.assertValue(u -> u.getLastPasswordReset() != null);
        testObserver.assertValue(u -> u.isRegistrationCompleted() == userUpdated.isRegistrationCompleted());
        // Shouldn't have changed
        testObserver.assertValue(u -> Objects.equals(u.getRoles(), user.getRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicRoles(), user.getDynamicRoles()));
        testObserver.assertValue(u -> Objects.equals(u.getDynamicGroups(), userUpdated.getDynamicGroups()));
        testObserver.assertValue(u -> Objects.equals(u.getIdentities(), Optional.ofNullable(user.getIdentities()).orElse(of())));
        testObserver.assertValue(u -> Objects.equals(u.getEntitlements(), user.getEntitlements()));
        testObserver.assertValue(u -> Objects.equals(u.getEmails(), user.getEmails()));
        testObserver.assertValue(u -> Objects.equals(u.getPhoneNumbers(), user.getPhoneNumbers()));
        testObserver.assertValue(u -> Objects.equals(u.getPhotos(), user.getPhotos()));
        testObserver.assertValue(u -> Objects.equals(u.getIms(), user.getIms()));
        testObserver.assertValue(u -> Objects.equals(u.getAddresses(), user.getAddresses()));
    }

    private User buildUser() {
        User user = new User();
        final String random = UUID.randomUUID().toString();
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
        user.setMfaEnrollmentSkippedAt(new Date());
        user.setCredentialsNonExpired(true);
        user.setDisplayName("display"+random);
        user.setEnabled(true);
        user.setExternalId("external"+random);
        user.setInternal(false);
        user.setLastName("last"+random);
        user.setLoggedAt(new Date());
        user.setLastPasswordReset(new Date());
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
        user.setDynamicRoles(Arrays.asList("dynamic_role"+random));
        user.setDynamicGroups(Arrays.asList("dynamic_group"+random));

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
        info.put(StandardClaims.LOCALE, "en");
        info.put(CUSTOM_ADDITIONAL_FIELD, "custom-value");
        user.setAdditionalInformation(info);
        return user;
    }

    @Test
    public void testFindById_referenceType() {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORGANIZATION_ID);
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(Reference.organization(ORGANIZATION_ID), userCreated.getFullId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals("testsUsername"));
    }

    @Test
    public void testNotFoundById() {
        var observer = userRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        user.setAdditionalInformation(Collections.singletonMap("email", "johndoe@test.com"));
        TestObserver<User> testObserver = userRepository.create(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()) && u.getAdditionalInformation().containsKey("email"));
    }

    @Test
    public void testCreate_withIdentities() {

        UserIdentity userIdentity = getUserIdentity();

        User user = buildUser();
        user.setIdentities(Collections.singletonList(userIdentity));
        User userCreated = userRepository.create(user).blockingGet();
        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getIdentities() != null);
        testObserver.assertValue(u -> userIdentity.getUserId().equals(u.getIdentities().get(0).getUserId()));
        testObserver.assertValue(u -> "value".equals(u.getIdentities().get(0).getAdditionalInformation().get("key")));
    }

    private static UserIdentity getUserIdentity() {
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId("userId");
        userIdentity.setUsername(UUID.randomUUID().toString());
        userIdentity.setProviderId("providerId");
        userIdentity.setLinkedAt(new Date());
        userIdentity.setAdditionalInformation(Collections.singletonMap("key", "value"));
        return userIdentity;
    }

    @Test
    public void testUpdate() {
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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(updatedUser.getUsername()));
    }

    @Test
    public void testUpdate_withIdentities() {
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testUsername");
        User userCreated = userRepository.create(user).blockingGet();

        UserIdentity userIdentity = getUserIdentity();

        User updatedUser = new User();
        updatedUser.setReferenceType(ReferenceType.DOMAIN);
        updatedUser.setReferenceId("domainId");
        updatedUser.setId(userCreated.getId());
        updatedUser.setUsername("testUsername");
        updatedUser.setIdentities(Collections.singletonList(userIdentity));

        userRepository.update(updatedUser).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getIdentities() != null);
        testObserver.assertValue(u -> userIdentity.getUserId().equals(u.getIdentities().get(0).getUserId()));
        testObserver.assertValue(u -> "value".equals(u.getIdentities().get(0).getAdditionalInformation().get("key")));
    }

    @Test
    public void testDelete() {
        // create user
        User user = buildUser();
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));

        // delete user
        TestObserver testObserver1 = userRepository.delete(userCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch user
        testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void testDeleteByRef() {
        final String DOMAIN_1 = "domain1";
        final String DOMAIN_2 = "domain2";

        // create user
        User user = buildUser();
        user.setReferenceId(DOMAIN_1);
        user.setReferenceType(ReferenceType.DOMAIN);
        userRepository.create(user).blockingGet();

        user = buildUser();
        user.setReferenceId(DOMAIN_1);
        user.setReferenceType(ReferenceType.DOMAIN);
        userRepository.create(user).blockingGet();

        user = buildUser();
        user.setReferenceId(DOMAIN_2);
        user.setReferenceType(ReferenceType.DOMAIN);
        userRepository.create(user).blockingGet();

        final long usersDomain1 = userRepository.findAll(ReferenceType.DOMAIN, DOMAIN_1).count().blockingGet();
        Assert.assertEquals("Domain1 should have 2 users", 2, usersDomain1);
        long usersDomain2 = userRepository.findAll(ReferenceType.DOMAIN, DOMAIN_2).count().blockingGet();
        Assert.assertEquals("Domain2 should have 1 users", 1, usersDomain2);

        // delete user
        TestObserver testObserver1 = userRepository.deleteByReference(ReferenceType.DOMAIN, DOMAIN_1).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch user
        final TestSubscriber<User> find = userRepository.findAll(ReferenceType.DOMAIN, DOMAIN_1).test();
        find.awaitDone(10, TimeUnit.SECONDS);
        find.assertNoValues();

        usersDomain2 = userRepository.findAll(ReferenceType.DOMAIN, DOMAIN_2).count().blockingGet();
        Assert.assertEquals("Domain2 should have 1 users", 1, usersDomain2);

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
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);
        testObserverP0.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername()) && it.next().getUsername().equals(user2.getUsername());
        });

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, "testUsername*", 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

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
        criteriaName.setQuoteFilterValue(true);

        FilterCriteria criteriaDate = new FilterCriteria();
        criteriaDate.setFilterName("meta.created");
        criteriaDate.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(10)));
        criteriaDate.setOperator("gt");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setOperator("and");
        criteria.setFilterComponents(Arrays.asList(criteriaDate, criteriaName));
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byDate_paged_by_Offset() {
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

        // fetch user (offset 0)
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
        TestObserver<Page<User>> testObserverOffset0 = userRepository.searchScim(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverOffset0.awaitDone(10, TimeUnit.SECONDS);

        testObserverOffset0.assertComplete();
        testObserverOffset0.assertNoErrors();
        testObserverOffset0.assertValue(users -> users.getData().size() == 3);

        final var allValues = testObserverOffset0.values().get(0).getData().stream().toList();

        // fetch user (offset 1)
        TestObserver<Page<User>> testObserverOffset1 = userRepository.searchScim(ReferenceType.DOMAIN, domain, criteria, 1, 2).test();
        testObserverOffset1.awaitDone(10, TimeUnit.SECONDS);

        testObserverOffset1.assertComplete();
        testObserverOffset1.assertNoErrors();
        testObserverOffset1.assertValue(users -> users.getData().size() == 2);

        // startIndex = 1 so the first element of the allValue is skipped
        testObserverOffset1.assertValue(users -> users.getData().stream().toList().get(0).getId().equals(allValues.get(1).getId()));
        testObserverOffset1.assertValue(users -> users.getData().stream().toList().get(1).getId().equals(allValues.get(2).getId()));

        // fetch user (offset 2)
        TestObserver<Page<User>> testObserverOffset2 = userRepository.searchScim(ReferenceType.DOMAIN, domain, criteria, 2, 2).test();
        testObserverOffset2.awaitDone(10, TimeUnit.SECONDS);

        testObserverOffset2.assertComplete();
        testObserverOffset2.assertNoErrors();
        testObserverOffset2.assertValue(users -> users.getData().size() == 1);
        testObserverOffset2.assertValue(users -> users.getData().stream().toList().get(0).getId().equals(allValues.get(2).getId()));

    }

    @Test
    public void testScimSearch_byEmail_withSpecialChars() {
        final String domain = "domain";

        final User user1 = CreateUser("testUsername1", "test+admin@email.com", domain);
        userRepository.create(user1).blockingGet();

        User user2 = CreateUser("testUsername2", "test.admin@email.com", domain);
        userRepository.create(user2).blockingGet();

        FilterCriteria criteriaEmailPlusChar = CreateFilter("email", "+admin@email.com", "ew");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteriaEmailPlusChar, 0, 2).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();

        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getEmail().equals(user1.getEmail());
        });

        FilterCriteria criteriaEmailPlusPeriod = CreateFilter("email", ".admin@email.com", "ew");
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteriaEmailPlusPeriod, 0, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();

        testObserverP1.assertValue(users -> users.getData().size() == 1);
        testObserverP1.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getEmail().equals(user2.getEmail());
        });

    }

    @Test
    public void testScimSearch_Field_created() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setCreatedAt(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setCreatedAt(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();

        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.created");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        final TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
    }

    @Test
    public void testScimSearch_Field_loggedAt() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setLoggedAt(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setLoggedAt(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();


        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.loggedAt");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        final TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
    }

    @Test
    public void testScrimSearch_field_lastPasswordReset() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setLastPasswordReset(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setLastPasswordReset(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();

        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.lastPasswordReset");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        final TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
    }

    @Test
    public void testScimSearch_field_mfaEnrollmentSkippedAt() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setMfaEnrollmentSkippedAt(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setMfaEnrollmentSkippedAt(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();


        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.mfaEnrollmentSkippedAt");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
    }

    @Test
    public void scrimSearch_field_accountLockedAt() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAccountLockedAt(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setAccountLockedAt(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();

        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.accountLockedAt");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        final TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
    }

    @Test
    public void testScimSearch_field_accountLockedUntil() {
        final String domain = "domain";
        final Date now = new Date();

        final User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAccountLockedUntil(now);
        userRepository.create(user1).blockingGet();

        final User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setUsername("testUsername2");
        user2.setAccountLockedUntil(new Date(TimeUnit.MICROSECONDS.toMillis(1556175797428L))); //April 25, 2019
        userRepository.create(user2).blockingGet();


        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("meta.accountLockedUntil");
        criteria.setFilterValue(UTC_FORMATTER.format(LocalDateTime.now(UTC).minusSeconds(20)));
        criteria.setOperator("gt");

        final TestObserver<Page<User>> observer = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(users -> users.getData().size() == 1);
        observer.assertValue(users -> {
            Iterator<User> it = users.getData().iterator();
            return it.next().getUsername().equals(user1.getUsername());
        });
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
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 3);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 2).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
    }

    @Test
    public void testScimSearch_byUsername_NotPaged() {
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
        criteria.setQuoteFilterValue(true);
        final TestSubscriber<User> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValueCount(3);
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
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 1, 1).test();
        testObserverP1.awaitDone(10, TimeUnit.SECONDS);

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
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));

    }

    @Test
    public void testScimSearch_byCustomField_EQ_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap(CUSTOM_ADDITIONAL_FIELD, "custom-value1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setAdditionalInformation(Collections.singletonMap(CUSTOM_ADDITIONAL_FIELD, "custom-value2"));
        userRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("additionalInformation." + CUSTOM_ADDITIONAL_FIELD);
        criteria.setFilterValue("custom-value1");
        criteria.setOperator("eq");
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user1.getUsername()));
    }

    @Test
    public void testScimSearch_byCustomField_PR_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setReferenceType(ReferenceType.DOMAIN);
        user1.setReferenceId(domain);
        user1.setUsername("testUsername1");
        user1.setAdditionalInformation(Collections.singletonMap(CUSTOM_ADDITIONAL_FIELD, "custom-value1"));
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        userRepository.create(user2).blockingGet();

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName("additionalInformation." + CUSTOM_ADDITIONAL_FIELD);
        criteria.setFilterValue("");
        criteria.setOperator("pr");
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

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
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

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
        criteria.setQuoteFilterValue(true);
        TestObserver<Page<User>> testObserverP0 = userRepository.search(ReferenceType.DOMAIN, domain, criteria, 0, 4).test();
        testObserverP0.awaitDone(10, TimeUnit.SECONDS);

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 1);
        testObserverP0.assertValue(users -> users.getData().iterator().next().getUsername().equals(user2.getUsername()));

    }

    @Test
    public void testFindByDomainAndEmail() {
        final String domain = "domain";
        // create user
        User user = createUserWithRandomName();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = createUserWithRandomName();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setEmail("test@Test.com");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestSubscriber<User> testSubscriber = userRepository.findByDomainAndEmail(domain, "test@test.com", true).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindByDomainAndEmailWithStandardClaim() {
        final String domain = "domain";
        // create user
        User user = createUserWithRandomName();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = createUserWithRandomName();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setAdditionalInformation(Collections.singletonMap(StandardClaims.EMAIL, "test@Test.com"));// one UPPER case letter
        userRepository.create(user2).blockingGet();

        // fetch user
        TestSubscriber<User> testSubscriber = userRepository.findByDomainAndEmail(domain, "test@test.com", false).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 2);
    }


    @Test
    public void testStat_UserRegistration() {
        final String domain = "domain";
        // create user
        User user = createUserWithRandomName();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setPreRegistration(true);
        user.setRegistrationCompleted(true);
        userRepository.create(user).blockingGet();

        User user2 = createUserWithRandomName();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setPreRegistration(true);
        user2.setRegistrationCompleted(false);
        userRepository.create(user2).blockingGet();

        User user3 = createUserWithRandomName();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setPreRegistration(false);
        userRepository.create(user3).blockingGet();

        // fetch user
        AnalyticsQuery query = new AnalyticsQuery();
        query.setField(Field.USER_REGISTRATION);
        query.setDomain(domain);
        TestObserver<Map<Object, Object>> testObserver = userRepository.statistics(query).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 2);
        testObserver.assertValue(users -> ((Number)users.get("total")).intValue() == 2);
        testObserver.assertValue(users -> ((Number)users.get("completed")).intValue() == 1);
    }


    @Test
    public void testStat_StatusRepartition() {
        final String domain = "domain_status";
        // enabled used
        User user = createUserWithRandomName();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEnabled(true);
        user.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user).blockingGet();

        // disabled used
        User user2 = createUserWithRandomName();
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setEnabled(false);
        user2.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user2).blockingGet();

        // locked used
        User user3 = createUserWithRandomName();
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setAccountNonLocked(false);
        user3.setAccountLockedUntil(new Date(Instant.now().plusSeconds(60).toEpochMilli()));
        user3.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user3).blockingGet();

        // expired locked user ==> so active one
        User user4 = createUserWithRandomName();
        user4.setReferenceType(ReferenceType.DOMAIN);
        user4.setReferenceId(domain);
        user4.setAccountNonLocked(false);
        user4.setAccountLockedUntil(new Date(Instant.now().minusSeconds(60).toEpochMilli()));
        user4.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user4).blockingGet();

        // inactive user
        User user5 =createUserWithRandomName();
        user5.setReferenceType(ReferenceType.DOMAIN);
        user5.setReferenceId(domain);
        user5.setLoggedAt(new Date(Instant.now().minus(91, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user5).blockingGet();

        // fetch user
        AnalyticsQuery query = new AnalyticsQuery();
        query.setField(Field.USER_STATUS);
        query.setDomain(domain);
        TestObserver<Map<Object, Object>> testObserver = userRepository.statistics(query).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 4);
        testObserver.assertValue(users -> ((Number)users.get("active")).intValue() == 2);
        testObserver.assertValue(users -> ((Number)users.get("inactive")).intValue() == 1);
        testObserver.assertValue(users -> ((Number)users.get("disabled")).intValue() == 1);
        testObserver.assertValue(users -> ((Number)users.get("locked")).intValue() == 1);
    }

    @Test
    public void testStat_StatusRepartition_byClient() {
        final String domain = "domain_status";
        final String clientId1 = UUID.randomUUID().toString();;
        final String clientId2 = UUID.randomUUID().toString();;

        // enabled used
        User user = createUserWithRandomName();
        user.setClient(clientId1);
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setEnabled(true);
        user.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user).blockingGet();

        // disabled used
        User user2 = createUserWithRandomName();
        user2.setClient(clientId1);
        user2.setReferenceType(ReferenceType.DOMAIN);
        user2.setReferenceId(domain);
        user2.setEnabled(false);
        user2.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user2).blockingGet();

        // locked used
        User user3 = createUserWithRandomName();
        user3.setClient(clientId1);
        user3.setReferenceType(ReferenceType.DOMAIN);
        user3.setReferenceId(domain);
        user3.setAccountNonLocked(false);
        user3.setAccountLockedUntil(new Date(Instant.now().plusSeconds(60).toEpochMilli()));
        user3.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user3).blockingGet();

        // expired locked user ==> so active one
        User user4 = createUserWithRandomName();
        user4.setClient(clientId2);
        user4.setReferenceType(ReferenceType.DOMAIN);
        user4.setReferenceId(domain);
        user4.setAccountNonLocked(false);
        user4.setAccountLockedUntil(new Date(Instant.now().minusSeconds(60).toEpochMilli()));
        user4.setLoggedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user4).blockingGet();

        // inactive user
        User user5 = createUserWithRandomName();
        user5.setClient(clientId2);
        user5.setReferenceType(ReferenceType.DOMAIN);
        user5.setReferenceId(domain);
        user5.setLoggedAt(new Date(Instant.now().minus(91, ChronoUnit.DAYS).toEpochMilli()));
        userRepository.create(user5).blockingGet();

        // fetch for clientId1
        AnalyticsQuery query1 = new AnalyticsQuery();
        query1.setField(Field.USER_STATUS);
        query1.setDomain(domain);
        query1.setApplication(clientId1);
        TestObserver<Map<Object, Object>> testObserver1 = userRepository.statistics(query1).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(users -> users.size() == 4);
        testObserver1.assertValue(users -> ((Number)users.get("active")).intValue() == 1);
        testObserver1.assertValue(users -> ((Number)users.get("inactive")).intValue() == 0);
        testObserver1.assertValue(users -> ((Number)users.get("disabled")).intValue() == 1);
        testObserver1.assertValue(users -> ((Number)users.get("locked")).intValue() == 1);

        // fetch for clientId2
        AnalyticsQuery query2 = new AnalyticsQuery();
        query2.setField(Field.USER_STATUS);
        query2.setDomain(domain);
        query2.setApplication(clientId2);
        TestObserver<Map<Object, Object>> testObserver2 = userRepository.statistics(query2).test();
        testObserver2.awaitDone(10, TimeUnit.SECONDS);

        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(users -> users.size() == 4);
        testObserver2.assertValue(users -> ((Number)users.get("active")).intValue() == 1);
        testObserver2.assertValue(users -> ((Number)users.get("inactive")).intValue() == 1);
        testObserver2.assertValue(users -> ((Number)users.get("disabled")).intValue() == 0);
        testObserver2.assertValue(users -> ((Number)users.get("locked")).intValue() == 0);
    }

    private User createUserWithRandomName(){
        User user = new User();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        user.setUsername("testUser" + uuid);
        return user;
    }

    private User CreateUser(String username, String email, String domain){
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(domain);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private FilterCriteria CreateFilter(String name, String value, String operator) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName(name);
        criteria.setFilterValue(value);
        criteria.setOperator(operator);
        criteria.setQuoteFilterValue(true);

        return criteria;
    }
}
