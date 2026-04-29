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
package io.gravitee.am.gateway.handler.root.resources.auth.provider;

import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class UsersDomainWhitelistValidatorTest {

    private UsersDomainWhitelistValidator validator;

    @Before
    public void setUp() {
        validator = new UsersDomainWhitelistValidator();
    }

    @Test
    public void shouldPassThrough_whenWhitelistIsNull() {
        DefaultUser user = new DefaultUser("jdoe@example.com");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, null).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldPassThrough_whenWhitelistIsEmpty() {
        DefaultUser user = new DefaultUser("jdoe@example.com");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, Collections.emptyList()).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenUsernameDomainMatches() {
        DefaultUser user = new DefaultUser("jdoe@acme.com");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme.com")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenEmailDomainMatches() {
        DefaultUser user = new DefaultUser("jdoe");
        user.setEmail("jdoe@acme.com");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme.com")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenUsernameDomainMatchesIgnoringCase_mixedCasingInUsername() {
        // AM-6967: jdoe@Acme-Corp.org must match whitelist entry acme-corp.org per RFC 4343
        DefaultUser user = new DefaultUser("jdoe@Acme-Corp.org");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme-corp.org")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenUsernameDomainMatchesIgnoringCase_upperCaseInUsername() {
        // AM-6967
        DefaultUser user = new DefaultUser("jdoe@ACME-CORP.ORG");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme-corp.org")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenWhitelistEntryIsMixedCase() {
        // AM-6967: whitelist entry is case-insensitive too
        DefaultUser user = new DefaultUser("jdoe@acme-corp.org");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("Acme-Corp.org")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldAccept_whenEmailDomainMatchesIgnoringCase() {
        // AM-6967
        DefaultUser user = new DefaultUser("jdoe");
        user.setEmail("jdoe@Acme-Corp.org");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme-corp.org")).test();

        observer.assertComplete();
        observer.assertValue(user);
    }

    @Test
    public void shouldReject_whenNeitherUsernameNorEmailDomainMatches() {
        DefaultUser user = new DefaultUser("jdoe@evil.com");
        user.setEmail("jdoe@evil.com");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme.com")).test();

        observer.assertError(LoginCallbackFailedException.class);
    }

    @Test
    public void shouldReject_whenUsernameIsNotAnEmailAndEmailIsNull() {
        DefaultUser user = new DefaultUser("jdoe");

        TestObserver<User> observer = validator.checkDomainWhitelist(user, List.of("acme.com")).test();

        observer.assertError(LoginCallbackFailedException.class);
    }
}
