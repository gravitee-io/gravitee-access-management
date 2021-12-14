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

package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.management.service.BotDetectionServiceProxy;
import io.gravitee.am.management.service.exception.BotDetectionPluginSchemaNotFoundException;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.service.BotDetectionService;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.model.NewBotDetection;
import io.gravitee.am.service.model.UpdateBotDetection;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class BotDetectionServiceProxyImpl extends AbstractSensitiveProxy implements BotDetectionServiceProxy {

    @Autowired
    private BotDetectionService botDetectionService;

    @Autowired
    private BotDetectionPluginService botDetectionPluginService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Maybe<BotDetection> findById(String id) {
        return botDetectionService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Flowable<BotDetection> findByDomain(String domain) {
        return botDetectionService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<BotDetection> create(String domain, NewBotDetection botDetection, User principal) {
        return botDetectionService.create(domain, botDetection, principal).flatMap(this::filterSensitiveData);
    }

    @Override
    public Single<BotDetection> update(String domain, String id, UpdateBotDetection updateBotDetection, User principal) {
        return botDetectionService.findById(id)
                .switchIfEmpty(Single.error(new BotDetectionNotFoundException(id)))
                .flatMap(oldBotDetection -> updateSensitiveData(updateBotDetection, oldBotDetection))
                .flatMap(botDetectionToUpdate -> botDetectionService.update(domain, id, botDetectionToUpdate, principal))
                .flatMap(this::filterSensitiveData);
    }

    @Override
    public Completable delete(String domain, String botDetectionId, User principal) {
        return botDetectionService.delete(domain, botDetectionId, principal);
    }

    private Single<BotDetection> filterSensitiveData(BotDetection botDetection) {
        return botDetectionPluginService.getSchema(botDetection.getType())
                .switchIfEmpty(Single.error(new BotDetectionPluginSchemaNotFoundException(botDetection.getType())))
                .map(schema -> {
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(botDetection.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, botDetection::setConfiguration);
                    return botDetection;
                });
    }

    private Single<UpdateBotDetection> updateSensitiveData(UpdateBotDetection updateBotDetection, BotDetection oldBotDetection) {
        return botDetectionPluginService.getSchema(oldBotDetection.getType())
                .switchIfEmpty(Single.error(new BotDetectionPluginSchemaNotFoundException(oldBotDetection.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateBotDetection.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldBotDetection.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateBotDetection::setConfiguration);
                    return updateBotDetection;
                });
    }
}
