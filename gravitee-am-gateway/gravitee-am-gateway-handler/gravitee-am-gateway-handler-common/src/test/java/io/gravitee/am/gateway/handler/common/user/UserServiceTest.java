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
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @Mock
    private io.gravitee.am.service.UserService commonLayerUserService;

    @Mock
    private UserStore userStore;

    @InjectMocks
    private UserServiceImpl cut;

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
