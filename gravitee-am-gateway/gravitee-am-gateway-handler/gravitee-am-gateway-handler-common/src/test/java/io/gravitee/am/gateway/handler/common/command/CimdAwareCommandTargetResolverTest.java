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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.command.impl.CimdAwareCommandTargetResolver;
import io.gravitee.am.gateway.handler.common.command.impl.DefaultCommandTargetResolver;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CimdAwareCommandTargetResolverTest {

    private static final String TEMPLATE_ID = "template-app-id";

    @Mock
    private DefaultCommandTargetResolver defaultCommandTargetResolver;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Mock
    private CimdMetadataService cimdMetadataService;

    private Domain domain;
    private Client template;
    private CimdAwareCommandTargetResolver resolver;

    @BeforeEach
    public void setUp() {
        domain = new Domain("domain-id");
        domain.setName("domain-name");
        template = new Client();
        template.setId(TEMPLATE_ID);
        template.setTemplate(true);

        resolver = new CimdAwareCommandTargetResolver(defaultCommandTargetResolver, domain, TEMPLATE_ID,
                clientSyncService, cimdMetadataDocumentService, cimdMetadataService);

        when(defaultCommandTargetResolver.resolveTargets()).thenReturn(Flowable.empty());
        when(clientSyncService.findById(TEMPLATE_ID)).thenReturn(Maybe.just(template));
    }

    @Test
    public void shouldSynthesizeTargetsFromDocumentsDeclaringACommandEndpoint() {
        CimdMetadataDocument optedIn = document("https://rp.example.com/metadata.json",
                "https://rp.example.com/commands", Duration.ofMinutes(5));
        CimdMetadataDocument notOptedIn = document("https://other.example.com/metadata.json",
                null, Duration.ofMinutes(5));
        when(cimdMetadataDocumentService.findByDomain(domain.getId()))
                .thenReturn(Flowable.just(optedIn, notOptedIn));
        Client synthesized = client(optedIn.getClientId());
        when(cimdMetadataService.synthesizeFromDocument(same(optedIn), same(template))).thenReturn(synthesized);

        resolver.resolveTargets().test()
                .assertResult(synthesized);
    }

    @Test
    public void shouldConcatDefaultTargetsWithCimdTargets() {
        Client preregistered = client("client-1");
        when(defaultCommandTargetResolver.resolveTargets()).thenReturn(Flowable.just(preregistered));
        CimdMetadataDocument document = document("https://rp.example.com/metadata.json",
                "https://rp.example.com/commands", Duration.ofMinutes(5));
        when(cimdMetadataDocumentService.findByDomain(domain.getId())).thenReturn(Flowable.just(document));
        Client synthesized = client(document.getClientId());
        when(cimdMetadataService.synthesizeFromDocument(same(document), same(template))).thenReturn(synthesized);

        resolver.resolveTargets().test()
                .assertResult(preregistered, synthesized);
    }

    @Test
    public void shouldDeduplicateTargetsByClientId() {
        Client preregistered = client("https://rp.example.com/metadata.json");
        when(defaultCommandTargetResolver.resolveTargets()).thenReturn(Flowable.just(preregistered));
        CimdMetadataDocument document = document("https://rp.example.com/metadata.json",
                "https://rp.example.com/commands", Duration.ofMinutes(5));
        when(cimdMetadataDocumentService.findByDomain(domain.getId())).thenReturn(Flowable.just(document));
        when(cimdMetadataService.synthesizeFromDocument(same(document), same(template)))
                .thenReturn(client(document.getClientId()));

        resolver.resolveTargets().test()
                .assertResult(preregistered);
    }

    @Test
    public void expiredDocumentsAreNotTargets() {
        CimdMetadataDocument expired = document("https://rp.example.com/metadata.json",
                "https://rp.example.com/commands", Duration.ofMinutes(-5));
        when(cimdMetadataDocumentService.findByDomain(domain.getId())).thenReturn(Flowable.just(expired));

        resolver.resolveTargets().test()
                .assertResult();
    }

    @Test
    public void missingTemplateYieldsNoCimdTargets() {
        when(clientSyncService.findById(TEMPLATE_ID)).thenReturn(Maybe.empty());

        resolver.resolveTargets().test()
                .assertResult();
        verifyNoInteractions(cimdMetadataDocumentService);
    }

    @Test
    public void synthesisFailureSkipsTheDocumentAndKeepsTheOthers() {
        CimdMetadataDocument broken = document("https://broken.example.com/metadata.json",
                "https://broken.example.com/commands", Duration.ofMinutes(5));
        CimdMetadataDocument valid = document("https://rp.example.com/metadata.json",
                "https://rp.example.com/commands", Duration.ofMinutes(5));
        when(cimdMetadataDocumentService.findByDomain(domain.getId())).thenReturn(Flowable.just(broken, valid));
        Client synthesized = client(valid.getClientId());
        when(cimdMetadataService.synthesizeFromDocument(same(broken), any()))
                .thenThrow(new InvalidClientMetadataException("stored metadata no longer valid"));
        when(cimdMetadataService.synthesizeFromDocument(same(valid), same(template))).thenReturn(synthesized);

        resolver.resolveTargets().test()
                .assertResult(synthesized);
    }

    @Test
    public void documentEnumerationErrorPropagates() {
        when(cimdMetadataDocumentService.findByDomain(domain.getId()))
                .thenReturn(Flowable.error(new RuntimeException("db unavailable")));

        resolver.resolveTargets().test()
                .assertError(RuntimeException.class);
        verify(cimdMetadataDocumentService).findByDomain(domain.getId());
    }

    private Client client(String clientId) {
        Client client = new Client();
        client.setClientId(clientId);
        client.setCommandEndpoint("https://rp.example.com/commands");
        return client;
    }

    private CimdMetadataDocument document(String clientId, String commandEndpoint, Duration ttl) {
        JsonObject metadata = new JsonObject()
                .put("client_id", clientId)
                .put("redirect_uris", new JsonArray().add("https://rp.example.com/cb"));
        if (commandEndpoint != null) {
            metadata.put("command_endpoint", commandEndpoint);
        }
        return CimdMetadataDocument.of(domain.getId(), clientId, metadata.encode(), ttl);
    }
}
