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
package io.gravitee.am.repository.gateway.test.config;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.MongodbProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"io.gravitee.am.repository.mongodb.gateway", "io.gravitee.am.repository.mongodb.common", "io.gravitee.am.repository.mongodb.provider"})
public class GatewayTestConfigurationLoader {
    @Bean
    public MongodbProvider embeddedClient() {
        return new MongodbProvider("test-am-gateway");
    }

    @Bean(name = "gatewayMongoTemplate")
    public MongoDatabase mongoOperations() {
        return embeddedClient().mongoDatabase();
    }
}
