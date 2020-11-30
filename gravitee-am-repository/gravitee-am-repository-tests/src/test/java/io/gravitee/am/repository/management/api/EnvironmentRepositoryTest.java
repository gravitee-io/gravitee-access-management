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

import io.gravitee.am.model.Environment;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnvironmentRepositoryTest extends AbstractManagementTest {

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Test
    public void testFindById() {
        Environment environment = new Environment();
        environment.setName("testName");
        environment.setDescription("testDescription");
        environment.setCreatedAt(new Date());
        environment.setUpdatedAt(environment.getUpdatedAt());
        environment.setOrganizationId(UUID.randomUUID().toString());
        environment.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));

        // TODO: find another way to inject data in DB. Avoid to rely on class under test for that.
        Environment envCreated = environmentRepository.create(environment).blockingGet();

        TestObserver<Environment> obs = environmentRepository.findById(envCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getId().equals(envCreated.getId()));
        obs.assertValue(o -> o.getName().equals(environment.getName()));
        obs.assertValue(o -> o.getDescription().equals(environment.getDescription()));
        obs.assertValue(o -> o.getOrganizationId().equals(environment.getOrganizationId()));
        obs.assertValue(o -> o.getDomainRestrictions().containsAll(environment.getDomainRestrictions()));
    }

    @Test
    public void testNotFoundById() {
        environmentRepository.findById("unknown").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Environment env = new Environment();
        env.setOrganizationId(UUID.randomUUID().toString());
        env.setName("testName");

        TestObserver<Environment> obs = environmentRepository.create(env).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(env.getName()) && o.getId() != null);
        obs.assertValue(o -> o.getOrganizationId().equals(env.getOrganizationId()) && o.getId() != null);
    }

    @Test
    public void testUpdate() {
        Environment env = new Environment();
        env.setName("testName");
        env.setDescription("testDescription");
        env.setCreatedAt(new Date());
        env.setUpdatedAt(env.getUpdatedAt());
        env.setOrganizationId(UUID.randomUUID().toString());
        env.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));

        Environment envCreated = environmentRepository.create(env).blockingGet();

        Environment envUpdated = new Environment();
        envUpdated.setId(envCreated.getId());
        envUpdated.setOrganizationId(env.getOrganizationId());
        envUpdated.setName("testNameUpdated");
        envUpdated.setDomainRestrictions(Arrays.asList("ValueDom2", "ValueDom3", "ValueDom4"));

        TestObserver<Environment> obs = environmentRepository.update(envUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(envUpdated.getName()) && o.getId().equals(envCreated.getId()));
        obs.assertValue(o -> o.getDomainRestrictions().containsAll(envUpdated.getDomainRestrictions()));
    }

    @Test
    public void testDelete() {
        Environment env = new Environment();
        env.setName("testName");
        env.setOrganizationId(UUID.randomUUID().toString());
        env.setDescription("testDescription");
        env.setCreatedAt(new Date());
        env.setUpdatedAt(env.getUpdatedAt());
        env.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));

        Environment envCreated = environmentRepository.create(env).blockingGet();

        assertNotNull(environmentRepository.findById(envCreated.getId()).blockingGet());

        TestObserver<Void> obs = environmentRepository.delete(envCreated.getId()).test();
        obs.awaitTerminalEvent();
        obs.assertNoValues();

        assertNull(environmentRepository.findById(envCreated.getId()).blockingGet());
    }
}
