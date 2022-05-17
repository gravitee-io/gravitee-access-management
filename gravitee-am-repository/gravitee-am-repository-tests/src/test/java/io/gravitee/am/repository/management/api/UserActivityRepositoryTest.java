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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.management.AbstractManagementTest;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.Objects.nonNull;
import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityRepositoryTest extends AbstractManagementTest {

    @Autowired
    private UserActivityRepository userActivityRepository;

    @Test
    public void must_find_by_id() {
        final String id = UUID.randomUUID().toString();
        final String key = "key-" + UUID.randomUUID();
        var userActivity = buildUserActivity(id, key, "domainId");
        var createdUserActivity = userActivityRepository.create(userActivity).blockingGet();
        var testSubscriber = userActivityRepository.findById(id).test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValue(ua -> createdUserActivity.getId().equals(ua.getId()));
        testSubscriber.assertValue(ua -> createdUserActivity.getUserActivityKey().equals(ua.getUserActivityKey()));
        testSubscriber.assertValue(ua -> createdUserActivity.getUserActivityType().equals(ua.getUserActivityType()));
        testSubscriber.assertValue(ua -> createdUserActivity.getReferenceType().equals(ua.getReferenceType()));
        testSubscriber.assertValue(ua -> createdUserActivity.getReferenceId().equals(ua.getReferenceId()));
        testSubscriber.assertValue(ua -> createdUserActivity.getLatitude().equals(ua.getLatitude()));
        testSubscriber.assertValue(ua -> createdUserActivity.getLongitude().equals(ua.getLongitude()));
        testSubscriber.assertValue(ua -> createdUserActivity.getUserAgent().equals(ua.getUserAgent()));
        testSubscriber.assertValue(ua -> createdUserActivity.getLoginAttempts().equals(ua.getLoginAttempts()));
        testSubscriber.assertValue(ua -> nonNull(ua.getCreatedAt()));
        testSubscriber.assertValue(ua -> nonNull(ua.getExpireAt()));
    }

    @Test
    public void must_not_find_by_id() {
        final String id = UUID.randomUUID().toString();
        var testSubscriber = userActivityRepository.findById(id).test();

        testSubscriber.assertEmpty();
    }

    @Test
    public void must_not_find_by_id_expired() {
        final String id = UUID.randomUUID().toString();
        final String key = "key-" + UUID.randomUUID();
        var userActivity = buildUserActivity(id, key, "domainId");
        userActivity.setExpireAt(new Date(System.currentTimeMillis() - 1000));
        userActivityRepository.create(userActivity).blockingGet();
        var testSubscriber = userActivityRepository.findById(id).test();

        testSubscriber.assertEmpty();
    }

    @Test
    public void must_create_user_activity() {
        final String key = "key-" + UUID.randomUUID();
        var userActivity = buildUserActivity(UUID.randomUUID().toString(), key, "domainId");
        var testSubscriber = userActivityRepository.create(userActivity).test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();

        testSubscriber.assertValue(ua -> nonNull(ua.getId()));
        testSubscriber.assertValue(ua -> userActivity.getUserActivityKey().equals(ua.getUserActivityKey()));
        testSubscriber.assertValue(ua -> userActivity.getUserActivityType().equals(ua.getUserActivityType()));
        testSubscriber.assertValue(ua -> userActivity.getReferenceType().equals(ua.getReferenceType()));
        testSubscriber.assertValue(ua -> userActivity.getReferenceId().equals(ua.getReferenceId()));
        testSubscriber.assertValue(ua -> userActivity.getLatitude().equals(ua.getLatitude()));
        testSubscriber.assertValue(ua -> userActivity.getLongitude().equals(ua.getLongitude()));
        testSubscriber.assertValue(ua -> userActivity.getUserAgent().equals(ua.getUserAgent()));
        testSubscriber.assertValue(ua -> userActivity.getLoginAttempts().equals(ua.getLoginAttempts()));
        testSubscriber.assertValue(ua -> nonNull(ua.getCreatedAt()));
        testSubscriber.assertValue(ua -> nonNull(ua.getExpireAt()));    }

    @Test
    public void must_update_user_activity() {
        final String id = UUID.randomUUID().toString();
        final String key = "key-" + UUID.randomUUID();
        var userActivity = buildUserActivity(id, key, "domainId");
        var createdUser = userActivityRepository.create(userActivity).blockingGet();

        var activityToUpdate = copy(userActivity)
                .setLatitude(randomCoordinate(90))
                .setLongitude(randomCoordinate(180));

        var testSubscriber = userActivityRepository.update(activityToUpdate).test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();

        testSubscriber.assertValue(ua -> !createdUser.equals(ua));
    }

    @Test
    public void must_find_by_domain_and_key() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.create(buildUserActivity(null, "key-2", "domainId2")).blockingGet();
        var testSubscriber = userActivityRepository.findByDomainAndTypeAndKey("domainId", Type.LOGIN, key).test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
    }

    @Test
    public void must_find_by_domain_and_key_limit_1() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.create(buildUserActivity(null, "key-2", "domainId2")).blockingGet();
        var testSubscriber = userActivityRepository.findByDomainAndTypeAndKeyAndLimit("domainId", Type.LOGIN, key, 1).test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void must_not_find_by_domain_and_key_expired() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId").setExpireAt(new Date(System.currentTimeMillis() - 1000))).blockingGet();
        userActivityRepository.create(buildUserActivity(null, key, "domainId").setExpireAt(new Date(System.currentTimeMillis() - 1000))).blockingGet();
        userActivityRepository.create(buildUserActivity(null, "key-2", "domainId2")).blockingGet();
        var testSubscriber = userActivityRepository.findByDomainAndTypeAndKey("domainId", Type.LOGIN, key).test();

        testSubscriber.assertEmpty();
    }

    @Test
    public void must_delete_by_id() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.delete(key).test().awaitTerminalEvent();

        userActivityRepository.findById(key).test().assertEmpty();
    }

    @Test
    public void must_delete_by_domain_and_key() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.deleteByDomainAndKey("domainId", key).test().awaitTerminalEvent();

        userActivityRepository.findByDomainAndTypeAndKey("domain", Type.LOGIN, key).test().assertEmpty();
    }

    @Test
    public void must_delete_by_domain() {
        final String key = "key-" + UUID.randomUUID();
        userActivityRepository.create(buildUserActivity(null, key, "domainId")).blockingGet();
        userActivityRepository.deleteByDomain("domainId").test().awaitTerminalEvent();

        userActivityRepository.findByDomainAndTypeAndKey("domainId", Type.LOGIN, key).test().assertEmpty();
    }

    public UserActivity buildUserActivity(String id, String key, String refId) {
        final ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC());
        var instant = now.toInstant();
        return new UserActivity()
                .setId(id)
                .setUserActivityType(Type.LOGIN)
                .setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(refId)
                .setUserActivityKey(key)
                .setLatitude(randomCoordinate(90))
                .setLongitude(randomCoordinate(180))
                .setUserAgent("Gravitee.io UserAgent Client/1.0")
                .setLoginAttempts(current().nextInt(0, 50))
                .setCreatedAt(new Date(instant.toEpochMilli()))
                .setExpireAt(new Date(instant.toEpochMilli() + 3600 * 24));
    }

    public UserActivity copy(UserActivity ua) {
        return new UserActivity()
                .setId(ua.getId())
                .setUserActivityType(ua.getUserActivityType())
                .setReferenceType(ua.getReferenceType())
                .setReferenceId(ua.getReferenceId())
                .setUserActivityKey(ua.getUserActivityKey())
                .setLatitude(ua.getLatitude())
                .setLongitude(ua.getLongitude())
                .setUserAgent(ua.getUserAgent())
                .setCreatedAt(ua.getCreatedAt())
                .setExpireAt(ua.getExpireAt());
    }

    private double randomCoordinate(int i) {
        return current().nextDouble(-i, i);
    }
}
