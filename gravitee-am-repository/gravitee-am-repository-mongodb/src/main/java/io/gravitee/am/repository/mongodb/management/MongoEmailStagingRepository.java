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
package io.gravitee.am.repository.mongodb.management;

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
import org.springframework.stereotype.Component;

import java.util.List;

@Component("managementEmailStagingRepository")
public class MongoEmailStagingRepository extends AbstractManagementMongoRepository implements EmailStagingRepository {

    private static final String COLLECTION_NAME = "cp_email_staging";

    private EmailStagingCommand emailStagingCommand;

    @PostConstruct
    public void init() {
        MongoCollection<EmailStagingMongo> emailStagingCollection = mongoOperations.getCollection(COLLECTION_NAME, EmailStagingMongo.class);
        super.init(emailStagingCollection);
        emailStagingCommand = new EmailStagingCommand(emailStagingCollection, emailStagingCollection, cursorMaxTimeInMs);
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
}
