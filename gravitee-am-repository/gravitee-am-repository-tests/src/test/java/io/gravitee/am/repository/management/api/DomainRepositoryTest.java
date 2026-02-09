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

import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainRepositoryTest extends AbstractManagementTest {

    @Autowired
    private DomainRepository domainRepository;

    @Test
    public void testFindAll() {
        // create domain
        Domain domain = initDomain();
        domainRepository.create(domain).blockingGet();

        // fetch domains
        TestObserver<List<Domain>> testObserver1 = domainRepository.findAll().toList().test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(domains -> compare(domains.getFirst(), domain));
    }

    private boolean compare(Domain domain1, Domain domain2) {
        return domain1.getId().equals(domain2.getId()) &&
                domain1.getName().equals(domain2.getName()) &&
                domain1.getHrid().equals(domain2.getHrid()) &&
                domain1.getCreatedAt().equals(domain2.getCreatedAt()) &&
                domain1.getUpdatedAt().equals(domain2.getUpdatedAt()) &&
                domain1.getDescription().equals(domain2.getDescription()) &&
                domain1.isEnabled() == domain2.isEnabled() &&
                domain1.getPath().equals(domain2.getPath()) &&
                domain1.getReferenceId().equals(domain2.getReferenceId()) &&
                domain1.getReferenceType().equals(domain2.getReferenceType()) &&
                domain1.getVersion().equals(domain2.getVersion()) &&
                domain1.getTags().equals(domain2.getTags()) &&
                domain1.getIdentities().equals(domain2.getIdentities()) &&
                domain1.getLoginSettings().getResetPasswordOnExpiration().equals(domain2.getLoginSettings().getResetPasswordOnExpiration());
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
        domain.setVersion(DomainVersion.V2_0);

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
        domain.getLoginSettings().setResetPasswordOnExpiration(true);
        final OIDCSettings oidc = new OIDCSettings();
        final CIBASettings cibaSettings = new CIBASettings();
        cibaSettings.setEnabled(true);
        final CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId(UUID.randomUUID().toString());
        cibaSettings.setDeviceNotifiers(Arrays.asList(notifier));
        oidc.setCibaSettings(cibaSettings);
        oidc.setRequestUris(Arrays.asList("https://somewhere"));
        domain.setOidc(oidc);
        domain.setScim(new SCIMSettings());
        domain.setUma(new UMASettings());
        domain.setWebAuthnSettings(new WebAuthnSettings());
        domain.setSelfServiceAccountManagementSettings(new SelfServiceAccountManagementSettings());
        CertificateSettings certificateSettings = new CertificateSettings();
        certificateSettings.setFallbackCertificate("fallback-cert-id");
        domain.setCertificateSettings(certificateSettings);

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
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

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
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void testFindById() {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getId().equals(domainCreated.getId()));
        testObserver.assertValue(d -> d.getName().equals(domain.getName()));
        testObserver.assertValue(d -> d.getPath().equals(domain.getPath()));
        testObserver.assertValue(d -> d.getReferenceId().equals(domain.getReferenceId()));
        testObserver.assertValue(d -> d.getReferenceType().equals(domain.getReferenceType()));
        testObserver.assertValue(d -> d.isEnabled() == domain.isEnabled());
        testObserver.assertValue(d -> d.isVhostMode() == domain.isVhostMode());
        testObserver.assertValue(d -> d.getVersion() == domain.getVersion());
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
        testObserver.assertValue(d -> d.getOidc().getCibaSettings() != null && d.getOidc().getCibaSettings().isEnabled());
        testObserver.assertValue(d -> d.getOidc().getCibaSettings().getDeviceNotifiers() != null && d.getOidc().getCibaSettings().getDeviceNotifiers().size() == 1);
        testObserver.assertValue(d -> d.getOidc().getRequestUris() != null && d.getOidc().getRequestUris().size() == 1);
        testObserver.assertValue(d -> d.getScim() != null);
        testObserver.assertValue(d -> d.getWebAuthnSettings() != null);
        testObserver.assertValue(d -> d.getSelfServiceAccountManagementSettings() != null);
        testObserver.assertValue(d -> d.getCertificateSettings() != null);
        testObserver.assertValue(d -> "fallback-cert-id".equals(d.getCertificateSettings().getFallbackCertificate()));
    }

    @Test
    public void testNotFoundById() {
        var observer = domainRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        Domain domain = initDomain();

        TestObserver<Domain> testObserver = domainRepository.create(domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domainCreated -> domainCreated.getName().equals(domain.getName()));
    }

    @Test
    public void testUpdate() {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // update domain
        Domain updatedDomain = initDomain();
        updatedDomain.setId(domainCreated.getId());
        updatedDomain.setVersion(domainCreated.getVersion());
        updatedDomain.setName("testUpdatedName");
        updatedDomain.setWebAuthnSettings(null);
        updatedDomain.setLoginSettings(null);
        updatedDomain.setUma(null);
        updatedDomain.setOidc(null);
        updatedDomain.setScim(null);
        updatedDomain.setAccountSettings(null);
        updatedDomain.setCertificateSettings(null);
        updatedDomain.setTags(new HashSet<>(Arrays.asList("test")));
        updatedDomain.setIdentities(new HashSet<>(Arrays.asList("test")));
        TestObserver<Domain> testObserver = domainRepository.update(updatedDomain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.assertValue(d -> d.getCertificateSettings() == null);
    }

    @Test
    public void testDelete() {
        // create domain
        Domain domain = initDomain();
        Domain domainCreated = domainRepository.create(domain).blockingGet();

        // fetch domain
        TestObserver<Domain> testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals(domain.getName()));

        // delete domain
        TestObserver testObserver1 = domainRepository.delete(domainCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch domain
        testObserver = domainRepository.findById(domainCreated.getId()).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void findByCriteria() {
        Domain domainToCreate = initDomain();
        Domain domainCreated = domainRepository.create(domainToCreate).blockingGet();

        DomainCriteria criteria = new DomainCriteria();
        criteria.setAlertEnabled(true);
        TestSubscriber<Domain> testObserver1 = domainRepository.findAllByCriteria(criteria).test();

        testObserver1.awaitDone(10, TimeUnit.SECONDS);
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertNoValues();

        domainCreated.setAlertEnabled(true);
        final Domain domainUpdated = domainRepository.update(domainCreated).blockingGet();
        testObserver1 = domainRepository.findAllByCriteria(criteria).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);
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
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);

    }

    @Test
    public void createDomain_withDataPlaneId() {
        // create domain
        Domain domain = initDomain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId("environment#dataPlane");
        domain.setDataPlaneId("dataPlaneId");
        domainRepository.create(domain).blockingGet();

        // fetch domains
        TestSubscriber<Domain> testObserver1 = domainRepository.findAllByReferenceId("environment#dataPlane").test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
        testObserver1.assertValue(d -> d.getDataPlaneId().equals("dataPlaneId"));

    }
}
