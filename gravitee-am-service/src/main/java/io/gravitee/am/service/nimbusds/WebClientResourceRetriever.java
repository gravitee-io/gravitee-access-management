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
package io.gravitee.am.service.nimbusds;

import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URL;

@RequiredArgsConstructor
public class WebClientResourceRetriever implements ResourceRetriever {
    private final WebClient webClient;

    @Override
    public Resource retrieveResource(URL url) throws IOException {
        try {
            final HttpResponse<Buffer> response = webClient.getAbs(url.toExternalForm()).rxSend().blockingGet();
            if (response.statusCode() != 200) {
                throw new IOException("HTTP status " + response.statusCode() + " retrieving JWK set from " + url + ". Body: " + response.bodyAsString());
            }
            return new Resource(response.bodyAsString(), response.getHeader(HttpHeaders.CONTENT_TYPE));
        } catch (Exception e) {
            throw new IOException("Failed to retrieve JWK set from " + url, e);
        }
    }

}
