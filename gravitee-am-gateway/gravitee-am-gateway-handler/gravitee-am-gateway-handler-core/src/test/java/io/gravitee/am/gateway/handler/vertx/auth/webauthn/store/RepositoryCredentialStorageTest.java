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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.store;

import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.ext.auth.webauthn4j.Authenticator;
import io.vertx.ext.auth.webauthn4j.AttestationCertificates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class RepositoryCredentialStorageTest {

    @InjectMocks
    private RepositoryCredentialStorage credentialStorage = new RepositoryCredentialStorage();

    @Mock
    private CredentialGatewayService credentialService;

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private Domain domain;

    @Test
    void returns_stable_plausible_credentials_for_an_unknown_username() throws Exception {
        credentialStorage.maxAllowCredentials = 5;
        when(credentialService.findByUsername(any(), eq("username"), intThat(i -> i == 5))).thenReturn(Flowable.empty());
        when(jwtBuilder.sign(any())).thenReturn("part1.part2.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

        List<Authenticator> authenticators = await(credentialStorage.find("username", null));
        List<Authenticator> again = await(credentialStorage.find("username", null));

        assertThat(authenticators).isNotEmpty();
        assertThat(again.get(0).getCredID()).isEqualTo(authenticators.get(0).getCredID());
        verify(jwtBuilder, times(4)).sign(any());
    }

    @Test
    void returns_different_plausible_credentials_for_different_unknown_usernames() throws Exception {
        credentialStorage.maxAllowCredentials = 5;
        when(credentialService.findByUsername(any(), eq("username"), anyInt())).thenReturn(Flowable.empty());
        when(credentialService.findByUsername(any(), eq("username2"), anyInt())).thenReturn(Flowable.empty());
        when(jwtBuilder.sign(any()))
                .thenReturn("part1.part2.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")
                .thenReturn("part1.part2.-sVkXqTOhFeJwQXyH3WhuNJfAfnRkVM6llEu6k46iqY");

        List<Authenticator> authenticators = await(credentialStorage.find("username", null));
        List<Authenticator> others = await(credentialStorage.find("username2", null));

        assertThat(authenticators).isNotEmpty();
        assertThat(others).isNotEmpty();
        assertThat(others.get(0).getCredID()).isNotEqualTo(authenticators.get(0).getCredID());
        verify(jwtBuilder, times(4)).sign(any());
    }

    @Test
    void returns_the_credentials_of_the_user_up_to_max_allow_credentials() throws Exception {
        credentialStorage.maxAllowCredentials = 5;
        when(credentialService.findByUsername(any(), eq("username"), eq(5))).thenReturn(Flowable.just(credential("username", "credID")));

        List<Authenticator> authenticators = await(credentialStorage.find("username", null));

        assertThat(authenticators).singleElement().satisfies(authenticator -> {
            assertThat(authenticator.getCredID()).isEqualTo("credID");
            assertThat(authenticator.getUsername()).isEqualTo("username");
        });
        verify(jwtBuilder, never()).sign(any());
    }

    @Test
    void returns_every_credential_of_the_user_when_max_allow_credentials_is_not_set() throws Exception {
        credentialStorage.maxAllowCredentials = -1;
        when(credentialService.findByUsername(any(), eq("username"))).thenReturn(Flowable.just(credential("username", "credID")));

        assertThat(await(credentialStorage.find("username", null))).extracting(Authenticator::getCredID).containsExactly("credID");
        verify(jwtBuilder, never()).sign(any());
    }

    @Test
    void narrows_the_credentials_of_the_user_to_the_credential_id() throws Exception {
        credentialStorage.maxAllowCredentials = -1;
        when(credentialService.findByUsername(any(), eq("username")))
                .thenReturn(Flowable.just(credential("username", "first"), credential("username", "second")));

        assertThat(await(credentialStorage.find("username", "second"))).extracting(Authenticator::getCredID).containsExactly("second");
    }

    @Test
    void finds_by_credential_id_without_username() throws Exception {
        when(credentialService.findByCredentialId(any(), eq("credID"))).thenReturn(Flowable.just(credential("username", "credID")));

        assertThat(await(credentialStorage.find(null, "credID"))).extracting(Authenticator::getUsername).containsExactly("username");
    }

    @Test
    void creates_a_new_credential_with_a_zero_aaguid_for_none_attestation() throws Exception {
        when(credentialService.findByCredentialId(any(), eq("credID"))).thenReturn(Flowable.empty());
        when(credentialService.create(any(), any())).thenAnswer(invocation -> Single.just(invocation.getArgument(1)));

        await(credentialStorage.storeCredential(new Authenticator()
                .setUsername("username")
                .setCredID("credID")
                .setPublicKey("publicKey")
                .setCounter(1)
                .setFmt("none")
                .setAaguid("fbfc3007-154e-4ecc-8c0b-6e020557d7bd")
                .setAttestationCertificates(new AttestationCertificates())));

        ArgumentCaptor<Credential> created = ArgumentCaptor.forClass(Credential.class);
        verify(credentialService).create(any(), created.capture());
        assertThat(created.getValue().getAaguid()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(created.getValue().getAttestationStatementFormat()).isEqualTo("none");
        assertThat(created.getValue().getAttestationStatement()).isEqualTo("{}");
        assertThat(created.getValue().getCounter()).isEqualTo(1L);
    }

    @Test
    void updates_the_counter_of_an_existing_credential() throws Exception {
        Credential existing = credential("username", "credID");
        existing.setCounter(1L);
        when(credentialService.findByCredentialId(any(), eq("credID"))).thenReturn(Flowable.just(existing));
        when(credentialService.update(any(), any(Credential.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(1)));

        await(credentialStorage.updateCounter(new Authenticator().setUsername("username").setCredID("credID").setCounter(7)));

        assertThat(existing.getCounter()).isEqualTo(7L);
        verify(credentialService, never()).create(any(), any());
    }

    private static Credential credential(String username, String credentialId) {
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setCredentialId(credentialId);
        return credential;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
