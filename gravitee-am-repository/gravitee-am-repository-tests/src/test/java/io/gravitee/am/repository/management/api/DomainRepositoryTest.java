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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainRepositoryTest extends AbstractManagementTest {

    @Autowired
    private DomainRepository domainRepository;

    @Test
    public void testFindAll() throws TechnicalException {
        // create domain
        Domain domain = initDomain();
        domainRepository.create(domain).blockingGet();

        // fetch domains
        TestObserver<List<Domain>> testObserver1 = domainRepository.findAll().toList().test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(domains -> domains.size() == 1);
    }

    private Domain initDomain() {
        return initDomain("testName");
    }

    private Domain initDomain(String name) {
        Domain domain = new Domain();
        domain.setName(name);
        domain.setHrid(name);
        domain.setCreatedAt(new Date());
        domain.setUpdatedAt(domain.getCreatedAt());
        domain.setDescription(name + " description");
        domain.setEnabled(true);
        domain.setAlertEnabled(false);
        domain.setPath("/"+name);
        domain.setReferenceId("refId"+name);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setVhostMode(true);

        VirtualHost host = new VirtualHost();
        host.setHost("hostname-"+name);
        host.setPath("/hostname-"+name);
        host.setOverrideEntrypoint(true);
        VirtualHost host2 = new VirtualHost();
        host2.setHost("hostname2-"+name);
        host2.setPath("/hostname2-"+name);
        host2.setOverrideEntrypoint(true);
        domain.setVhosts(Arrays.asList(host, host2));

        domain.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
        domain.setIdentities(new HashSet<>(Arrays.asList("id1", "id2")));

        domain.setAccountSettings(new AccountSettings());
        domain.setLoginSettings(new LoginSettings());
        domain.setOidc(new OIDCSettings());
        domain.setScim(new SCIMSettings());
        domain.setUma(new UMASettings());
        domain.setWebAuthnSettings(new WebAuthnSettings());
        domain.setSelfServiceAccountManagementSettings(new SelfServiceAccountManagementSettings());

        return domain;
    }

    @Test
    public void testFindAllByEnvironment() {
        // create domain
        Domain domain = initDomain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId("environment#1");
        domainRepository.create(domain).blockingGet();

        // create domain on different environment.
        Domain otherDomain = initDomain();
        otherDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        otherDomain.setReferenceId("environment#2");
        otherDomain.setVhosts(null);
        domainRepository.create(otherDomain).blockingGet();

        // fetch domains
        TestSubscriber<Domain> testObserver1 = domainRepository.findAllByReferenceId("environment#1").test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
    }

    @Test
    public void testFindInIds() {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domains
        TestSubscriber<Domain> testSubscriber = domainRepository.findByIdIn(Collections.singleton(domainCreated.getId())).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(domainCreated.getId()));
        testObserver.assertValue(d -> d.getName().equals(domain.getName()));
        testObserver.assertValue(d -> d.getPath().equals(domain.getPath()));
        testObserver.assertValue(d -> d.getReferenceId().equals(domain.getReferenceId()));
        testObserver.assertValue(d -> d.getReferenceType().equals(domain.getReferenceType()));
        testObserver.assertValue(d -> d.isEnabled() == domain.isEnabled());
        testObserver.assertValue(d -> d.isVhostMode() == domain.isVhostMode());
        testObserver.assertValue(d -> d.getTags() != null &&
                domain.getTags() != null &&
                d.getTags().size() == domain.getTags().size() &&
                d.getTags().containsAll(domain.getTags()));
        testObserver.assertValue(d -> d.getIdentities() != null &&
                domain.getIdentities() != null &&
                d.getIdentities().size() == domain.getIdentities().size() &&
                d.getIdentities().containsAll(domain.getIdentities()));
        testObserver.assertValue(d -> d.getVhosts() != null &&
                domain.getVhosts() != null &&
                d.getVhosts().size() == domain.getVhosts().size());
        testObserver.assertValue(d -> d.getAccountSettings() != null);
        testObserver.assertValue(d -> d.getLoginSettings() != null);
        testObserver.assertValue(d -> d.getUma() != null);
        testObserver.assertValue(d -> d.getOidc() != null);
        testObserver.assertValue(d -> d.getScim() != null);
        testObserver.assertValue(d -> d.getWebAuthnSettings() != null);
        testObserver.assertValue(d -> d.getSelfServiceAccountManagementSettings() != null);
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        domainRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Domain domain = initDomain();

        TestObserver<Domain> testObserver = domainRepository.create(domain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domainCreated -> domainCreated.getName().equals(domain.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // update domain
        Domain updatedDomain = initDomain();
        updatedDomain.setId(domainCreated.getId());
        updatedDomain.setName("testUpdatedName");
        updatedDomain.setWebAuthnSettings(null);
        updatedDomain.setLoginSettings(null);
        updatedDomain.setUma(null);
        updatedDomain.setOidc(null);
        updatedDomain.setScim(null);
        updatedDomain.setAccountSettings(null);
        updatedDomain.setTags(new HashSet<>(Arrays.asList("test")));
        updatedDomain.setIdentities(new HashSet<>(Arrays.asList("test")));
        TestObserver<Domain> testObserver = domainRepository.update(updatedDomain).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(updatedDomain.getName()));
        testObserver.assertValue(d -> d.getId().equals(updatedDomain.getId()));
        testObserver.assertValue(d -> d.getName().equals(updatedDomain.getName()));
        testObserver.assertValue(d -> d.getPath().equals(updatedDomain.getPath()));
        testObserver.assertValue(d -> d.getReferenceId().equals(updatedDomain.getReferenceId()));
        testObserver.assertValue(d -> d.getReferenceType().equals(updatedDomain.getReferenceType()));
        testObserver.assertValue(d -> d.isEnabled() == updatedDomain.isEnabled());
        testObserver.assertValue(d -> d.isVhostMode() == updatedDomain.isVhostMode());
        testObserver.assertValue(d -> d.getVhosts() != null &&
                domain.getVhosts() != null &&
                d.getVhosts().size() == domain.getVhosts().size());
        testObserver.assertValue(d -> d.getTags() != null &&
                updatedDomain.getTags() != null &&
                d.getTags().size() == updatedDomain.getTags().size() &&
                d.getTags().containsAll(updatedDomain.getTags()));
        testObserver.assertValue(d -> d.getIdentities() != null &&
                updatedDomain.getIdentities() != null &&
                d.getIdentities().size() == updatedDomain.getIdentities().size() &&
                d.getIdentities().containsAll(updatedDomain.getIdentities()));
        testObserver.assertValue(d -> d.getAccountSettings() == null);
        testObserver.assertValue(d -> d.getLoginSettings() == null);
        testObserver.assertValue(d -> d.getUma() == null);
        testObserver.assertValue(d -> d.getOidc() == null);
        testObserver.assertValue(d -> d.getWebAuthnSettings() == null);
        testObserver.assertValue(d -> d.getScim() == null);
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(domain.getName()));

        // delete domain
        TestObserver testObserver1 = domainRepository.delete(domainCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch domain
        domainRepository.findById(domainCreated.getId()).test().assertEmpty();
    }

    @Test
    public void findByCriteria() {
        Domain domainToCreate = initDomain();
        Domain domainCreated = domainRepository.create(domainToCreate).blockingGet();

        DomainCriteria criteria = new DomainCriteria();
        criteria.setAlertEnabled(true);
        TestSubscriber<Domain> testObserver1 = domainRepository.findAllByCriteria(criteria).test();

        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();

        domainCreated.setAlertEnabled(true);
        final Domain domainUpdated = domainRepository.update(domainCreated).blockingGet();
        testObserver1 = domainRepository.findAllByCriteria(criteria).test();
        testObserver1.awaitTerminalEvent();
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(domain -> domain.getId().equals(domainUpdated.getId()));

    }

    @Test
    public void testSearch_wildcard() {
        // create domain
        Domain domain = initDomain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId("environment#1");
        domainRepository.create(domain).blockingGet();

        // fetch domains
        TestSubscriber<Domain> testObserver1 = domainRepository.search("environment#1", "testName").test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);

    }
}
