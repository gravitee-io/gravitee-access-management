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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ProtectedResource.Type.MCP_SERVER;
import static io.reactivex.rxjava3.core.Single.zip;

public class ProtectedResourceRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ProtectedResourceRepository repository;

    @Test
    public void testCreate() {
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings);

        TestObserver<ProtectedResource> testObserver = repository.create(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getId().equals(toSave.getId()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getDomainId().equals(toSave.getDomainId()));
        testObserver.assertValue(a -> a.getUpdatedAt().equals(toSave.getUpdatedAt()));
        testObserver.assertValue(a -> a.getCreatedAt().equals(toSave.getCreatedAt()));
        testObserver.assertValue(a -> a.getDescription().equals(toSave.getDescription()));
        testObserver.assertValue(a -> a.getType().equals(toSave.getType()));
        testObserver.assertValue(a -> a.getResourceIdentifiers().containsAll(toSave.getResourceIdentifiers()));
        testObserver.assertValue(a -> toSave.getResourceIdentifiers().containsAll(a.getResourceIdentifiers()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getClientId().equals(toSave.getClientId()));

        testObserver.assertValue(a -> a.getClientSecrets().get(0).getId().equals(clientSecret.getId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getName().equals(clientSecret.getName()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSettingsId().equals(clientSecret.getSettingsId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSecret().equals(clientSecret.getSecret()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getExpiresAt().equals(clientSecret.getExpiresAt()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getCreatedAt().equals(clientSecret.getCreatedAt()));

        testObserver.assertValue(a -> a.getSecretSettings().get(0).getId().equals(secretSettings.getId()));
        testObserver.assertValue(a -> a.getSecretSettings().get(0).getAlgorithm().equals(secretSettings.getAlgorithm()));

    }

    @Test
    public void shouldFindByDomainAndId() {

        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings);

        TestObserver<ProtectedResource> testObserver = repository.create(toSave)
                .concatMapMaybe(saved -> repository.findByDomainAndClient(saved.getDomainId(), saved.getClientId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getId().equals(toSave.getId()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getDomainId().equals(toSave.getDomainId()));
        testObserver.assertValue(a -> a.getUpdatedAt().equals(toSave.getUpdatedAt()));
        testObserver.assertValue(a -> a.getCreatedAt().equals(toSave.getCreatedAt()));
        testObserver.assertValue(a -> a.getDescription().equals(toSave.getDescription()));
        testObserver.assertValue(a -> a.getType().equals(toSave.getType()));
        testObserver.assertValue(a -> a.getResourceIdentifiers().containsAll(toSave.getResourceIdentifiers()));
        testObserver.assertValue(a -> toSave.getResourceIdentifiers().containsAll(a.getResourceIdentifiers()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getClientId().equals(toSave.getClientId()));

        testObserver.assertValue(a -> a.getClientSecrets().get(0).getId().equals(clientSecret.getId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getName().equals(clientSecret.getName()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSettingsId().equals(clientSecret.getSettingsId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSecret().equals(clientSecret.getSecret()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getExpiresAt().equals(clientSecret.getExpiresAt()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getCreatedAt().equals(clientSecret.getCreatedAt()));

        testObserver.assertValue(a -> a.getSecretSettings().get(0).getId().equals(secretSettings.getId()));
        testObserver.assertValue(a -> a.getSecretSettings().get(0).getAlgorithm().equals(secretSettings.getAlgorithm()));

    }

    @Test
    public void shouldFindAll() {
        var resources = generateResources(10, "all-domain-id");

        TestObserver<List<ProtectedResource>> testObserver = repository.findAll().toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .assertValue(res -> res.size() >= resources.size())
                .assertValue(res -> res.getFirst().getClientSecrets() != null && res.getFirst().getClientSecrets().size() == resources.getFirst().getClientSecrets().size())
                .assertValue(res -> res.getFirst().getSecretSettings() != null && res.getFirst().getSecretSettings().size() == resources.getFirst().getSecretSettings().size());
    }

    @Test
    public void shouldFindByDomain() {
        var resources = generateResources(5, "dummy-domain-id");
        generateResources(3, "other-domain-id");

        TestObserver<List<ProtectedResource>> testObserver = repository.findByDomain("dummy-domain-id").toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .assertValue(res -> res.size() == resources.size())
                .assertValue(res -> res.getFirst().getClientSecrets() != null && res.getFirst().getClientSecrets().size() == resources.getFirst().getClientSecrets().size())
                .assertValue(res -> res.getFirst().getSecretSettings() != null && res.getFirst().getSecretSettings().size() == resources.getFirst().getSecretSettings().size());
    }

    private List<ProtectedResource> generateResources(int count, String domainId) {
        List<ProtectedResource> resources = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ClientSecret clientSecret = generateClientSecret();
            ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
            ProtectedResource toSave = generateResource(clientSecret, secretSettings);
            // Add multiple secrets to ensure that the query is correct.
            for (int j = 0; j < 2; j++) {
                ClientSecret otherSecret = generateClientSecret();
                var clientSecrets = new java.util.ArrayList<>(toSave.getClientSecrets());
                clientSecrets.add(otherSecret);
                toSave.setClientSecrets(clientSecrets);
            }
            toSave.setDomainId(domainId);
            resources.add(toSave);
            repository.create(toSave).blockingGet();
        }
        return resources;
    public void shouldFindBySearch() {
        ProtectedResource toSave1 = generateResource("abc", "domainSearch2", "client1", generateClientSecret(), generateApplicationSecretSettings());
        ProtectedResource toSave2 = generateResource("dcf", "domainSearch2", "client2",generateClientSecret(), generateApplicationSecretSettings());

        ProtectedResource differentDomain = generateResource("ggg", "domainSearch12", "client1", generateClientSecret(), generateApplicationSecretSettings());

        zip(repository.create(toSave1), repository.create(toSave2), repository.create(differentDomain), List::of)
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        repository.findByDomainAndType(toSave1.getDomainId(), MCP_SERVER, PageSortRequest.builder()
                        .page(0)
                        .size(2)
                        .sortBy("name")
                        .asc(true)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 0)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().size() == 2)
                .assertValue(page -> List.copyOf(page.getData()).get(0).id().equals(toSave1.getId()))
                .assertValue(page -> List.copyOf(page.getData()).get(1).id().equals(toSave2.getId()));

        repository.findByDomainAndType(toSave1.getDomainId(), MCP_SERVER, PageSortRequest.builder()
                        .page(0)
                        .size(2)
                        .sortBy("name")
                        .asc(false)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 0)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().size() == 2)
                .assertValue(page -> List.copyOf(page.getData()).get(0).id().equals(toSave2.getId()))
                .assertValue(page -> List.copyOf(page.getData()).get(1).id().equals(toSave1.getId()));

        repository.findByDomainAndType(toSave1.getDomainId(), MCP_SERVER, PageSortRequest.builder()
                        .page(1)
                        .size(2)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 1)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().isEmpty());

        repository.findByDomainAndType(toSave1.getDomainId(), MCP_SERVER, PageSortRequest.builder()
                        .page(1)
                        .size(1)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 1)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().size() == 1);
    }

    @Test
    public void shouldFindBySearchByIds() {

        ProtectedResource toSave1 = generateResource("abc", "domainSearch2", "client1", generateClientSecret(), generateApplicationSecretSettings());
        ProtectedResource toSave2 = generateResource("dcf", "domainSearch2", "client2",generateClientSecret(), generateApplicationSecretSettings());

        ProtectedResource differentDomain = generateResource("ggg", "domainSearch21", "client1",generateClientSecret(), generateApplicationSecretSettings());

        zip(repository.create(toSave1), repository.create(toSave2), repository.create(differentDomain), List::of)
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        repository.findByDomainAndTypeAndIds(toSave1.getDomainId(), MCP_SERVER, List.of(), PageSortRequest.builder()
                        .page(0)
                        .size(2)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 0)
                .assertValue(page -> page.getTotalCount() == 0)
                .assertValue(page -> page.getData().isEmpty());

        repository.findByDomainAndTypeAndIds(toSave1.getDomainId(), MCP_SERVER, List.of(toSave1.getId(), toSave2.getId()), PageSortRequest.builder()
                        .page(0)
                        .size(2)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 0)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().size() == 2)
                .assertValue(page -> page.getData().stream().anyMatch(res -> res.id().equals(toSave1.getId())))
                .assertValue(page -> page.getData().stream().anyMatch(res -> res.id().equals(toSave2.getId())));

        repository.findByDomainAndTypeAndIds(toSave1.getDomainId(), MCP_SERVER, List.of(toSave1.getId(), toSave2.getId()), PageSortRequest.builder()
                        .page(1)
                        .size(2)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 1)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().isEmpty());

        repository.findByDomainAndTypeAndIds(toSave1.getDomainId(), MCP_SERVER, List.of(toSave1.getId(), toSave2.getId()), PageSortRequest.builder()
                        .page(1)
                        .size(1)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 1)
                .assertValue(page -> page.getTotalCount() == 2)
                .assertValue(page -> page.getData().size() == 1);

        repository.findByDomainAndTypeAndIds(toSave1.getDomainId(), MCP_SERVER, List.of(toSave1.getId()), PageSortRequest.builder()
                        .page(0)
                        .size(2)
                        .build())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(page -> page.getCurrentPage() == 0)
                .assertValue(page -> page.getTotalCount() == 1)
                .assertValue(page -> page.getData().size() == 1)
                .assertValue(page -> page.getData().stream().anyMatch(res -> res.id().equals(toSave1.getId())))
                .assertValue(page -> page.getData().stream().noneMatch(res -> res.id().equals(toSave2.getId())));
    }

    private ClientSecret generateClientSecret() {
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId(RandomString.generate());
        clientSecret.setName(RandomString.generate());
        clientSecret.setSettingsId("settingsId");
        clientSecret.setSecret("secret");
        clientSecret.setExpiresAt(new Date());
        clientSecret.setCreatedAt(new Date());
        return clientSecret;
    }

    private ApplicationSecretSettings generateApplicationSecretSettings() {
        ApplicationSecretSettings secretSettings = new ApplicationSecretSettings();
        secretSettings.setId(RandomString.generate());
        secretSettings.setAlgorithm("NONE");
        return secretSettings;
    }

    private ProtectedResource generateResource(ClientSecret clientSecret, ApplicationSecretSettings secretSettings) {
        return generateResource("test-resource", "domainId", "clientId", clientSecret, secretSettings);
    }

    private ProtectedResource generateResource(String name, ClientSecret clientSecret, ApplicationSecretSettings secretSettings) {
        return generateResource(name, "domainId", "clientId", clientSecret, secretSettings);
    }

    private ProtectedResource generateResource(String name, String domainId, String clientId, ClientSecret clientSecret, ApplicationSecretSettings secretSettings) {
        ProtectedResource toSave = new ProtectedResource();
        toSave.setId(RandomString.generate());
        toSave.setName(name);
        toSave.setClientId(clientId);
        toSave.setDomainId(domainId);
        toSave.setType(MCP_SERVER);
        toSave.setCreatedAt(new Date());
        toSave.setUpdatedAt(new Date());
        toSave.setDescription("description");
        toSave.setResourceIdentifiers(List.of("resource-identifier1", "resource-identifier2"));
        toSave.setSecretSettings(List.of(secretSettings));
        toSave.setClientSecrets(List.of(clientSecret));
        return toSave;
    }

}
