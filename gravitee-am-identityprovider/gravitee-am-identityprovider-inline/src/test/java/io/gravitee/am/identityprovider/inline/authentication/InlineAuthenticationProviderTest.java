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
package io.gravitee.am.identityprovider.inline.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.inline.authentication.provisioning.InlineInMemoryUserDetailsManager;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class InlineAuthenticationProviderTest {

    @InjectMocks
    private InlineAuthenticationProvider inlineAuthenticationProvider = new InlineAuthenticationProvider();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InlineInMemoryUserDetailsManager userDetailsService;

    @Test
    public void shouldLoadUserByUsername_authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("username");
        when(authentication.getCredentials()).thenReturn("password");

        io.gravitee.am.identityprovider.inline.model.User user = mock(io.gravitee.am.identityprovider.inline.model.User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getPassword()).thenReturn("password");

        when(userDetailsService.loadUserByUsername("username")).thenReturn(Maybe.just(user));
        when(passwordEncoder.matches((String) authentication.getCredentials(), user.getPassword())).thenReturn(true);

        TestObserver<User> testObserver = inlineAuthenticationProvider.loadUserByUsername(authentication).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "username".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("username");
        when(authentication.getCredentials()).thenReturn("password");

        io.gravitee.am.identityprovider.inline.model.User user = mock(io.gravitee.am.identityprovider.inline.model.User.class);

        when(userDetailsService.loadUserByUsername("username")).thenReturn(Maybe.just(user));

        TestObserver<User> testObserver = inlineAuthenticationProvider.loadUserByUsername(authentication).test();
        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("username");

        when(userDetailsService.loadUserByUsername("username")).thenReturn(Maybe.error(new UsernameNotFoundException("username")));

        TestObserver<User> testObserver = inlineAuthenticationProvider.loadUserByUsername(authentication).test();
        testObserver.assertError(UsernameNotFoundException.class);
    }

}
