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
package io.gravitee.am.reporter.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Raw HTTP access to the test Elasticsearch, used for arranging fixtures and for assertions that
 * deliberately bypass the reporter (mappings, templates, index names, refresh).
 *
 * @author GraviteeSource Team
 */
public final class ElasticsearchTestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;

    public ElasticsearchTestClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static ElasticsearchTestClient onTestContainer() {
        return new ElasticsearchTestClient(ElasticsearchTestContainer.endpoint());
    }

    public HttpResponse<String> get(String path) {
        return send(request(path).GET());
    }

    public HttpResponse<String> put(String path, String body) {
        return send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    public HttpResponse<String> post(String path, String body) {
        return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public HttpResponse<String> delete(String path) {
        return send(request(path).DELETE());
    }

    public JsonNode getJson(String path) {
        return parse(get(path).body());
    }

    /** Make everything written so far visible to search. */
    public void refresh(String indexPattern) {
        post("/" + indexPattern + "/_refresh", "");
    }

    /**
     * Blocks until every shard behind the pattern is allocated. A daily index created moments ago can
     * still be initialising, and a search that touches it fails the whole request with
     * {@code no_shard_available_action_exception} rather than returning partial results.
     */
    public void awaitSearchable(String indexPattern) {
        get("/_cluster/health/" + indexPattern + "?wait_for_status=yellow&timeout=30s");
    }

    /** Names of the concrete indices matching the pattern, or empty when none exist. */
    public JsonNode indices(String indexPattern) {
        return parse(get("/_cat/indices/" + indexPattern + "?format=json&h=index").body());
    }

    public long count(String indexPattern) {
        JsonNode response = getJson("/" + indexPattern + "/_count");
        return response.path("count").asLong();
    }

    /**
     * Waits for a reporter's index template to appear. The reporter applies it asynchronously, so a
     * test that writes the instant the harness returns would otherwise race it.
     */
    public void awaitTemplate(String templateName) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (get("/_index_template/" + templateName).statusCode() == 200) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for template " + templateName, e);
            }
        }
        throw new IllegalStateException("Index template " + templateName + " was never applied");
    }

    /** Indexes pre-built documents directly, for volumes that would be slow through the reporter. */
    public void bulkIndex(String index, List<String> documents) {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            payload.append("{\"index\":{\"_index\":\"").append(index).append("\",\"_id\":\"bulk-").append(i).append("\"}}\n")
                    .append(documents.get(i)).append("\n");
        }
        HttpResponse<String> response = post("/_bulk", payload.toString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Bulk index failed: " + response.body());
        }
    }

    /** Best-effort teardown: removes every index and index template matching the pattern. */
    public void cleanUp(String indexPattern) {
        delete("/" + indexPattern);
        delete("/_index_template/" + indexPattern);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch test request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Elasticsearch", e);
        }
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Unparseable Elasticsearch response: " + body, e);
        }
    }
}
