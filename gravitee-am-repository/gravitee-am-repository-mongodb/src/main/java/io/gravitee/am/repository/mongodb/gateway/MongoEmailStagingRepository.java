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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.ReadPreference;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.repository.common.EmailStagingRepository;
import io.gravitee.am.repository.mongodb.common.EmailStagingCommand;
import io.gravitee.am.repository.mongodb.common.model.EmailStagingMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.PostConstruct;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("gatewayEmailStagingRepository")
@CustomLog
public class MongoEmailStagingRepository extends AbstractGatewayMongoRepository implements EmailStagingRepository {

    private static final Long DEFAULT_MAX_READ_PREFERENCE_STALENESS = 90000L;
    private static final String COLLECTION_NAME = "dp_email_staging";

    private MongoCollection<EmailStagingMongo> emailStagingCollection;

    private EmailStagingCommand emailStagingCommand;

    @Value("${repositories.gateway.mongodb.readPreference:SECONDARY}")
    private String readPreference = "SECONDARY";

    @Value("${repositories.gateway.mongodb.readPreferenceMaxStaleness:90000}")
    private Long readPreferenceMaxStalenessMs = DEFAULT_MAX_READ_PREFERENCE_STALENESS;

    @PostConstruct
    public void init() {
        emailStagingCollection = mongoOperations.getCollection(COLLECTION_NAME, EmailStagingMongo.class);
        super.init(emailStagingCollection);
        emailStagingCommand = new EmailStagingCommand(emailStagingCollection, withReadPreferenceCollection(), getMaxTimeInMs());
    }

    @Override
    public Single<EmailStaging> create(EmailStaging emailStaging) {
        return emailStagingCommand.create(emailStaging);
    }

    @Override
    public Completable delete(String id) {
        return emailStagingCommand.delete(id);
    }

    @Override
    public Completable delete(List<String> ids) {
        return emailStagingCommand.delete(ids);
    }

    @Override
    public Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit) {
        return emailStagingCommand.findOldestByUpdateDate(reference, limit);
    }

    @Override
    public Single<EmailStaging> updateAttempts(String id, int attempts) {
        return emailStagingCommand.updateAttempts(id, attempts);
    }

    @Override
    public Flowable<Reference> listReferences() {
        return emailStagingCommand.listReferences();
    }

    private MongoCollection<EmailStagingMongo> withReadPreferenceCollection() {
        if (this.readPreference == null) {
            return this.emailStagingCollection;
        }

        ReadPreference readPreferenceValue;
        try {
            readPreferenceValue = ReadPreference.valueOf(this.readPreference);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid read preference value: {}", this.readPreference, ex);
            return this.emailStagingCollection;
        }

        // Max staleness is only compatible with NON-PRIMARY read preference
        if (readPreferenceValue != ReadPreference.primary()) {
            if (this.readPreferenceMaxStalenessMs < DEFAULT_MAX_READ_PREFERENCE_STALENESS) {
                this.readPreferenceMaxStalenessMs = DEFAULT_MAX_READ_PREFERENCE_STALENESS;
            }
            readPreferenceValue = readPreferenceValue.withMaxStalenessMS(this.readPreferenceMaxStalenessMs, TimeUnit.MILLISECONDS);
        }

        return this.emailStagingCollection.withReadPreference(readPreferenceValue);
    }
}
