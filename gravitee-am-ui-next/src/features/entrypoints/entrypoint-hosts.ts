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
import type { Entrypoint } from '../../lib/api/domains';

export interface VirtualHostRow {
    readonly host: string;
    readonly path: string;
    readonly overrideEntrypoint?: boolean;
}

const DOMAIN_LABEL = '(?!-)[A-Za-z0-9\\-_]{1,63}(?<![\\-_])';
const PORT = '(?::[0-9]{1,5})';

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\/-]/g, '\\$&');
}

/** The Angular `buildHostPattern`: DNS labels and an optional port, under one of the environment's domain restrictions when it has any. */
export function hostPattern(domainRestrictions: readonly string[]): RegExp {
    if (domainRestrictions.length === 0) {
        return new RegExp(`^(?:${DOMAIN_LABEL})(?:\\.${DOMAIN_LABEL})*${PORT}?$`);
    }
    return new RegExp(`^(?:${DOMAIN_LABEL}\\.)*(?:${domainRestrictions.map(escapeRegExp).join('|')})${PORT}?$`);
}

/** The Angular `getHostOptions`: the typed host completed with each domain restriction, the port kept. */
export function hostOptions(host: string, domainRestrictions: readonly string[]): readonly string[] {
    if (host === '') {
        return domainRestrictions;
    }
    const [name, port] = host.split(':');
    const suffix = port === undefined ? '' : `:${port}`;
    let prefix = name;
    for (const restriction of domainRestrictions) {
        prefix = prefix.replace(new RegExp(`\\.?${restriction}$`, 'i'), '');
    }
    if (domainRestrictions.includes(prefix)) {
        return domainRestrictions;
    }
    return domainRestrictions.map(restriction => (prefix === '' ? restriction : `${prefix}.${restriction}`) + suffix);
}

/** The Angular `DomainService.getEntrypoint`: the only entrypoint, else the first one that is not the default. */
export function domainEntrypoint(entrypoints: readonly Entrypoint[]): Entrypoint | undefined {
    return entrypoints.length === 1 ? entrypoints[0] : (entrypoints.find(entrypoint => !entrypoint.defaultEntrypoint) ?? entrypoints[0]);
}

/** The Angular `EntrypointService.resolveBaseUrl`: the URL applications use to reach the domain. */
export function accessUrl(entrypointUrl: string, path: string, vhostMode: boolean, vhosts: readonly VirtualHostRow[]): string {
    let base = entrypointUrl;
    let resolvedPath = path;
    const vhost = vhostMode ? vhosts.find(candidate => candidate.overrideEntrypoint) : undefined;
    if (vhost) {
        base = (/^(https?:\/\/)/.exec(entrypointUrl)?.[1] ?? '') + vhost.host;
        resolvedPath = vhost.path;
    }
    return resolvedPath === '/' ? base : base + resolvedPath;
}
