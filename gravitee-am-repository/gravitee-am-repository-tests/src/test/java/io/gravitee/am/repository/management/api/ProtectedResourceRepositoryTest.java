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
import io.gravitee.am.model.McpTool;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceFeature;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ProtectedResource.Type.MCP_SERVER;
import static io.reactivex.rxjava3.core.Single.zip;

public class ProtectedResourceRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ProtectedResourceRepository repository;

    @Test
    public void testCreate() {
        ClientSecret clientSecret = generateClientSecret();
        McpTool tool1 = generateMcpTool("key1");
        McpTool tool2 = generateMcpTool("key2");
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings, List.of(tool1, tool2));

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
        testObserver.assertValue(a -> a.getCertificate().equals(toSave.getCertificate()));

        testObserver.assertValue(a -> a.getClientSecrets().get(0).getId().equals(clientSecret.getId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getName().equals(clientSecret.getName()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSettingsId().equals(clientSecret.getSettingsId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSecret().equals(clientSecret.getSecret()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getExpiresAt().equals(clientSecret.getExpiresAt()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getCreatedAt().equals(clientSecret.getCreatedAt()));

        testObserver.assertValue(a -> a.getSecretSettings().get(0).getId().equals(secretSettings.getId()));
        testObserver.assertValue(a -> a.getSecretSettings().get(0).getAlgorithm().equals(secretSettings.getAlgorithm()));

        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getKey().equals(tool1.getKey())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getDescription().equals(tool1.getDescription())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getType().equals(tool1.getType())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getCreatedAt().equals(tool1.getCreatedAt())));
        testObserver.assertValue(a -> a.getFeatures().stream().map(McpTool.class::cast).toList().stream()
                .anyMatch(f -> f.getScopes().equals(tool1.getScopes())));

        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getKey().equals(tool2.getKey())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getDescription().equals(tool2.getDescription())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getType().equals(tool2.getType())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getCreatedAt().equals(tool2.getCreatedAt())));
        testObserver.assertValue(a -> a.getFeatures().stream().map(McpTool.class::cast).toList().stream()
                .anyMatch(f -> f.getScopes().equals(tool2.getScopes())));


    }

    @Test
    public void testUpdate() {
        ClientSecret clientSecret = generateClientSecret();
        McpTool tool1 = generateMcpTool("key1");
        McpTool tool2 = generateMcpTool("key2");
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings, List.of(tool1, tool2));

        ProtectedResource createdResource = repository.create(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId()))
                .timeout(10, TimeUnit.SECONDS)
                .blockingGet();


        toSave.setId(createdResource.getId());
        // update fields
        toSave.setCertificate(UUID.randomUUID().toString());
        toSave.setDescription("Updated description");

        var testObserver = repository.update(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId())).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getId().equals(toSave.getId()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getDomainId().equals(toSave.getDomainId()));
        testObserver.assertValue(a -> a.getUpdatedAt().equals(toSave.getUpdatedAt()));
        testObserver.assertValue(a -> a.getCreatedAt().equals(toSave.getCreatedAt()));
        testObserver.assertValue(a -> a.getDescription().equals(toSave.getDescription()) && !a.getDescription().equals(createdResource.getDescription()));
        testObserver.assertValue(a -> a.getType().equals(toSave.getType()));
        testObserver.assertValue(a -> a.getResourceIdentifiers().containsAll(toSave.getResourceIdentifiers()));
        testObserver.assertValue(a -> toSave.getResourceIdentifiers().containsAll(a.getResourceIdentifiers()));
        testObserver.assertValue(a -> a.getName().equals(toSave.getName()));
        testObserver.assertValue(a -> a.getClientId().equals(toSave.getClientId()));
        testObserver.assertValue(a -> a.getCertificate().equals(toSave.getCertificate()) && !a.getCertificate().equals(createdResource.getCertificate()));

        testObserver.assertValue(a -> a.getClientSecrets().get(0).getId().equals(clientSecret.getId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getName().equals(clientSecret.getName()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSettingsId().equals(clientSecret.getSettingsId()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSecret().equals(clientSecret.getSecret()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getExpiresAt().equals(clientSecret.getExpiresAt()));
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getCreatedAt().equals(clientSecret.getCreatedAt()));

        testObserver.assertValue(a -> a.getSecretSettings().get(0).getId().equals(secretSettings.getId()));
        testObserver.assertValue(a -> a.getSecretSettings().get(0).getAlgorithm().equals(secretSettings.getAlgorithm()));

        testObserver.assertValue(a -> a.getSettings().getOauth().getClientId().equals("oauthClientId"));
        testObserver.assertValue(a -> a.getSettings().getSecretExpirationSettings().getEnabled().equals(true));
        testObserver.assertValue(a -> a.getSettings().getSecretExpirationSettings().getExpiryTimeSeconds().equals(3600L));

        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getKey().equals(tool1.getKey())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getDescription().equals(tool1.getDescription())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getType().equals(tool1.getType())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getCreatedAt().equals(tool1.getCreatedAt())));
        testObserver.assertValue(a -> a.getFeatures().stream().map(McpTool.class::cast).toList().stream()
                .anyMatch(f -> f.getScopes().equals(tool1.getScopes())));

        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getKey().equals(tool2.getKey())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getDescription().equals(tool2.getDescription())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getType().equals(tool2.getType())));
        testObserver.assertValue(a -> a.getFeatures().stream().anyMatch(f -> f.getCreatedAt().equals(tool2.getCreatedAt())));
        testObserver.assertValue(a -> a.getFeatures().stream().map(McpTool.class::cast).toList().stream()
                .anyMatch(f -> f.getScopes().equals(tool2.getScopes())));
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

        testObserver.assertValue(a -> a.getSettings().getOauth().getClientId().equals("oauthClientId"));
        testObserver.assertValue(a -> a.getSettings().getSecretExpirationSettings().getEnabled().equals(true));
        testObserver.assertValue(a -> a.getSettings().getSecretExpirationSettings().getExpiryTimeSeconds().equals(3600L));

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

    @Test
    public void testFindByDomainAndId() {
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource resource = generateResource(clientSecret, secretSettings, List.of());
        resource.setDomainId("DomainTestFindByDomainAndId");
        ProtectedResource created = repository.create(resource).blockingGet();

        TestObserver<ProtectedResource> testObserver = repository.findByDomainAndId("DomainTestFindByDomainAndId", created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getId().equals(created.getId()));
        testObserver.assertValue(r -> r.getDomainId().equals("DomainTestFindByDomainAndId"));
    }

    @Test
    public void testFindByDomainAndId_whenDomainMismatch() {
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource resource = generateResource(clientSecret, secretSettings, List.of());
        resource.setDomainId("domain-1");
        ProtectedResource created = repository.create(resource).blockingGet();

        TestObserver<ProtectedResource> testObserver = repository.findByDomainAndId("domain-2", created.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues(); // Should return empty Maybe
    }

    @Test
    public void testCreateWithMultipleSecrets() {
        // Create resource with 3 client secrets
        ClientSecret clientSecret1 = generateClientSecret();
        ClientSecret clientSecret2 = generateClientSecret();
        ClientSecret clientSecret3 = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        
        ProtectedResource toSave = generateResource(clientSecret1, secretSettings);
        toSave.setClientSecrets(List.of(clientSecret1, clientSecret2, clientSecret3));

        TestObserver<ProtectedResource> testObserver = repository.create(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify exactly 3 secrets
        testObserver.assertValue(a -> a.getClientSecrets() != null && a.getClientSecrets().size() == 3);
        
        // Verify all 3 secrets are persisted correctly
        testObserver.assertValue(a -> a.getClientSecrets().stream().anyMatch(s -> s.getId().equals(clientSecret1.getId())));
        testObserver.assertValue(a -> a.getClientSecrets().stream().anyMatch(s -> s.getId().equals(clientSecret2.getId())));
        testObserver.assertValue(a -> a.getClientSecrets().stream().anyMatch(s -> s.getId().equals(clientSecret3.getId())));
        
        // Verify first secret details
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getName() != null);
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSecret() != null);
        testObserver.assertValue(a -> a.getClientSecrets().get(0).getSettingsId() != null);
    }

    private List<ProtectedResource> generateResources(int count, String domainId) {
        List<ProtectedResource> resources = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ClientSecret clientSecret = generateClientSecret();
            ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
            ProtectedResource toSave = generateResource(clientSecret, secretSettings);
            toSave.setDomainId(domainId);
            resources.add(toSave);
            repository.create(toSave).blockingGet();
        }
        return resources;
    }

    @Test
    public void shouldFindBySearch() {
        ProtectedResource toSave1 = generateResource("abc", "domainSearch2", "client1", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));
        ProtectedResource toSave2 = generateResource("dcf", "domainSearch2", "client2",generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));

        ProtectedResource differentDomain = generateResource("ggg", "domainSearch12", "client1", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));

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

        ProtectedResource toSave1 = generateResource("abc", "domainSearch2", "client1", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));
        ProtectedResource toSave2 = generateResource("dcf", "domainSearch2", "client2",generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));

        ProtectedResource differentDomain = generateResource("ggg", "domainSearch21", "client1",generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));

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

    @Test
    public void shouldTellIfExistsByDomain(){
        ProtectedResource toSave1 = generateResource("abc", "domainSearchExists1", "client1", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));
        ProtectedResource toSave2 = generateResource("dcf", "domainSearchExists1", "client2", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));

        toSave1.setResourceIdentifiers(List.of("https://domain.one", "https://domain.two"));
        toSave2.setResourceIdentifiers(List.of("https://domain.three"));


        zip(repository.create(toSave1), repository.create(toSave2), List::of)
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.one"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.one", "https://domain.two"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.one", "https://domain.three"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.three"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.new"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.new", "https://domain.new2"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        repository.existsByResourceIdentifiers("domainSearchExists1", List.of("https://domain.new", "https://domain.one"))
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);
    }

    @Test
    public void shouldTellIfExistsByResourceIdentifiersExcludingId() {
        ProtectedResource toSave1 = generateResource("abc", "domainSearchExclude1", "client1", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key1")));
        ProtectedResource toSave2 = generateResource("dcf", "domainSearchExclude1", "client2", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key2")));
        ProtectedResource toSave3 = generateResource("ggg", "domainSearchExclude1", "client3", generateClientSecret(), generateApplicationSecretSettings(), List.of(generateMcpTool("key3")));

        toSave1.setResourceIdentifiers(List.of("https://exclude.one", "https://exclude.two"));
        toSave2.setResourceIdentifiers(List.of("https://exclude.three"));
        toSave3.setResourceIdentifiers(List.of("https://exclude.four"));

        zip(repository.create(toSave1), repository.create(toSave2), repository.create(toSave3), List::of)
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        ProtectedResource created1 = repository.findById(toSave1.getId()).blockingGet();
        ProtectedResource created2 = repository.findById(toSave2.getId()).blockingGet();
        ProtectedResource created3 = repository.findById(toSave3.getId()).blockingGet();

        // Test: Exclude created1, check for created1's identifiers - should return false (only created1 has them)
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.one"), created1.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        // Test: Exclude created1, check for created2's identifiers - should return true (created2 has them)
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.three"), created1.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        // Test: Exclude created2, check for created2's identifiers - should return false (only created2 has them)
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.three"), created2.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        // Test: Exclude created2, check for created1's identifiers - should return true (created1 has them)
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.two"), created2.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);

        // Test: Exclude created3, check for created3's identifiers - should return false (only created3 has them)
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.four"), created3.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        // Test: Exclude created3, check for identifiers that don't exist - should return false
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.nonexistent"), created3.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        // Test: Exclude created3, check for empty list - should return false
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of(), created3.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);

        // Test: Exclude created3, check for multiple identifiers where one exists in another resource - should return true
        repository.existsByResourceIdentifiersExcludingId("domainSearchExclude1", List.of("https://exclude.nonexistent", "https://exclude.one"), created3.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true); // created1 or created2 has "https://exclude.one"

        // Test: Different domain - should return false (identifiers don't exist in that domain)
        repository.existsByResourceIdentifiersExcludingId("differentDomain", List.of("https://exclude.one"), created1.getId())
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false);
    }

    @Test
    public void shouldReturnFeaturesInAlphabeticalOrderByKey() {
        // Create tools in non-alphabetical order
        McpTool zebraTool = generateMcpTool("zebra_tool");
        zebraTool.setDescription("Should be last");
        
        McpTool appleTool = generateMcpTool("apple_tool");
        appleTool.setDescription("Should be first");
        
        McpTool middleTool = generateMcpTool("middle_tool");
        middleTool.setDescription("Should be in middle");
        
        McpTool bananaTool = generateMcpTool("banana_tool");
        bananaTool.setDescription("Should be second");
        
        // Add them in random order
        List<ProtectedResourceFeature> toolsInRandomOrder = List.of(zebraTool, appleTool, middleTool, bananaTool);
        
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource("ordering-test", "domain-ordering", "client-ordering", 
                clientSecret, secretSettings, toolsInRandomOrder);

        TestObserver<ProtectedResource> testObserver = repository.create(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify features are returned in alphabetical order by key
        testObserver.assertValue(a -> a.getFeatures() != null && a.getFeatures().size() == 4);
        testObserver.assertValue(a -> a.getFeatures().get(0).getKey().equals("apple_tool"));
        testObserver.assertValue(a -> a.getFeatures().get(1).getKey().equals("banana_tool"));
        testObserver.assertValue(a -> a.getFeatures().get(2).getKey().equals("middle_tool"));
        testObserver.assertValue(a -> a.getFeatures().get(3).getKey().equals("zebra_tool"));
    }
    
    @Test
    public void shouldReturnFeaturesInAlphabeticalOrderAfterUpdate() {
        // Create resource with initial tools
        McpTool tool1 = generateMcpTool("tool_1");
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings, List.of(tool1));

        ProtectedResource created = repository.create(toSave).blockingGet();
        
        // Update with tools in reverse alphabetical order
        McpTool zebraTool = generateMcpTool("zebra_update");
        McpTool appleTool = generateMcpTool("apple_update");
        McpTool middleTool = generateMcpTool("middle_update");
        
        created.setFeatures(List.of(zebraTool, appleTool, middleTool));
        
        TestObserver<ProtectedResource> testObserver = repository.update(created)
                .flatMapMaybe(updated -> repository.findById(updated.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify features are returned in alphabetical order after update
        testObserver.assertValue(a -> a.getFeatures().size() == 3);
        testObserver.assertValue(a -> a.getFeatures().get(0).getKey().equals("apple_update"));
        testObserver.assertValue(a -> a.getFeatures().get(1).getKey().equals("middle_update"));
        testObserver.assertValue(a -> a.getFeatures().get(2).getKey().equals("zebra_update"));
    }

    @Test
    public void shouldDeleteResourceById() {
        ClientSecret clientSecret = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret, secretSettings, List.of(generateMcpTool("key1")));

        ProtectedResource created = repository.create(toSave).blockingGet();

        repository.delete(created.getId())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors();

        repository.findById(created.getId())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void shouldEnforceUniqueResourceIdentifiersPerDomain() {
        // Create first resource with specific identifiers
        ProtectedResource resource1 = generateResource("resource1", "unique-domain", "client1", 
                generateClientSecret(), generateApplicationSecretSettings(), List.of());
        resource1.setResourceIdentifiers(List.of("https://unique.example.com/resource1"));

        ProtectedResource created1 = repository.create(resource1).blockingGet();

        // Try to create second resource with same identifier in same domain - should fail uniqueness check
        ProtectedResource resource2 = generateResource("resource2", "unique-domain", "client2", 
                generateClientSecret(), generateApplicationSecretSettings(), List.of());
        resource2.setResourceIdentifiers(List.of("https://unique.example.com/resource1")); // Same identifier

        // The uniqueness check should detect this
        repository.existsByResourceIdentifiers("unique-domain", List.of("https://unique.example.com/resource1"))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true); // Should exist

        // But should return false when excluding the original resource
        repository.existsByResourceIdentifiersExcludingId("unique-domain", 
                List.of("https://unique.example.com/resource1"), created1.getId())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(false); // Should not exist when excluding created1

        // Different domain can have same identifier
        ProtectedResource resource3 = generateResource("resource3", "different-domain", "client3", 
                generateClientSecret(), generateApplicationSecretSettings(), List.of());
        resource3.setResourceIdentifiers(List.of("https://unique.example.com/resource1")); // Same identifier, different domain

        repository.create(resource3).blockingGet();
        
        // Should exist in different domain
        repository.existsByResourceIdentifiers("different-domain", List.of("https://unique.example.com/resource1"))
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertValue(true);
    }

    @Test
    public void testUpdateSecrets() {
        // Create resource with one secret
        ClientSecret clientSecret1 = generateClientSecret();
        ApplicationSecretSettings secretSettings = generateApplicationSecretSettings();
        ProtectedResource toSave = generateResource(clientSecret1, secretSettings);

        ProtectedResource created = repository.create(toSave).blockingGet();

        // Add a second secret
        ClientSecret clientSecret2 = generateClientSecret();
        List<ClientSecret> updatedSecrets = new java.util.ArrayList<>(created.getClientSecrets());
        updatedSecrets.add(clientSecret2);
        created.setClientSecrets(updatedSecrets);

        // Update the resource
        TestObserver<ProtectedResource> testObserver = repository.update(created)
                .flatMapMaybe(updated -> repository.findById(updated.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Verify that the resource now has 2 secrets
        testObserver.assertValue(a -> a.getClientSecrets().size() == 2);
        testObserver.assertValue(a -> a.getClientSecrets().stream().anyMatch(s -> s.getId().equals(clientSecret1.getId())));
        testObserver.assertValue(a -> a.getClientSecrets().stream().anyMatch(s -> s.getId().equals(clientSecret2.getId())));
    }

    @Test
    public void shouldLoadMultipleResourceIdentifiers() {
        // Create resource with multiple identifiers
        ProtectedResource toSave = generateResource("multi-identifier", "load-test-domain", "client-load", 
                generateClientSecret(), generateApplicationSecretSettings(), List.of());
        toSave.setResourceIdentifiers(List.of(
                "https://test.example.com/api/v1",
                "https://test.example.com/api/v2",
                "https://test.example.com/api/v3"
        ));

        TestObserver<ProtectedResource> testObserver = repository.create(toSave)
                .flatMapMaybe(created -> repository.findById(created.getId()))
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        // Verify all identifiers are loaded correctly
        testObserver.assertValue(a -> a.getResourceIdentifiers() != null);
        testObserver.assertValue(a -> a.getResourceIdentifiers().size() == 3);
        testObserver.assertValue(a -> a.getResourceIdentifiers().contains("https://test.example.com/api/v1"));
        testObserver.assertValue(a -> a.getResourceIdentifiers().contains("https://test.example.com/api/v2"));
        testObserver.assertValue(a -> a.getResourceIdentifiers().contains("https://test.example.com/api/v3"));
        
        // Verify all identifiers are present (order may vary depending on implementation)
        testObserver.assertValue(a -> a.getResourceIdentifiers().containsAll(toSave.getResourceIdentifiers()));
        testObserver.assertValue(a -> toSave.getResourceIdentifiers().containsAll(a.getResourceIdentifiers()));
    }

    private McpTool generateMcpTool(String key) {
        McpTool tool = new McpTool();
        tool.setKey(key);
        tool.setDescription(RandomString.generate());
        tool.setScopes(List.of("abc", "cde"));
        tool.setCreatedAt(new Date());
        return tool;
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
        return generateResource("test-resource", "domainId", "clientId", clientSecret, secretSettings, List.of());
    }

    private ProtectedResource generateResource(ClientSecret clientSecret, ApplicationSecretSettings secretSettings, List<? extends ProtectedResourceFeature> features) {
        return generateResource("test-resource", "domainId", "clientId", clientSecret, secretSettings, features);
    }

    private ProtectedResource generateResource(String name, String domainId, String clientId, ClientSecret clientSecret, ApplicationSecretSettings secretSettings, List<? extends ProtectedResourceFeature> features) {
        String id = RandomString.generate();
        ProtectedResource toSave = new ProtectedResource();
        toSave.setId(id);
        toSave.setName(name);
        toSave.setClientId(clientId);
        toSave.setDomainId(domainId);
        toSave.setType(MCP_SERVER);
        toSave.setCreatedAt(new Date());
        toSave.setUpdatedAt(new Date());
        toSave.setDescription("description");
        String uniqueBase = "https://resource.example.com/" + id;
        toSave.setResourceIdentifiers(List.of(
                uniqueBase + "/identifier1",
                uniqueBase + "/identifier2"
        ));
        toSave.setSecretSettings(List.of(secretSettings));
        
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("oauthClientId");
        settings.setOauth(oauth);

        SecretExpirationSettings secretExpirationSettings = new SecretExpirationSettings();
        secretExpirationSettings.setEnabled(true);
        secretExpirationSettings.setExpiryTimeSeconds(3600L);
        settings.setSecretExpirationSettings(secretExpirationSettings);
        toSave.setSettings(settings);

        toSave.setClientSecrets(List.of(clientSecret));
        toSave.setFeatures(features);
        toSave.setCertificate(UUID.randomUUID().toString());
        return toSave;
    }

}
