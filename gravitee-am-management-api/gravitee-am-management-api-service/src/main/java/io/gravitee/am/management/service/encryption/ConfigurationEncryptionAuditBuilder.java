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
package io.gravitee.am.management.service.encryption;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult.Status;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.reporter.builder.management.ManagementAuditBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The re-encryption covers the whole platform, so it is recorded against the default organization, with the
 * key as target. The results hold plugin types, statuses and counts, never a configuration.
 *
 * @author GraviteeSource Team
 */
public class ConfigurationEncryptionAuditBuilder extends ManagementAuditBuilder<ConfigurationEncryptionAuditBuilder> {

    public ConfigurationEncryptionAuditBuilder() {
        super();
        type(EventType.PLUGIN_CONFIGURATIONS_REENCRYPTED);
    }

    /**
     * Reported as a failure when a plugin type failed.
     */
    public ConfigurationEncryptionAuditBuilder reencryption(String keyId, List<ConfigurationEncryptionResult> results) {
        reference(Reference.organization(Organization.DEFAULT));
        setTarget(keyId, EntityType.ENCRYPTION_KEY, null, keyId, ReferenceType.ORGANIZATION, Organization.DEFAULT);
        if (results != null) {
            // the audit value must be a JSON object
            setNewValue(new Reencryption(keyId, results));
            String failed = results.stream()
                    .filter(result -> result.status() == Status.FAILED)
                    .map(ConfigurationEncryptionResult::plugin)
                    .collect(Collectors.joining(", "));
            if (!failed.isEmpty()) {
                throwable(new IllegalStateException("Unable to encrypt the configurations of " + failed + " with key [" + keyId + "]"));
            }
        }
        return this;
    }

    record Reencryption(String keyId, List<ConfigurationEncryptionResult> results) {
    }
}
