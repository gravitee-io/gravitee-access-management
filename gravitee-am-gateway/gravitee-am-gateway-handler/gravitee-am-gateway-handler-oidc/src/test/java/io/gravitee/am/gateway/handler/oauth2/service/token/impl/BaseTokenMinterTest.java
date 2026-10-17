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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseTokenMinterTest {

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private AuditService auditService;

    private final OAuth2Request request = new OAuth2Request();
    private final Client client = new Client();
    private final User user = new User();
    private final Token token = new AccessToken("token-value");
    private final AuditBuilder<?> successAudit = AuditBuilder.builder(ClientTokenAuditBuilder.class);

    @BeforeEach
    void setUp() {
        client.setClientId("client-id");
        user.setId("user-id");
    }

    @Test
    void shouldReturnMintedToken() {
        givenExecutionContextIsCreated();
        StubTokenMinter minter = minter(Single.just(new BaseTokenMinter.MintedToken(token, successAudit)));

        minter.mint(request, client, user).test().assertValue(token);
    }

    @Test
    void shouldReportSuccessAuditOfMintedToken() {
        givenExecutionContextIsCreated();
        StubTokenMinter minter = minter(Single.just(new BaseTokenMinter.MintedToken(token, successAudit)));

        minter.mint(request, client, user).test().assertComplete();

        verify(auditService).report(successAudit);
        verifyNoMoreInteractions(auditService);
    }

    @Test
    void shouldReportOnlyFailureAuditWhenMintingFails() {
        givenExecutionContextIsCreated();
        StubTokenMinter minter = minter(Single.error(new IllegalStateException("signing failed")));

        minter.mint(request, client, user).test().assertError(IllegalStateException.class);

        Audit audit = reportedAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(audit.getOutcome().getMessage()).contains("signing failed");
        assertThat(audit.getTarget().getId()).isEqualTo("user-id");
    }

    @Test
    void shouldReportFailureAuditWithoutMintingWhenExecutionContextCannotBeCreated() {
        when(executionContextFactory.create(any())).thenThrow(new IllegalStateException("no context"));
        StubTokenMinter minter = minter(Single.just(new BaseTokenMinter.MintedToken(token, successAudit)));

        minter.mint(request, client, user).test().assertError(IllegalStateException.class);

        assertThat(minter.executionContext).isNull();
        assertThat(reportedAudit().getOutcome().getMessage()).contains("no context");
    }

    @Test
    void shouldMintWithClientAndUserInExecutionContext() {
        givenExecutionContextIsCreated();
        StubTokenMinter minter = minter(Single.just(new BaseTokenMinter.MintedToken(token, successAudit)));

        minter.mint(request, client, user).test().assertComplete();

        assertThat(minter.executionContext.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY))
                .isInstanceOfSatisfying(ClientProperties.class, properties -> assertThat(properties.getClientId()).isEqualTo("client-id"));
        assertThat(minter.executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY))
                .isInstanceOfSatisfying(UserProperties.class, properties -> assertThat(properties.getId()).isEqualTo("user-id"));
    }

    @Test
    void shouldMintWithoutUserInExecutionContextWhenNoEndUser() {
        givenExecutionContextIsCreated();
        StubTokenMinter minter = minter(Single.just(new BaseTokenMinter.MintedToken(token, successAudit)));

        minter.mint(request, client, null).test().assertComplete();

        assertThat(minter.executionContext.getAttribute(ConstantKeys.USER_CONTEXT_KEY)).isNull();
    }

    private void givenExecutionContextIsCreated() {
        when(executionContextFactory.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Audit reportedAudit() {
        ArgumentCaptor<AuditBuilder> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(new ObjectMapper());
    }

    private StubTokenMinter minter(Single<BaseTokenMinter.MintedToken> result) {
        StubTokenMinter minter = new StubTokenMinter(result);
        minter.executionContextFactory = executionContextFactory;
        minter.auditService = auditService;
        return minter;
    }

    private static final class StubTokenMinter extends BaseTokenMinter {

        private final Single<MintedToken> result;
        private ExecutionContext executionContext;

        private StubTokenMinter(Single<MintedToken> result) {
            this.result = result;
        }

        @Override
        protected Single<MintedToken> mint(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext) {
            this.executionContext = executionContext;
            return result;
        }
    }
}
