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
package io.gravitee.am.reporter.elasticsearch.spring;

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.elasticsearch.client.Client;
import io.gravitee.elasticsearch.exception.ElasticsearchException;
import io.gravitee.elasticsearch.model.CountResponse;
import io.gravitee.elasticsearch.model.Health;
import io.gravitee.elasticsearch.model.SearchResponse;
import io.gravitee.elasticsearch.model.bulk.BulkResponse;
import io.gravitee.elasticsearch.version.ElasticsearchInfo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;

import java.util.List;

/**
 * A per-reporter view onto an Elasticsearch client shared by every reporter pointing at the same
 * cluster.
 * <p>
 * It exists to keep the shared client out of Spring's hands. Each reporter gets its own plugin
 * context, and the shared client's initializer is annotated {@code @PostConstruct}, so handing the
 * same instance to a second context would re-run initialization on a client that is already
 * connected and rebuild its underlying HTTP clients. This wrapper carries no lifecycle annotations,
 * so it is what each context owns and the client underneath is initialized exactly once.
 *
 * @author GraviteeSource Team
 */
class SharedElasticsearchClient implements Client {

    private final Client delegate;

    SharedElasticsearchClient(Client delegate) {
        this.delegate = delegate;
    }

    @Override
    public Single<ElasticsearchInfo> getInfo() throws ElasticsearchException {
        return delegate.getInfo();
    }

    @Override
    public Single<Health> getClusterHealth() {
        return delegate.getClusterHealth();
    }

    @Override
    public Single<List<String>> getFieldTypes(String index, String field) {
        return delegate.getFieldTypes(index, field);
    }

    @Override
    public Single<BulkResponse> bulk(Buffer payload, boolean refresh) {
        return delegate.bulk(payload, refresh);
    }

    @Override
    public Completable putTemplate(String name, String body) {
        return delegate.putTemplate(name, body);
    }

    @Override
    public Completable putIndexTemplate(String name, String body) {
        return delegate.putIndexTemplate(name, body);
    }

    @Override
    public Completable putPipeline(String name, String body) {
        return delegate.putPipeline(name, body);
    }

    @Override
    public Single<SearchResponse> search(String index, String type, String query) {
        return delegate.search(index, type, query);
    }

    @Override
    public Single<CountResponse> count(String index, String type, String query) {
        return delegate.count(index, type, query);
    }

    @Override
    public Maybe<JsonNode> getAlias(String alias) {
        return delegate.getAlias(alias);
    }

    @Override
    public Completable createIndexWithAlias(String index, String alias) {
        return delegate.createIndexWithAlias(index, alias);
    }
}
