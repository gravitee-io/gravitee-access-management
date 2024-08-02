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

package io.gravitee.am.gateway.handler.manager.subject;


import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SubjectManagerV2Test {

    @Mock
    private Domain domain;

    @Mock
    private UserService userService;

    @Mock
    private ClientManager clientManager;

    private SubjectManager cut;

    @BeforeEach
    protected void setup() {
        cut = new SubjectManagerV2(userService, clientManager, domain);
    }

    @Test
    public void should_generate_sub_using_userid() {
        final var user = new User();
        user.setExternalId(UUID.randomUUID().toString());
        user.setSource(UUID.randomUUID().toString());

        Assertions.assertEquals(user.getSource() + ":" + user.getExternalId(), cut.generateInternalSubFrom(user));
        Assertions.assertEquals(3, UUID.fromString(cut.generateSubFrom(user)).version());
    }

    @Test
    public void find_user_by_sub_requires_valid_sub() {
        final var token = new JWT();
        token.setInternalSub(UUID.randomUUID().toString());

        TestObserver<User> observer = cut.findUserBySub(token).test();
        observer.assertError(IllegalArgumentException.class);
        verify(userService, never()).findByDomainAndExternalIdAndSource(any(), any(), any());
    }

    @Test
    public void should_find_user_by_sub() throws Exception {
        final var user = new User();
        user.setExternalId(UUID.randomUUID().toString());
        user.setSource(UUID.randomUUID().toString());
        final var token = new JWT();
        cut.updateJWT(token, user);

        when(userService.findByDomainAndExternalIdAndSource(any(), any(), any())).thenReturn(Maybe.just(user));
        TestObserver<User> observer = cut.findUserBySub(token).test();

        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValueCount(1);
    }

    @Test
    public void should_provide_principal() throws Exception {
        final var user = new User();
        user.setExternalId(UUID.randomUUID().toString());
        user.setSource(UUID.randomUUID().toString());
        final var token = new JWT();
        cut.updateJWT(token, user);

        when(userService.findByDomainAndExternalIdAndSource(any(), any(), any())).thenReturn(Maybe.just(user));
        TestObserver<io.gravitee.am.identityprovider.api.User> observer = cut.getPrincipal(token).test();

        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValueCount(1);
    }

    @Test
    public void should_not_provide_principal_as_internalSub_is_not_composed() throws Exception {
        final var token = new JWT();
        token.setInternalSub(UUID.randomUUID().toString());
        TestObserver<io.gravitee.am.identityprovider.api.User> observer = cut.getPrincipal(token).test();

        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValueCount(0);

        verify(userService, never()).findByDomainAndExternalIdAndSource(any(), any(), any());
    }
}
