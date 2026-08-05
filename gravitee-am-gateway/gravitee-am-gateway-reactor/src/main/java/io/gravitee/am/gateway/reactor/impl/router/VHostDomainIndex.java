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
package io.gravitee.am.gateway.reactor.impl.router;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Host-indexed dispatcher for vhost-mode domains.
 *
 * The previous strategy ({@link VHostRouter} mounted individually via
 * {@code this.router.route("/*").subRouter(...)} for every vhost, all on the
 * SAME shared root router) makes Vert.x-web's route-chain traversal walk
 * every previously-mounted vhost, in registration order, for every request -
 * because all vhost routes share the same wildcard path pattern, so nothing
 * about the route itself lets Vert.x skip non-matching candidates. That
 * traversal is recursive (one Java stack frame per candidate route tested
 * via RoutingContext.next()), so request latency AND stack depth both grow
 * linearly with the number of mounted domains - guaranteed to
 * StackOverflowError past some domain count (~3000 in practice).
 *
 * This class replaces per-domain route mounting with a single Vert.x route
 * backed by a hashmap keyed on the Host header. Dispatch becomes an O(1)
 * lookup plus a linear scan only across the candidates registered for THAT
 * host (in practice 1, occasionally a handful if a host is deliberately
 * reused with different paths) - no recursion, no dependency on total
 * domain count.
 *
 * Only one Vert.x route needs to ever be mounted on the shared router for
 * ALL vhost domains combined; call {@link #dispatch(RoutingContext)} from
 * that route's handler.
 */
public class VHostDomainIndex {

    private record Entry(String domainId, Pattern pathPattern, String contextPath, Router delegate) {
    }

    // hostname (lowercase) -> candidates for that host, most specific path first
    private final Map<String, CopyOnWriteArrayList<Entry>> byHost = new ConcurrentHashMap<>();

    // domainId -> hosts it registered, so unmount() doesn't need a full scan
    private final Map<String, List<String>> hostsByDomainId = new ConcurrentHashMap<>();

    public void mount(Domain domain, VirtualHost vhost, Router delegate) {
        String hostKey = vhost.getHost().toLowerCase(Locale.ROOT);
        Pattern pathPattern = Pattern.compile(Pattern.quote(vhost.getPath()) + ".*");
        String contextPath = "/".equals(vhost.getPath()) ? "" : vhost.getPath();
        Entry entry = new Entry(domain.getId(), pathPattern, contextPath, delegate);

        CopyOnWriteArrayList<Entry> entries = byHost.computeIfAbsent(hostKey, k -> new CopyOnWriteArrayList<>());
        entries.add(entry);
        // longer (more specific) paths win over a catch-all "/" registered on the same host
        entries.sort(Comparator.comparingInt((Entry e) -> e.pathPattern().pattern().length()).reversed());

        hostsByDomainId.computeIfAbsent(domain.getId(), k -> new CopyOnWriteArrayList<>()).add(hostKey);
    }

    public void unmount(String domainId) {
        List<String> hosts = hostsByDomainId.remove(domainId);
        if (hosts == null) {
            return;
        }
        for (String host : hosts) {
            byHost.computeIfPresent(host, (h, entries) -> {
                entries.removeIf(e -> e.domainId().equals(domainId));
                return entries.isEmpty() ? null : entries;
            });
        }
    }

    public void dispatch(RoutingContext context) {
        var authority = context.request().authority();
        String host = authority == null ? null : authority.toString();

        if (host != null) {
            List<Entry> entries = byHost.get(host.toLowerCase(Locale.ROOT));
            if (entries != null) {
                String path = context.request().path();
                for (Entry entry : entries) {
                    if (entry.pathPattern().matcher(path).matches()) {
                        context.put(CONTEXT_PATH, entry.contextPath());
                        entry.delegate().handleContext(context);
                        return;
                    }
                }
            }
        }

        // no vhost matched this host/path - fall through (e.g. to the final 404 handler)
        context.next();
    }
}
