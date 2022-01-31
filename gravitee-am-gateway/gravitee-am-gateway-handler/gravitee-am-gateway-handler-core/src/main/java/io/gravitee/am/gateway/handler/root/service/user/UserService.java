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
package io.gravitee.am.gateway.handler.root.service.user;

import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    Maybe<UserToken> verifyToken(String token);

    Single<UserToken> extractSessionFromIdToken(String idToken);

    Single<RegistrationResponse> register(Client client, User user, io.gravitee.am.identityprovider.api.User principal);

    Single<RegistrationResponse> confirmRegistration(Client client, User user, io.gravitee.am.identityprovider.api.User principal);

    Single<ResetPasswordResponse> resetPassword(Client client, User user, io.gravitee.am.identityprovider.api.User principal);

    Completable forgotPassword(ForgotPasswordParameters inputParameters, Client client, io.gravitee.am.identityprovider.api.User principal);

    Completable logout(User user, boolean invalidateTokens, io.gravitee.am.identityprovider.api.User principal);

    Single<User> addFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal);

    Completable setMfaEnrollmentSkippedTime(Client client, User user);

    default Single<RegistrationResponse> register(Client client, User user) {
        return register(client, user, null);
    }

    default Single<ResetPasswordResponse> resetPassword(Client client, User user) {
        return resetPassword(client, user, null);
    }

    default Completable forgotPassword(String email, Client client) {
        ForgotPasswordParameters params = new ForgotPasswordParameters(email, false, false);
        return forgotPassword(params, client, null);
    }

    default Single<RegistrationResponse> confirmRegistration(Client client, User user) {
        return confirmRegistration(client, user, null);
    }

}
