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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderRepositoryTest extends AbstractManagementTest {

    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Test
    public void testFindAll() {
        // baseline count
        int beforeCount = identityProviderRepository.findAll().toList().blockingGet().size();

        // create idps
        IdentityProvider idp1 = buildIdentityProvider();
        identityProviderRepository.create(idp1).blockingGet();

        IdentityProvider idp2 = buildIdentityProvider();
        identityProviderRepository.create(idp2).blockingGet();

        // fetch all
        TestObserver<List<IdentityProvider>> testObserver = identityProviderRepository.findAll().toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idps -> idps.size() == beforeCount + 2);
        testObserver.assertValue(idps -> idps.stream().map(IdentityProvider::getName)
                .collect(Collectors.toSet())
                .containsAll(Arrays.asList(idp1.getName(), idp2.getName())));
    }

    @Test
    public void testFindByDomain() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        identityProvider.setReferenceId("testDomain");
        identityProviderRepository.create(identityProvider).blockingGet();

        IdentityProvider identityProvider2 = buildIdentityProvider();
        identityProvider2.setReferenceId("testDomain");
        identityProviderRepository.create(identityProvider2).blockingGet();

        IdentityProvider identityProvider3 = buildIdentityProvider();
        identityProviderRepository.create(identityProvider3).blockingGet();

        // fetch idps
        TestObserver<List<IdentityProvider>> testObserver = identityProviderRepository.findAll(ReferenceType.DOMAIN, "testDomain").toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idps -> idps.size() == 2);
        testObserver.assertValue(idps -> idps.stream().map(IdentityProvider::getName)
                .collect(Collectors.toSet())
                .containsAll(Arrays.asList(identityProvider.getName(), identityProvider2.getName())));
    }

    private IdentityProvider buildIdentityProvider() {
        String random = UUID.randomUUID().toString();
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("name" + random);
        identityProvider.setReferenceType(ReferenceType.DOMAIN);
        identityProvider.setReferenceId("ref" + random);
        identityProvider.setConfiguration("{\"field\": \"" + random + "\"}");
        identityProvider.setExternal(true);
        identityProvider.setSystem(true);
        identityProvider.setType("type" + random);
        identityProvider.setDataPlaneId(UUID.randomUUID().toString());

        Map<String, String> mappers = new HashMap<>();
        mappers.put("mapper", "mapper" + random);
        identityProvider.setMappers(mappers);

        Map<String, String[]> roleMapper = new HashMap<>();
        roleMapper.put("mapper", new String[]{"mapper" + random, "mapper2" + random});
        identityProvider.setRoleMapper(roleMapper);

        identityProvider.setDomainWhitelist(List.of("gmail.com", "hotmail.com", "customdomain.com"));

        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(new Date());
        return identityProvider;
    }

    @Test
    public void testFindById() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.findById(identityProviderCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(identityProviderCreated.getId()));
        assertEqualsTo(identityProvider, testObserver);

    }

    private void assertEqualsTo(IdentityProvider identityProvider, TestObserver<IdentityProvider> testObserver) {
        testObserver.assertValue(idp -> idp.getName().equals(identityProvider.getName()));
        testObserver.assertValue(idp -> idp.getType().equals(identityProvider.getType()));
        testObserver.assertValue(idp -> idp.isSystem() == identityProvider.isSystem());
        testObserver.assertValue(idp -> idp.getDataPlaneId().equals(identityProvider.getDataPlaneId()));
        testObserver.assertValue(idp -> idp.getConfiguration().equals(identityProvider.getConfiguration()));
        testObserver.assertValue(idp -> idp.getReferenceId().equals(identityProvider.getReferenceId()));
        testObserver.assertValue(idp -> idp.getReferenceType().equals(identityProvider.getReferenceType()));
        testObserver.assertValue(idp -> idp.getMappers().entrySet().equals(identityProvider.getMappers().entrySet()));
        testObserver.assertValue(idp -> idp.getRoleMapper().keySet().equals(identityProvider.getRoleMapper().keySet()));
        testObserver.assertValue(idp -> idp.getRoleMapper().values().stream().filter(v -> v instanceof String[]).count() > 0);
    }

    @Test
    public void testFindById_refrenceType() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        identityProvider.setReferenceType(ReferenceType.ORGANIZATION);
        identityProvider.setReferenceId(ORGANIZATION_ID);
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, identityProviderCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(identityProviderCreated.getId()));
        assertEqualsTo(identityProvider, testObserver);
    }

    @Test
    public void testNotFoundById() {
        var observer = identityProviderRepository.findById("test").test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        IdentityProvider identityProvider = buildIdentityProvider();
        identityProvider.setMappers(Collections.singletonMap("username", "johndoe"));
        identityProvider.setRoleMapper(Collections.singletonMap("username=johndoe", new String[]{"dev", "admin"}));
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.create(identityProvider).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals(identityProvider.getName())
                && idp.getMappers().containsKey("username")
                && idp.getRoleMapper().containsKey("username=johndoe"));
    }

    @Test
    public void testUpdate() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // update idp
        IdentityProvider updatedIdentityProvider = buildIdentityProvider();
        updatedIdentityProvider.setId(identityProviderCreated.getId());

        TestObserver<IdentityProvider> testObserver = identityProviderRepository.update(updatedIdentityProvider).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getId().equals(identityProviderCreated.getId()));
        assertEqualsTo(updatedIdentityProvider, testObserver);
    }

    @Test
    public void testDelete() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.findById(identityProviderCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals(identityProvider.getName()));

        // delete idp
        TestObserver testObserver1 = identityProviderRepository.delete(identityProviderCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch idp
        testObserver = identityProviderRepository.findById(identityProviderCreated.getId()).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }

    @Test
    public void findByPasswordPolicy() {
        // create idp
        IdentityProvider identityProvider = buildIdentityProvider();
        identityProvider.setReferenceId("testDomain");
        identityProvider.setPasswordPolicy("passwordPolicy1");
        identityProviderRepository.create(identityProvider).blockingGet();

        IdentityProvider identityProvider2 = buildIdentityProvider();
        identityProvider2.setReferenceId("testDomain");
        identityProvider.setPasswordPolicy("passwordPolicy2");
        identityProviderRepository.create(identityProvider2).blockingGet();

        // fetch idps
        TestObserver<List<IdentityProvider>> testObserver = identityProviderRepository.findAllByPasswordPolicy(ReferenceType.DOMAIN, "testDomain", "passwordPolicy1").toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idps -> idps.size() == 1);
        testObserver.assertValue(idp -> idp.get(0).getName().equals(identityProvider.getName()));
    }
}
