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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.Mongo;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.common.AbstractRepositoryConfiguration;
import io.gravitee.am.repository.mongodb.common.MongoFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.repository.mongodb.oauth2")
public class OAuth2RepositoryConfiguration extends AbstractRepositoryConfiguration {

    @Autowired
    @Qualifier("oauth2Mongo")
    private Mongo mongo;

    @Bean(name = "oauth2Mongo")
    public static MongoFactory mongoFactory() {
        return new MongoFactory(Scope.OAUTH2.getName());
    }

    @Bean(name = "oauth2MongoTemplate")
    public MongoOperations mongoOperations() {
        return new MongoTemplate(mongo, getDatabaseName());
    }

    @Override
    public Mongo mongo() throws Exception {
        return mongo;
    }
}