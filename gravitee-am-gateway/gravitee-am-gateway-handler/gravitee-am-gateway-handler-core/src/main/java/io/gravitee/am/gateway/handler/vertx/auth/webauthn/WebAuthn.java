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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.WebAuthnImpl;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.CredentialStore;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;

public interface WebAuthn {

    /**
     * Create a WebAuthN auth provider
     *
     * @param vertx the Vertx instance.
     * @param store the user store used to load credentials.
     * @return the auth provider.
     */
    static WebAuthn create(Vertx vertx, CredentialStore store) {
        return create(vertx, new WebAuthnOptions(), store);
    }

    /**
     * Create a WebAuthN auth provider
     *
     * @param vertx the Vertx instance.
     * @param options the custom options to the provider.
     * @param store the user store used to load credentials.
     * @return the auth provider.
     */
    static WebAuthn create(Vertx vertx, WebAuthnOptions options, CredentialStore store) {
        return new WebAuthnImpl(vertx, options, store);
    }

    WebAuthn createCredentialsOptions(JsonObject user, Handler<AsyncResult<JsonObject>> handler);

    /**
     * Same as {@link #createCredentialsOptions(JsonObject, Handler)} but returning a Future.
     */
    default Future<JsonObject> createCredentialsOptions(JsonObject user) {
        Promise<JsonObject> promise = Promise.promise();
        createCredentialsOptions(user, promise);
        return promise.future();
    }

    void authenticate(WebAuthnCredentials authInfo, Handler<AsyncResult<User>> handler);

    default Future<User> authenticate(WebAuthnCredentials authInfo) {
        Promise<User> promise = Promise.promise();
        authenticate(authInfo, promise);
        return promise.future();
    }

    default void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {
        authenticate(new WebAuthnCredentials(authInfo), handler);
    }

    /**
     * Generates getAssertion request. If the auth provider is configured with {@code RequireResidentKey} and
     * the username is null then the generated assertion will be a RK assertion (Usernameless).
     *
     * @param username the unique user identified
     * @param handler server encoded get assertion request
     * @return fluent self.
     */
    WebAuthn getCredentialsOptions(@Nullable String username, Handler<AsyncResult<JsonObject>> handler);

    /**
     * Same as {@link #getCredentialsOptions(String, Handler)} but returning a Future.
     */
    default Future<JsonObject> getCredentialsOptions(@Nullable String username) {
        Promise<JsonObject> promise = Promise.promise();
        getCredentialsOptions(username, promise);
        return promise.future();
    }
}
