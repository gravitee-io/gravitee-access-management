/*
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
import type { Domain, Entrypoint } from '../../lib/api/domains';

/**
 * The callback URL an external provider must know: the domain entrypoint, or its overriding virtual host, plus the
 * domain path. The Angular `DomainService.getEntrypoint` and `EntrypointService.resolveBaseUrl`.
 */
export function redirectUri(entrypoints: readonly Entrypoint[], domain: Domain): string | undefined {
    const entrypoint = entrypoints.length === 1 ? entrypoints[0] : (entrypoints.find(e => !e.defaultEntrypoint) ?? entrypoints[0]);
    if (!entrypoint) {
        return undefined;
    }
    let baseUrl = entrypoint.url;
    let path = domain.path ?? '/';
    const vhost = domain.vhostMode ? domain.vhosts?.find(candidate => candidate.overrideEntrypoint) : undefined;
    if (vhost) {
        const scheme = /^(https?:\/\/)/.exec(entrypoint.url)?.[1] ?? '';
        baseUrl = scheme + vhost.host;
        path = vhost.path;
    }
    return `${path === '/' ? baseUrl : baseUrl + path}/login/callback`;
}
