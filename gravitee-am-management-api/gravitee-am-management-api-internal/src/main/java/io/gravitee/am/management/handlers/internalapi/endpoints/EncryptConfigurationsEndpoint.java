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
package io.gravitee.am.management.handlers.internalapi.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult.Status;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionService;
import io.gravitee.am.repository.encryption.EncryptionKeys;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Optional;

/**
 * Encrypts the stored plugin configurations with the current key: {@code POST /_node/encryption/reencrypt}.
 * <p>
 * Called once the current key is declared on every Management API and Gateway node. Responds with one
 * result per plugin type: 200 when every plugin type is encrypted with the current key, 409 when another
 * call is still running one of them, 500 when one failed. Calling it again only runs what is not done.
 *
 * @author GraviteeSource Team
 */
public class EncryptConfigurationsEndpoint extends AbstractInternalApiEndpoint {

    private final ConfigurationEncryptionService configurationEncryptionService;

    public EncryptConfigurationsEndpoint(ConfigurationEncryptionService configurationEncryptionService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.configurationEncryptionService = configurationEncryptionService;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return "/encryption/reencrypt";
    }

    @Override
    public void handle(RoutingContext context) {
        Optional<String> keyId = configurationEncryptionService.currentKeyId();
        if (keyId.isEmpty()) {
            respondError(context, HttpStatusCode.BAD_REQUEST_400, "Encryption is not enabled with " + EncryptionKeys.ENABLED_PROPERTY);
            return;
        }

        configurationEncryptionService.encryptAll(keyId.get())
                .subscribe(
                        results -> respond(context, statusOf(results), new Report(keyId.get(), results)),
                        throwable -> respondFailure(context, throwable, "Unable to encrypt the plugin configurations"));
    }

    private static int statusOf(List<ConfigurationEncryptionResult> results) {
        if (results.stream().anyMatch(result -> result.status() == Status.FAILED)) {
            return HttpStatusCode.INTERNAL_SERVER_ERROR_500;
        }
        if (results.stream().anyMatch(result -> result.status() == Status.IN_PROGRESS)) {
            return HttpStatusCode.CONFLICT_409;
        }
        return HttpStatusCode.OK_200;
    }

    record Report(String keyId, List<ConfigurationEncryptionResult> results) {
    }
}
