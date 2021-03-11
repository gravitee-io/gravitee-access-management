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
package io.gravitee.am.management.service.impl.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.management.service.exception.NotifierPluginNotFoundException;
import io.gravitee.am.management.service.exception.NotifierPluginSchemaNotFoundException;
import io.gravitee.am.plugins.notifier.core.NotifierPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.NotifierPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class NotifierPluginService {

    public static String EXPAND_ICON = "icon";
    public static final String DEFAULT_NOTIFIER_MESSAGE = "An alert '${alert.name}' has been fired.\\n"
            + "\\n"
            + "Date: ${notification.timestamp?number?number_to_datetime}\\n"
            + "Domain: ${domain.name} (${domain.id})\\n"
            + "Application: ${application.name} (${application.id})\\n"
            + "User: ${notification.properties['user']}\\n"
            + "Alert: ${alert.description}\\n"
            + "Technical message: ${notification.message}";

    private final NotifierPluginManager notifierPluginManager;
    private final ObjectMapper objectMapper;

    public NotifierPluginService(@Lazy NotifierPluginManager notifierPluginManager, ObjectMapper objectMapper) {
        this.notifierPluginManager = notifierPluginManager;
        this.objectMapper = objectMapper;
    }

    public Flowable<NotifierPlugin> findAll(String... expand) {
        return Flowable.fromIterable(notifierPluginManager.findAll())
                .flatMapSingle(plugin -> convert(plugin, expand))
                .onErrorResumeNext(throwable -> {
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to get notifier plugins", throwable));
                });
    }

    public Single<NotifierPlugin> findById(String notifierId) {
        return Maybe.fromCallable(() -> notifierPluginManager.findById(notifierId))
                .flatMap(plugin -> convert(plugin).toMaybe())
                .onErrorResumeNext(throwable -> {
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to get notifier plugin " + notifierId, throwable));
                })
                .switchIfEmpty(Single.defer(() -> Single.error(new NotifierPluginNotFoundException(notifierId))));
    }

    public Single<String> getSchema(String notifierId) {

        return Maybe.fromCallable(() -> notifierPluginManager.getSchema(notifierId))
                .map(objectMapper::readTree)
                .doOnSuccess(jsonSchema -> {
                    final JsonNode propertiesNode = jsonSchema.get("properties");
                    JsonNode messageNode = null;
                    if (propertiesNode instanceof ObjectNode) {
                        if (propertiesNode.has("message")) {
                            messageNode = propertiesNode.get("message");
                        } else if (propertiesNode.has("body")) {
                            messageNode = propertiesNode.get("body");
                        }
                    }

                    if (messageNode instanceof ObjectNode) {
                        ((ObjectNode) messageNode).put("default", DEFAULT_NOTIFIER_MESSAGE);
                    }
                })
                .map(JsonNode::toString)
                .onErrorResumeNext(throwable -> {
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to get schema for notifier plugin " + notifierId, throwable));
                })
                .switchIfEmpty(Single.defer(() -> Single.error(new NotifierPluginSchemaNotFoundException(notifierId))));
    }

    public Maybe<String> getIcon(String notifierId) {

        return Maybe.fromCallable(() -> notifierPluginManager.getIcon(notifierId))
                .onErrorResumeNext(throwable -> {
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to get incon for notifier plugin " + notifierId, throwable));
                });
    }

    public Maybe<String> getDocumentation(String notifierId) {

        return Maybe.fromCallable(() -> notifierPluginManager.getDocumentation(notifierId))
                .onErrorResumeNext(throwable -> {
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to get documentation for notifier plugin " + notifierId, throwable));
                });
    }

    private Single<NotifierPlugin> convert(Plugin plugin, String... expand) {
        NotifierPlugin notifierPlugin = new NotifierPlugin();
        notifierPlugin.setId(plugin.manifest().id());
        notifierPlugin.setName(plugin.manifest().name());
        notifierPlugin.setDescription(plugin.manifest().description());
        notifierPlugin.setVersion(plugin.manifest().version());


        if (expand != null) {
            final List<String> expandList = Arrays.asList(expand);
            if (expandList.contains(EXPAND_ICON)) {
                return this.getIcon(notifierPlugin.getId())
                        .doOnSuccess(notifierPlugin::setIcon)
                        .ignoreElement().andThen(Single.just(notifierPlugin));
            }
        }

        return Single.just(notifierPlugin);
    }
}
