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
package io.gravitee.am.repository.mongodb.oauth2.code;

import com.mongodb.Mongo;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.tests.MongodForTestsFactory;
import io.gravitee.am.repository.mongodb.common.AbstractRepositoryConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ComponentScan("io.gravitee.am.repository.mongodb.oauth2.code")
@EnableMongoRepositories
public class MongoAuthorizationCodeRepositoryTestContextConfiguration extends AbstractRepositoryConfiguration {

    @Bean
    public MongodForTestsFactory factory() throws Exception {
        return MongodForTestsFactory.with(Version.Main.DEVELOPMENT);
    }

    @Bean(name = "oauth2Mongo")
    public Mongo mongo() throws Exception {
        return factory().newMongo();
    }

    @Bean(name = "oauth2MongoTemplate")
    public MongoTemplate mongoTemplate(Mongo mongo) {
        return new MongoTemplate(mongo, "gravitee-oauth2");
    }
}
