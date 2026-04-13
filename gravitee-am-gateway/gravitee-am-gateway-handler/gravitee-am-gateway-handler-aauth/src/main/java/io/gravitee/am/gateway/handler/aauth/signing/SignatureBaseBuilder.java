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
package io.gravitee.am.gateway.handler.aauth.signing;

import io.vertx.rxjava3.core.http.HttpServerRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the RFC 9421 signature base string from the HTTP request and the parsed Signature-Input.
 *
 * The signature base is a string of lines, one per covered component, plus a final
 * {@code @signature-params} line. Each line is: {@code "<component-id>": <value>}.
 */
public final class SignatureBaseBuilder {

    private static final Set<String> REQUIRED_COMPONENTS = Set.of("@method", "@authority", "@path", "signature-key");

    private SignatureBaseBuilder() {
    }

    /**
     * Build the signature base bytes from the request and parsed Signature-Input.
     *
     * @param request     the HTTP request
     * @param headers     all request headers as a map (for regular header components)
     * @param inputInfo   parsed Signature-Input info
     * @return the signature base as bytes (UTF-8)
     * @throws SignatureVerificationException if required components are missing
     */
    public static byte[] build(HttpServerRequest request, Map<String, String> headers,
                                SignatureInputInfo inputInfo) throws SignatureVerificationException {
        List<String> components = inputInfo.coveredComponents();

        // Validate required components
        validateRequiredComponents(components);

        StringBuilder base = new StringBuilder();

        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String value = resolveComponent(component, request, headers);
            base.append("\"").append(component).append("\": ").append(value);
            base.append("\n");
        }

        // Final line: @signature-params (the raw Signature-Input value for this label)
        base.append("\"@signature-params\": ").append(inputInfo.rawValue());

        return base.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void validateRequiredComponents(List<String> components) throws SignatureVerificationException {
        List<String> missing = REQUIRED_COMPONENTS.stream()
                .filter(r -> !components.contains(r))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new SignatureVerificationException("invalid_input",
                    Map.of("required_input", String.join(", ", REQUIRED_COMPONENTS)));
        }
    }

    private static String resolveComponent(String component, HttpServerRequest request,
                                            Map<String, String> headers) throws SignatureVerificationException {
        return switch (component) {
            case "@method" -> request.method().name();
            case "@authority" -> {
                // Per RFC 9421, @authority is the Host header value for HTTP/1.1
                String host = request.getHeader("Host");
                if (host == null && request.authority() != null) {
                    host = request.authority().toString();
                }
                yield host;
            }
            case "@path" -> request.path();
            default -> {
                // Regular header field — look up by lowercase name
                String value = headers.get(component.toLowerCase());
                if (value == null) {
                    throw new SignatureVerificationException("invalid_input",
                            Map.of("required_input", component));
                }
                yield value;
            }
        };
    }
}
