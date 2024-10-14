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
import io.gravitee.am.gateway.handler.common.user.impl.UserServiceImpl;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private io.gravitee.am.service.UserService commonLayerUserService;

    @Mock
    private UserStore userStore;

    @Mock
    private AuditService auditService;


    @InjectMocks
    private UserServiceImpl cut = new UserServiceImpl();

    @Test
    public void shouldFindById_into_cache() throws Exception {
        when(userStore.get(any())).thenReturn(Maybe.just(new User()));

        TestObserver<User> observer = cut.findById(UUID.randomUUID().toString()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).get(any());
        verify(commonLayerUserService, never()).findById(any(UserId.class));
    }

    @Test
    public void shouldFindById_into_Database() throws Exception {
        when(userStore.get(any())).thenReturn(Maybe.empty());
        when(commonLayerUserService.findById(anyString())).thenReturn(Maybe.just(new User()));

        TestObserver<User> observer = cut.findById(UUID.randomUUID().toString()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).get(any());
        verify(commonLayerUserService).findById(anyString());
    }

    @Test
    public void shouldCreate_and_cache_value() throws Exception {
        User user = new User();
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);
        when(userStore.add(any())).thenReturn(Maybe.empty());
        when(commonLayerUserService.create(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
        verify(commonLayerUserService).create(any());
    }

    @Test
    public void shouldSkipCache_if_create_fails_due_to_connection_error() throws Exception {
        when(commonLayerUserService.create(any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));

        User user = new User();
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);

        TestObserver<User> observer = cut.create(user).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(RepositoryConnectionException.class);

        verify(userStore, never()).add(any());
    }
    @Test
    public void shouldUpdate_and_cache_value() throws Exception {
        when(userStore.add(any())).thenReturn(Maybe.empty());
        when(commonLayerUserService.update(any(), any())).thenReturn(Single.just(new User()));

        TestObserver<User> observer = cut.update(new User()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(userStore).add(any());
        verify(commonLayerUserService).update(any(), any());
    }

    @Test
    public void shouldSkipCache_if_update_fails_due_to_connection_error() throws Exception {
        when(commonLayerUserService.update(any(), any())).thenReturn(Single.error(new RepositoryConnectionException(new RuntimeException())));

        TestObserver<User> observer = cut.update(new User()).test();
        observer.await(5,TimeUnit.SECONDS);
        observer.assertError(RepositoryConnectionException.class);

        verify(userStore, never()).add(any());
    }

    @Test
    public void shouldUpsertFactor_SMS() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33606060606"));

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(userStore.add(any())).thenAnswer(args -> Maybe.just(args.getArguments()[0]));
        when(commonLayerUserService.findById(userid)).thenReturn(Maybe.just(new User()));
        when(commonLayerUserService.update(any(), any())).thenReturn(Single.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(commonLayerUserService).update(argThat(user -> user.getFactors() != null
                && !user.getFactors().isEmpty()
                && user.getFactors().get(0).getFactorId().equals(enrolledFactor.getFactorId()) ), any());
    }

    @Test
    public void shouldUpsertFactor_SMS_resetMfaSkippedAt() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33606060606"));

        User userProfile = new User();
        userProfile.setMfaEnrollmentSkippedAt(new Date());
        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(userStore.add(any())).thenAnswer(args -> Maybe.just(args.getArguments()[0]));
        when(commonLayerUserService.findById(userid)).thenReturn(Maybe.just(userProfile));
        ArgumentCaptor<User> userArgs = ArgumentCaptor.forClass(User.class);
        when(commonLayerUserService.update(userArgs.capture(), any())).thenReturn(Single.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(commonLayerUserService).update(argThat(user -> user.getFactors() != null
                && !user.getFactors().isEmpty()
                && user.getFactors().get(0).getFactorId().equals(enrolledFactor.getFactorId()) ), any());
        Assertions.assertNull(userArgs.getValue().getMfaEnrollmentSkippedAt());
    }

    @Test
    public void shouldUpsertFactor_Email() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, "test@acme.com"));

        when(userStore.get(userid)).thenReturn(Maybe.just(new User()));
        when(userStore.add(any())).thenAnswer(args -> Maybe.just(args.getArguments()[0]));
        when(commonLayerUserService.update(any(), any())).thenReturn(Single.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(commonLayerUserService).update(argThat(user -> user.getFactors() != null
                && !user.getFactors().isEmpty()
                && user.getFactors().get(0).getFactorId().equals(enrolledFactor.getFactorId()) ), any());
    }

    @Test
    public void shouldNotUpsertFactor_MissingPhoneNumber() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, null));

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(commonLayerUserService.findById(userid)).thenReturn(Maybe.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidFactorAttributeException.class);

        verify(commonLayerUserService, never()).update(any());
    }

    @Test
    public void shouldNotUpsertFactor_MissingEmail() {
        final var userid = "userid";
        final var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factorid");
        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, null));

        when(userStore.get(userid)).thenReturn(Maybe.empty());
        when(commonLayerUserService.findById(userid)).thenReturn(Maybe.just(new User()));

        final var observer = cut.upsertFactor(userid, enrolledFactor, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidFactorAttributeException.class);
        verify(commonLayerUserService, never()).update(any());
    }
}
