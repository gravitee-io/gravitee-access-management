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
export type MetadataEntry = {
    readonly id: string;
    readonly name: string;
    readonly value: string;
    /** The stored value when it is not text, sent back as it is until the user edits it. */
    readonly raw?: unknown;
};

function display(value: unknown): string {
    return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '');
}

export function toEntries(metadata: Readonly<Record<string, unknown>> | undefined): MetadataEntry[] {
    return Object.entries(metadata ?? {}).map(([name, value]) =>
        typeof value === 'string' ? { id: name, name, value } : { id: name, name, value: display(value), raw: value },
    );
}

/** The whole map: the API replaces the stored metadata with it. */
export function toMetadata(entries: readonly MetadataEntry[]): Record<string, unknown> {
    return Object.fromEntries(entries.map(entry => [entry.name.trim(), entry.raw !== undefined ? entry.raw : entry.value]));
}

/** The name error of each entry, by entry id. */
export function metadataErrors(entries: readonly MetadataEntry[]): Map<string, string> {
    const errors = new Map<string, string>();
    entries.forEach((entry, index) => {
        const name = entry.name.trim();
        if (!name) {
            errors.set(entry.id, 'A name is required.');
        } else if (entries.findIndex(other => other.name.trim() === name) !== index) {
            errors.set(entry.id, `Metadata "${name}" already exists.`);
        }
    });
    return errors;
}
