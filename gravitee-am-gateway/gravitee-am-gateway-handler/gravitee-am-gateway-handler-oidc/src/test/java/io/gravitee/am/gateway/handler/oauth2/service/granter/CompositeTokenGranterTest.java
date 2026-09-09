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
package io.gravitee.am.gateway.handler.oauth2.service.granter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CompositeTokenGranterTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CompositeTokenGranter compositeTokenGranter;

    @Test
    public void shouldRecordRequestContextWhenGrantIsDenied() {
        TokenRequest tokenRequest = tokenRequest(GrantType.CLIENT_CREDENTIALS);
        Client client = client();
        TokenGranter granter = mock(TokenGranter.class);
        when(granter.handle(GrantType.CLIENT_CREDENTIALS, client)).thenReturn(true);
        when(granter.grant(tokenRequest, client)).thenReturn(Single.error(new InvalidScopeException("Invalid scope")));
        compositeTokenGranter.addTokenGranter(GrantType.CLIENT_CREDENTIALS, granter);

        compositeTokenGranter.grant(tokenRequest, client)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(InvalidScopeException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage())
                .contains("Invalid scope",
                        "\"GRANT_TYPE\":\"" + GrantType.CLIENT_CREDENTIALS + "\"",
                        "\"SCOPE\":\"read\"",
                        "\"RESOURCE\":\"https://mcp.example.com/api\"");
        assertThat(audit.getAccessPoint().getAlternativeId()).isEqualTo("client-id");
    }

    @Test
    public void shouldRecordRequestContextWhenGrantWithResponseIsDenied() {
        TokenRequest tokenRequest = tokenRequest(GrantType.TOKEN_EXCHANGE);
        Client client = client();
        TokenGranter granter = mock(TokenGranter.class);
        when(granter.handle(GrantType.TOKEN_EXCHANGE, client)).thenReturn(true);
        when(granter.grant(any(TokenRequest.class), any(), any(Client.class)))
                .thenReturn(Single.error(new InvalidScopeException("Invalid scope")));
        compositeTokenGranter.addTokenGranter(GrantType.TOKEN_EXCHANGE, granter);

        compositeTokenGranter.grant(tokenRequest, null, client)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(InvalidScopeException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getMessage())
                .contains("Invalid scope", "\"GRANT_TYPE\":\"" + GrantType.TOKEN_EXCHANGE + "\"", "\"SCOPE\":\"read\"");
    }

    @Test
    public void shouldRecordRequestContextWhenGrantTypeIsUnsupported() {
        TokenRequest tokenRequest = tokenRequest("unknown-grant");
        Client client = client();

        compositeTokenGranter.grant(tokenRequest, client)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(UnsupportedGrantTypeException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getMessage())
                .contains("Unsupported grant type", "\"GRANT_TYPE\":\"unknown-grant\"", "\"SCOPE\":\"read\"");
    }

    @Test
    public void shouldRecordWhatAnIdJagRequestAskedForWhenItIsDenied() {
        TokenRequest tokenRequest = tokenRequest(GrantType.TOKEN_EXCHANGE);
        tokenRequest.setResources(Set.of("https://calendar.acme.com"));
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.REQUESTED_TOKEN_TYPE, TokenType.ID_JAG);
        parameters.add(Parameters.AUDIENCE, "https://auth.acme.com");
        tokenRequest.setParameters(parameters);

        Client client = client();
        TokenGranter granter = mock(TokenGranter.class);
        when(granter.handle(GrantType.TOKEN_EXCHANGE, client)).thenReturn(true);
        when(granter.grant(tokenRequest, client))
                .thenReturn(Single.error(new InvalidResourceException("This application is not configured for audience")));
        compositeTokenGranter.addTokenGranter(GrantType.TOKEN_EXCHANGE, granter);

        compositeTokenGranter.grant(tokenRequest, client)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(InvalidResourceException.class);

        Audit audit = captureTokenAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage())
                .contains("not configured for audience",
                        "\"REQUESTED_TOKEN_TYPE\":\"" + TokenType.ID_JAG + "\"",
                        "\"AUDIENCE\":\"https://auth.acme.com\"",
                        "\"RESOURCE\":\"https://calendar.acme.com\"");
    }

    private TokenRequest tokenRequest(String grantType) {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setGrantType(grantType);
        tokenRequest.setScopes(Set.of("read"));
        tokenRequest.setResources(Set.of("https://mcp.example.com/api"));
        return tokenRequest;
    }

    private Client client() {
        Client client = new Client();
        client.setId("client-technical-id");
        client.setClientId("client-id");
        client.setDomain("domain-id");
        return client;
    }

    private Audit captureTokenAudit() {
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(auditCaptor.capture());
        AuditBuilder<?> capturedBuilder = auditCaptor.getValue();
        assertThat(capturedBuilder).isInstanceOf(ClientTokenAuditBuilder.class);
        return capturedBuilder.build(new ObjectMapper());
    }
}
