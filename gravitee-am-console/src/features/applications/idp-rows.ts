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
export interface ProviderRow {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly selected: boolean;
    readonly selectionRule: string;
}

interface ProviderLink {
    readonly identity?: string;
    readonly priority?: number;
    readonly selectionRule?: string;
}

/** The selected providers by priority, then the others in the domain order. */
export function toProviderRows(
    providers: readonly { readonly id: string; readonly name: string; readonly type: string }[],
    links: readonly ProviderLink[],
): ProviderRow[] {
    return providers
        .map((provider, index) => {
            const link = links.find(candidate => candidate.identity === provider.id);
            return {
                row: {
                    id: provider.id,
                    name: provider.name,
                    type: provider.type,
                    selected: !!link,
                    selectionRule: link?.selectionRule ?? '',
                },
                priority: link ? (link.priority ?? 0) : providers.length,
                index,
            };
        })
        .sort((a, b) => a.priority - b.priority || a.index - b.index)
        .map(({ row }) => row);
}

/** The selected providers of each list, with priorities that restart at 0 in each list. */
export function toIdentityProviderLinks(...lists: readonly (readonly ProviderRow[])[]) {
    return lists.flatMap(rows =>
        rows.filter(row => row.selected).map((row, priority) => ({ identity: row.id, selectionRule: row.selectionRule, priority })),
    );
}

export function moveRow<T>(rows: readonly T[], from: number, to: number): T[] {
    const next = [...rows];
    const [row] = next.splice(from, 1);
    if (row !== undefined) {
        next.splice(to, 0, row);
    }
    return next;
}

export function defaultSelectionRule(type: string): string {
    if (type === 'google-am-idp') {
        return "{#request.params['username'] matches '.+@gmail.com$'}";
    }
    if (type === 'azure-ad-am-idp') {
        return "{#request.params['username'] matches '.+@microsoft.com$'}";
    }
    return "{#request.params['username'] matches '.+'}";
}
