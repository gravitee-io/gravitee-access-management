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
package io.gravitee.am.management.handlers.management.api.authentication.http;

import io.gravitee.gateway.api.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JettyHttpServerHeaders implements HttpHeaders {

    private final Map<String, List<String>> headers;

    public JettyHttpServerHeaders(HttpServletRequest httpServletRequest) {
        headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                List<String> headerValues = new LinkedList<>();
                httpServletRequest.getHeaders(headerName).asIterator().forEachRemaining(headerValues::add);
                headers.put(headerName, headerValues);
            }
        }
    }

    @Override
    public String get(CharSequence charSequence) {
        return headers.get(charSequence) != null ? headers.get(charSequence).get(0) : null;
    }

    @Override
    public List<String> getAll(CharSequence charSequence) {
        return headers.get(charSequence);
    }

    @Override
    public boolean contains(CharSequence charSequence) {
        return headers.containsKey(charSequence);
    }

    @Override
    public Set<String> names() {
        return headers.keySet();
    }

    @Override
    public HttpHeaders add(CharSequence charSequence, CharSequence charSequence1) {
        headers.putIfAbsent(charSequence.toString(), List.of(charSequence1.toString()));
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence charSequence, Iterable<CharSequence> iterable) {
        if (iterable != null) {
            List<String> headerValues = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .map(CharSequence::toString)
                    .collect(Collectors.toList());
            headers.putIfAbsent(charSequence.toString(), headerValues);
        }
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence charSequence, CharSequence charSequence1) {
        headers.put(charSequence.toString(), List.of(charSequence1.toString()));
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence charSequence, Iterable<CharSequence> iterable) {
        if (iterable != null) {
            List<String> headerValues = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .map(CharSequence::toString)
                    .collect(Collectors.toList());
            headers.put(charSequence.toString(), headerValues);
        }
        return this;
    }

    @Override
    public HttpHeaders remove(CharSequence charSequence) {
        headers.remove(charSequence);
        return this;
    }

    @Override
    public void clear() {
        headers.clear();
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return null;
    }
}
