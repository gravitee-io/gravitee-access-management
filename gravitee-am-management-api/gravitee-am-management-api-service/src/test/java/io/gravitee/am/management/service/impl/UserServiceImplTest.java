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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplTest {

    @Mock
    protected io.gravitee.am.service.UserService userService;

    @InjectMocks
    private UserService service = new UserServiceImpl();

    @Test
    public void shouldThrowAnExceptionOnUpdateExternalUserWithForceResetPassword() {
        // given

        UpdateUser updateUser = new UpdateUser();
        updateUser.setForceResetPassword(true);

        User user = new User();
        user.setInternal(false);

        Mockito.when(userService.findById(any(), any(), any())).thenReturn(Single.just(user));

        // when
        TestObserver<User> observer = new TestObserver<>();
        service.update(ReferenceType.DOMAIN, "id", "id", updateUser, new DefaultUser())
                .subscribe(observer);

        // then
        observer.assertError(throwable -> throwable.getMessage().equals("forceResetPassword is forbidden on external users"));
    }
}