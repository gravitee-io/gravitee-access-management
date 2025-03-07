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

package io.gravitee.am.gateway.handler.common.user;


import io.gravitee.am.common.exception.mfa.InvalidFactorAttributeException;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.gateway.handler.common.user.impl.UserGatewayServiceImplV2;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UserGatewayServiceV2Test {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserValidator userValidator;

    @Mock
    private UserStore userStore;

    @InjectMocks
    private UserGatewayServiceImplV2 cut = new UserGatewayServiceImplV2();

    @BeforeEach
    public void init() {
        ReflectionTestUtils.setField(cut, "resilientMode", false);
        lenient().when(userValidator.validate(any())).thenReturn(Completable.complete());
    }

    @Test
    public void shouldFindById_into_cache() throws Exception {
        when(userStore.get(any())).thenReturn(Maybe.just(new User()));

        TestObserver<User> observer = cut.findById(UUID.randomUUID().toString()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).get(any());
        verify(userRepository, never()).findById(anyString());
    }

    @Test
    public void shouldFindById_into_Database() throws Exception {
        when(userStore.get(any())).thenReturn(Maybe.empty());
        when(userRepository.findById(anyString())).thenReturn(Maybe.just(new User()));

        TestObserver<User> observer = cut.findById(UUID.randomUUID().toString()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).get(any());
        verify(userRepository).findById(anyString());
    }

    @Test
    public void shouldCreate_and_cache_value() throws Exception {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        when(userStore.add(any())).thenReturn(Maybe.empty());
        when(userRepository.create(any())).thenReturn(Single.just(user));

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
        verify(userRepository).create(any());
    }

    @Test
    public void shouldSkipCache_if_create_fails_due_to_connection_error_resilientMode_false() throws Exception {
        when(userRepository.create(any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(err -> err.getCause() instanceof RepositoryConnectionException);

        verify(userStore, never()).add(any());
    }

    @Test
    public void shouldNotSkipCache_if_create_fails_due_to_connection_error_resilientMode_true() throws Exception {
        ReflectionTestUtils.setField(cut, "resilientMode", true);

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        when(userRepository.create(any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));
        when(userStore.add(any())).thenReturn(Maybe.just(user));

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
    }

    @Test
    public void shouldSkipCache_if_create_fails_due_to_technicalError_resilientMode_true() throws Exception {
        ReflectionTestUtils.setField(cut, "resilientMode", true);
        when(userRepository.create(any())).thenReturn(Single.error(new TechnicalException(new RuntimeException())));

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(err -> err.getCause() instanceof TechnicalException);


        verify(userStore, never()).add(any());
    }

    @Test
    public void shouldUpdate_and_cache_value() throws Exception {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        when(userStore.add(any())).thenReturn(Maybe.empty());
        when(userRepository.update(any(), any())).thenReturn(Single.just(user));

        TestObserver<User> observer = cut.update(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
        verify(userRepository).update(any(), any());
    }

    @Test
    public void shouldSkipCache_if_update_fails_due_to_connection_error_resilientMode_false() throws Exception {
        when(userRepository.update(any(), any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        TestObserver<User> observer = cut.update(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(err -> err.getCause() instanceof RepositoryConnectionException);

        verify(userStore, never()).add(any());
    }

    @Test
    public void shouldNotSkipCache_if_update_fails_due_to_connection_error_resilientMode_true() throws Exception {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        ReflectionTestUtils.setField(cut, "resilientMode", true);
        when(userRepository.update(any(), any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));
        when(userStore.add(any())).thenReturn(Maybe.just(user));

        TestObserver<User> observer = cut.update(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
    }

    @Test
    public void shouldSkipCache_if_update_fails_due_to_runtime_error_resilientMode_true() throws Exception {
        ReflectionTestUtils.setField(cut, "resilientMode", true);
        when(userRepository.update(any(), any())).thenReturn(Single.error(new RuntimeException()));

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        TestObserver<User> observer = cut.update(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(RuntimeException.class);

        verify(userStore, never()).add(any());
    }

    @Test
    public void shouldUpsertFactor_SMS() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33606060606"));

        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setId(userid);

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(userStore.add(any())).thenAnswer(args -> Maybe.just(args.getArguments()[0]));
        when(userRepository.findById(userid)).thenReturn(Maybe.just(user));
        when(userRepository.update(any(), any())).thenReturn(Single.just(user));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(userRepository).update(argThat(userUpdated -> userUpdated.getFactors() != null
                && !userUpdated.getFactors().isEmpty()
                && userUpdated.getFactors().get(0).getFactorId().equals(enrolledFactor.getFactorId()) ), any());
    }

    @Test
    public void shouldUpsertFactor_Email() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, "test@acme.com"));

        User user = new User();
        user.setId(userid);
        user.setUsername(UUID.randomUUID().toString());
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        when(userStore.get(userid)).thenReturn(Maybe.just(new User()));
        when(userStore.add(any())).thenAnswer(args -> Maybe.just(args.getArguments()[0]));
        when(userRepository.update(any(), any())).thenReturn(Single.just(user));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(userRepository).update(argThat(userupdated -> userupdated.getFactors() != null
                && !userupdated.getFactors().isEmpty()
                && userupdated.getFactors().get(0).getFactorId().equals(enrolledFactor.getFactorId()) ), any());
    }

    @Test
    public void shouldNotUpsertFactor_MissingPhoneNumber() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, null));

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(userRepository.findById(userid)).thenReturn(Maybe.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidFactorAttributeException.class);

        verify(userRepository, never()).update(any());
    }

    @Test
    public void shouldNotUpsertFactor_MissingEmail() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, null));

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(userRepository.findById(userid)).thenReturn(Maybe.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidFactorAttributeException.class);
        verify(userRepository, never()).update(any());
    }
}
