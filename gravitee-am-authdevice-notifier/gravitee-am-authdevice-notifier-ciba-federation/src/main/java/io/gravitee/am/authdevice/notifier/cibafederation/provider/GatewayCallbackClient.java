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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.ext.web.client.WebClient;

public class GatewayCallbackClient {

    private final WebClient client;
    private final String clientId;
    private final String clientSecret;

    public GatewayCallbackClient(WebClient client, String clientId, String clientSecret) {
        this.client = client; this.clientId = clientId; this.clientSecret = clientSecret;
    }

    public Completable postCallback(String callbackUrl, String state, String tid, boolean validated,
                                    String idToken, String accessToken) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .set("state", state).set("tid", tid).set("validated", Boolean.toString(validated))
                .set("client_id", clientId).set("client_secret", clientSecret);
        if (idToken != null && !idToken.isBlank())     form.set(CibaFederationAuthenticationDeviceNotifierProvider.ID_TOKEN, idToken);
        if (accessToken != null && !accessToken.isBlank()) form.set(CibaFederationAuthenticationDeviceNotifierProvider.ACCESS_TOKEN, accessToken);
        return client.postAbs(callbackUrl).rxSendForm(form).flatMapCompletable(resp ->
            resp.statusCode() != 200
                ? Completable.error(new IllegalStateException("callback rejected: " + resp.statusCode()))
                : Completable.complete());
    }
}
