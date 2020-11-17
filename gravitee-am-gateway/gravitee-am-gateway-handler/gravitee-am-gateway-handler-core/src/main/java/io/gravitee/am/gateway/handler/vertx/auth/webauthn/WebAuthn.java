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
/*
 * Copyright 2019 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.impl.WebAuthnImpl;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.webauthn.Authenticator;

import java.util.List;
import java.util.function.Function;

/**
 * Factory interface for creating WebAuthN based AuthenticationProvider instances.
 *
 * @author Paulo Lopes
 */
// TODO to remove when updating to vert.x 4
public interface WebAuthn {

    /**
     * Create a WebAuthN auth provider
     *
     * @param vertx the Vertx instance.
     * @return the auth provider.
     */
    static WebAuthn create(Vertx vertx) {
        return create(vertx, new WebAuthnOptions());
    }

    /**
     * Create a WebAuthN auth provider
     *
     * @param vertx the Vertx instance.
     * @param options the custom options to the provider.
     * @return the auth provider.
     */
    static WebAuthn create(Vertx vertx, WebAuthnOptions options) {
        return new WebAuthnImpl(vertx, options);
    }

    /**
     * Gets a challenge and any other parameters for the {@code navigator.credentials.create()} call.
     *
     * The object being returned is described here <a href="https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptions">https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptions</a>
     * @param user    - the user object with name and optionally displayName and icon
     * @param handler server encoded make credentials request
     * @return fluent self
     */
    WebAuthn createCredentialsOptions(JsonObject user, Handler<AsyncResult<JsonObject>> handler);

    /**
     * Same as {@link #createCredentialsOptions(JsonObject, Handler)} but returning a Future.
     */
    default Future<JsonObject> createCredentialsOptions(JsonObject user) {
        Promise<JsonObject> promise = Promise.promise();
        createCredentialsOptions(user, promise);
        return promise.future();
    }

    /**
     * Creates an assertion challenge and any other parameters for the {@code navigator.credentials.get()} call.
     * If the auth provider is configured with {@code RequireResidentKey} and the username is null then the
     * generated assertion will be a RK assertion (Usernameless).
     *
     * The object being returned is described here <a href="https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptions">https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptions</a>
     *
     * @param name the unique user identified
     * @param handler server encoded get assertion request
     * @return fluent self.
     */
    WebAuthn getCredentialsOptions(@Nullable String name, Handler<AsyncResult<JsonObject>> handler);

    /**
     * Same as {@link #getCredentialsOptions(String, Handler)} but returning a Future.
     */
    default Future<JsonObject> getCredentialsOptions(@Nullable String username) {
        Promise<JsonObject> promise = Promise.promise();
        getCredentialsOptions(username, promise);
        return promise.future();
    }

    void authenticate(WebAuthnCredentials authInfo, Handler<AsyncResult<User>> handler);

    /**
     * Provide a {@link Function} that can fetch {@link Authenticator}s from a backend given the incomplete
     * {@link Authenticator} argument.
     *
     * The implementation must consider the following fields <strong>exclusively</strong>, while performing the lookup:
     * <ul>
     *   <li>{@link Authenticator#getUserName()}</li>
     *   <li>{@link Authenticator#getCredID()} ()}</li>
     * </ul>
     *
     * It may return more than 1 result, for example when a user can be identified using different modalities.
     * To signal that a user is not allowed/present on the system, a failure should be returned, not {@code null}.
     *
     * The function signature is as follows:
     *
     * {@code (Authenticator) -> Future<List<Authenticator>>>}
     *
     * <ul>
     *   <li>{@link Authenticator} the incomplete authenticator data to lookup.</li>
     *   <li>{@link Future}async result with a list of authenticators.</li>
     * </ul>
     *
     * @param fetcher fetcher function.
     * @return fluent self.
     */
    WebAuthn authenticatorFetcher(Function<Authenticator, Future<List<Authenticator>>> fetcher);

    /**
     * Provide a {@link Function} that can update or insert a {@link Authenticator}.
     * The function <strong>should</strong> store a given authenticator to a persistence storage.
     *
     * When an authenticator is already present, this method <strong>must</strong> at least update
     * {@link Authenticator#getCounter()}, and is not required to perform any other update.
     *
     * For new authenticators, the whole object data <strong>must</strong> be persisted.
     *
     * The function signature is as follows:
     *
     * {@code (Authenticator) -> Future<Void>}
     *
     * <ul>
     *   <li>{@link Authenticator} the authenticator data to update.</li>
     *   <li>{@link Future}async result of the operation.</li>
     * </ul>
     *
     * @param updater updater function.
     * @return fluent self.
     */
    WebAuthn authenticatorUpdater(Function<Authenticator, Future<Void>> updater);

    /**
     * Getter to the instance FIDO2 Meta Data Service.
     * @return the MDS instance.
     */
    MetaDataService metaDataService();
}
