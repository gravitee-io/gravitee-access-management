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
package io.gravitee.am.service.secrets.evaluators;

import io.gravitee.am.service.exception.NotImplementedException;
import io.gravitee.am.service.secrets.ref.SecretRef;
import io.gravitee.am.service.secrets.ref.SecretRefParser;
import io.gravitee.am.service.secrets.resolver.SecretResolver;
import io.gravitee.secrets.api.el.EvaluatedSecretsMethods;
import io.gravitee.secrets.api.el.SecretFieldAccessControl;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
class RefBasedEvaluatedSecretMethods implements EvaluatedSecretsMethods {

    private final SecretResolver resolver;

    @Override
    public String get(String nameOrUri, String key) {
        SecretRef secretRef = SecretRefParser.parse(nameOrUri).useExplicitKey(key);
        return resolver.resolveSecretFromUrl(secretRef.toSecretURL()).blockingGet().asString();
    }

    @Override
    public String get(String nameOrUri) {
        SecretRef secretRef = SecretRefParser.parse(nameOrUri);
        return resolver.resolveSecretFromUrl(secretRef.toSecretURL()).blockingGet().asString();
    }

    @Override
    public String fromGrant(String s, SecretFieldAccessControl secretFieldAccessControl) {
        throw new NotImplementedException();
    }

    @Override
    public String fromGrant(String s, String s1, SecretFieldAccessControl secretFieldAccessControl) {
        throw new NotImplementedException();
    }

    @Override
    public String fromEL(String s, String s1, SecretFieldAccessControl secretFieldAccessControl) {
        throw new NotImplementedException();
    }
}
