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
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.CertificateMongo;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoCertificateRepository extends AbstractManagementMongoRepository implements CertificateRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoCertificateRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private MongoCollection<CertificateMongo> certificatesCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        certificatesCollection = mongoOperations.getCollection("certificates", CertificateMongo.class);
        certificatesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
    }

    @Override
    public Single<Set<Certificate>> findByDomain(String domain) {
        return Observable.fromPublisher(certificatesCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Certificate>> findAll() {
        return Observable.fromPublisher(certificatesCollection.find()).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Certificate> findById(String certificateId) {
        return Observable.fromPublisher(certificatesCollection.find(eq(FIELD_ID, certificateId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Certificate> create(Certificate item) {
        CertificateMongo certificate = convert(item);
        certificate.setId(certificate.getId() == null ? (String) idGenerator.generate() : certificate.getId());
        return Single.fromPublisher(certificatesCollection.insertOne(certificate)).flatMap(success -> findById(certificate.getId()).toSingle());
    }

    @Override
    public Single<Certificate> update(Certificate item) {
        CertificateMongo certificate = convert(item);
        return Single.fromPublisher(certificatesCollection.replaceOne(eq(FIELD_ID, certificate.getId()), certificate)).flatMap(updateResult -> findById(certificate.getId()).toSingle());
    }

    @Override
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(certificatesCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.CERTIFICATE);
    }
    private Certificate convert(CertificateMongo certificateMongo) {
        if (certificateMongo == null) {
            return null;
        }

        Certificate certificate = new Certificate();
        certificate.setId(certificateMongo.getId());
        certificate.setName(certificateMongo.getName());
        certificate.setType(certificateMongo.getType());
        certificate.setConfiguration(certificateMongo.getConfiguration());
        certificate.setDomain(certificateMongo.getDomain());
        certificate.setCreatedAt(certificateMongo.getCreatedAt());
        certificate.setUpdatedAt(certificateMongo.getUpdatedAt());
        return certificate;
    }

    private CertificateMongo convert(Certificate certificate) {
        if (certificate == null) {
            return null;
        }

        CertificateMongo certificateMongo = new CertificateMongo();
        certificateMongo.setId(certificate.getId());
        certificateMongo.setName(certificate.getName());
        certificateMongo.setType(certificate.getType());
        certificateMongo.setConfiguration(certificate.getConfiguration());
        certificateMongo.setDomain(certificate.getDomain());
        certificateMongo.setCreatedAt(certificate.getCreatedAt());
        certificateMongo.setUpdatedAt(certificate.getUpdatedAt());
        return certificateMongo;
    }

    private class IndexSubscriber extends DefaultSubscriber<String> {
        @Override
        public void onNext(String value) {
            logger.debug("Created an index named : " + value);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Error occurs during indexing", throwable);
        }

        @Override
        public void onComplete() {
            logger.debug("Index creation complete");
        }
    }

}
