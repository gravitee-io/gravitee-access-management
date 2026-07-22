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
package io.gravitee.am.service;

import io.gravitee.am.model.Reference;
import io.reactivex.rxjava3.core.Completable;

/**
 * Single enforcement point for runtime license gating of plugin usage.
 * <p>
 * A plugin whose manifest declares a license feature may only be written (create/update)
 * when the license of the owning organization grants that feature. Reads and deletes are
 * never gated. Plugins without a feature (OSS plugins) are always allowed.
 * <p>
 * The gate only restricts anything when {@code cloud.enabled} is set.
 * <p>
 * The plugin type values are the raw {@code type} entries of the plugins' {@code plugin.properties}
 * (the keys of {@link io.gravitee.plugin.core.api.PluginRegistry}).
 *
 * @author GraviteeSource Team
 */
public interface PluginLicenseGate {

    String TYPE_IDENTITY_PROVIDER = "identity_provider";
    String TYPE_CERTIFICATE = "certificate";
    String TYPE_REPORTER = "am-reporter";
    String TYPE_FACTOR = "factor";
    String TYPE_RESOURCE = "resource";
    String TYPE_EXTENSION_GRANT = "extension_grant";
    String TYPE_BOT_DETECTION = "bot_detection";
    String TYPE_DEVICE_IDENTIFIER = "device_identifier";
    String TYPE_AUTHDEVICE_NOTIFIER = "authdevice-notifier";
    String TYPE_POLICY = "policy";
    String TYPE_AUTHORIZATION_ENGINE = "authorization-engine";
    String TYPE_NOTIFIER = "notifier";
    String TYPE_PROTOCOL = "protocol";
    String TYPE_AUTHENTICATOR = "authenticator";

    /**
     * Checks that an instance of the given plugin may be created or updated under the given reference.
     *
     * @return a {@link Completable} completing when allowed, erroring with
     * {@link io.gravitee.am.service.exception.LicenseFeatureRequiredException} otherwise
     */
    Completable check(Reference reference, String pluginType, String pluginId);

    /**
     * Checks that an instance of the given plugin may be created or updated under the given reference,
     * evaluated against the <em>persisted</em> organization license instead of the in-memory license registry.
     * Use it from paths that may run before {@code OrganizationLicenseManager} has populated the registry.
     *
     * @return a {@link Completable} completing when allowed, erroring with
     * {@link io.gravitee.am.service.exception.LicenseFeatureRequiredException} otherwise
     */
    Completable checkPersisted(Reference reference, String pluginType, String pluginId);
}
