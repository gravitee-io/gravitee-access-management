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
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnvironmentRepositoryTest extends AbstractManagementTest {

    public static final String FIXED_REF_ID = "fixedRefId";

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Test
    public void testFindById() {
        Environment environment = buildEnv();

        // TODO: find another way to inject data in DB. Avoid to rely on class under test for that.
        Environment envCreated = environmentRepository.create(environment).blockingGet();

        TestObserver<Environment> obs = environmentRepository.findById(envCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(e -> e.getId().equals(envCreated.getId()));
        obs.assertValue(e -> e.getName().equals(environment.getName()));
        obs.assertValue(e -> e.getDescription().equals(environment.getDescription()));
        obs.assertValue(e -> e.getOrganizationId().equals(environment.getOrganizationId()));
        obs.assertValue(e -> e.getDomainRestrictions().containsAll(environment.getDomainRestrictions()));
        obs.assertValue(e -> e.getHrids().containsAll(environment.getHrids()));
    }

    @Test
    public void testNotFoundById() {
        environmentRepository.findById("unknown").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Environment env = buildEnv();
        env.setOrganizationId(UUID.randomUUID().toString());
        env.setName("testName");

        TestObserver<Environment> obs = environmentRepository.create(env).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(e -> e.getName().equals(env.getName()) && e.getId() != null);
        obs.assertValue(e -> e.getOrganizationId().equals(env.getOrganizationId()) && e.getId() != null);
        obs.assertValue(e -> e.getName().equals(env.getName()));
        obs.assertValue(e -> e.getDescription().equals(env.getDescription()));
        obs.assertValue(e -> e.getOrganizationId().equals(env.getOrganizationId()));
        obs.assertValue(e -> e.getDomainRestrictions().containsAll(env.getDomainRestrictions()));
        obs.assertValue(e -> e.getHrids().containsAll(env.getHrids()));
    }

    @Test
    public void testUpdate() {
        Environment env = buildEnv();

        Environment envCreated = environmentRepository.create(env).blockingGet();

        Environment envUpdated = new Environment();
        envUpdated.setId(envCreated.getId());
        envUpdated.setOrganizationId(env.getOrganizationId());
        envUpdated.setName("testNameUpdated");
        envUpdated.setDomainRestrictions(Arrays.asList("ValueDom2", "ValueDom3", "ValueDom4"));
        envUpdated.setHrids(Arrays.asList("Hrid2", "Hrid3", "Hrid4"));

        TestObserver<Environment> obs = environmentRepository.update(envUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(e -> e.getName().equals(envUpdated.getName()) && e.getId().equals(envCreated.getId()));
        obs.assertValue(e -> e.getDomainRestrictions().containsAll(envUpdated.getDomainRestrictions()));
        obs.assertValue(e -> e.getHrids().containsAll(envUpdated.getHrids()));
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
        env.setHrids(Arrays.asList("Hrid1", "Hrid2"));

        Environment envCreated = environmentRepository.create(env).blockingGet();

        assertNotNull(environmentRepository.findById(envCreated.getId()).blockingGet());

        TestObserver<Void> obs = environmentRepository.delete(envCreated.getId()).test();
        obs.awaitTerminalEvent();
        obs.assertNoValues();

        assertNull(environmentRepository.findById(envCreated.getId()).blockingGet());
    }

    @Test
    public void testFindAllByReference() {
        final int loop = 10;
        for (int i = 0; i < loop; i++) {
            final Environment environment = buildEnv();
            environment.setOrganizationId(FIXED_REF_ID);
            environmentRepository.create(environment).blockingGet();
        }

        for (int i = 0; i < loop; i++) {
            // random ref id
            environmentRepository.create(buildEnv()).blockingGet();
        }

        TestObserver<List<Environment>> testObserver = environmentRepository.findAll(FIXED_REF_ID).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(l -> l.size() == loop);
        testObserver.assertValue(l -> l.stream().map(Environment::getId).distinct().count() == loop);
    }

    private Environment buildEnv() {
        Environment env = new Environment();
        env.setName("testName");
        env.setDescription("testDescription");
        env.setCreatedAt(new Date());
        env.setUpdatedAt(env.getUpdatedAt());
        env.setOrganizationId(UUID.randomUUID().toString());
        env.setDomainRestrictions(Arrays.asList("ValueDom1", "ValueDom2"));
        env.setHrids(Arrays.asList("Hrid1", "Hrid2"));
        return env;
    }
}
