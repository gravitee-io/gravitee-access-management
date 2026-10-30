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
import type { CorsSettings, Domain, DomainWebProtectionSettings, WebProtectionSettings } from '../../lib/api/domains';
import { type DurationDraft, draftToSeconds, exactDuration } from '../../lib/forms/duration';
import {
    CSP_DIRECTIVE_NAME_PATTERN,
    CSP_DIRECTIVE_NAMES,
    CSP_DIRECTIVE_VALUE_PATTERN,
    type CspDirectiveRow,
    allowsValue,
    parseCspDirectives,
    requiresValue,
    serializeCspDirectives,
} from './csp-directives';

type Mutable<T> = { -readonly [K in keyof T]: T[K] };
export type Cors = Mutable<Omit<CorsSettings, 'maxAge'>>;
export type Csp = Mutable<Omit<NonNullable<WebProtectionSettings['csp']>, 'directives'>>;
export type Header = Mutable<NonNullable<WebProtectionSettings['xframe']>>;

export interface FormState {
    readonly cors: Cors;
    readonly maxAge: DurationDraft;
    readonly csp: Csp;
    readonly directives: readonly CspDirectiveRow[];
    readonly xframe: Header;
    readonly xss: Header;
}

export interface Inheritable {
    readonly inherited?: boolean;
    readonly enabled?: boolean;
}

const NAME_RE = new RegExp(`^${CSP_DIRECTIVE_NAME_PATTERN}$`);
const VALUE_RE = new RegExp(`^${CSP_DIRECTIVE_VALUE_PATTERN}$`);

const DEFAULT_CORS: Cors = {
    inherited: true,
    allowedOrigins: [],
    allowedMethods: [],
    allowedHeaders: [],
    allowCredentials: false,
    enabled: false,
};
const DEFAULT_CSP = { inherited: true, enabled: false, reportOnly: false, scriptInlineNonce: true, directives: [] };
const DEFAULT_XFRAME: Header = { inherited: true, enabled: false, action: 'DENY' };
const DEFAULT_XSS: Header = { inherited: true, enabled: false, action: '1; mode=block' };

/** A section saved before `inherited` existed inherits unless it was enabled. */
function normalize<T extends Inheritable>(settings: T): T {
    return { ...settings, inherited: settings.inherited ?? !settings.enabled };
}

export function fromDomain(domain: Domain): FormState {
    const { maxAge, ...cors }: CorsSettings = domain.corsSettings ?? DEFAULT_CORS;
    const { directives = [], ...csp } = domain.webProtectionSettings?.csp ?? DEFAULT_CSP;
    return {
        cors: normalize({
            ...cors,
            allowedOrigins: cors.allowedOrigins ?? [],
            allowedHeaders: cors.allowedHeaders ?? [],
        }),
        maxAge: maxAge ? exactDuration(maxAge) : { time: '', unit: 'seconds' },
        csp: normalize(csp),
        directives: parseCspDirectives([...directives]),
        xframe: normalize(domain.webProtectionSettings?.xframe ?? DEFAULT_XFRAME),
        xss: normalize(domain.webProtectionSettings?.xss ?? DEFAULT_XSS),
    };
}

export function toSettings(form: FormState): DomainWebProtectionSettings {
    return {
        // An empty max age is stored as 0. A missing key would keep the 86400 default.
        corsSettings: { ...form.cors, maxAge: draftToSeconds(form.maxAge) },
        webProtectionSettings: {
            csp: { ...form.csp, directives: serializeCspDirectives([...form.directives]) },
            xframe: form.xframe,
            xss: form.xss,
        },
    };
}

export function nameError(row: CspDirectiveRow, duplicate: boolean): string | undefined {
    if (!row.name) {
        return 'Enter a directive name';
    }
    if (!NAME_RE.test(row.name)) {
        return 'Use letters, digits and hyphens only';
    }
    return duplicate ? 'This directive is already configured' : undefined;
}

export function valueError(row: CspDirectiveRow): string | undefined {
    if (!allowsValue(row.name)) {
        return undefined;
    }
    if (requiresValue(row.name) && !row.value) {
        return 'Enter a value';
    }
    return VALUE_RE.test(row.value) ? undefined : 'Use one directive per row, without ";" or control characters';
}

/** The known names that contain the typed text. */
export function directiveNames(name: string): string[] {
    const typed = name.trim().toLowerCase();
    return typed ? CSP_DIRECTIVE_NAMES.filter(candidate => candidate.includes(typed)) : CSP_DIRECTIVE_NAMES;
}
