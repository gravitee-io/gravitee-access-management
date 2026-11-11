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
import type { SpelGrammar, SpelGrammarNode } from '../../lib/api/management-api';

export interface SpelCompletion {
    readonly label: string;
    readonly insertText: string;
    readonly detail: string;
}

interface SpelCompletions {
    /** The text typed before the cursor that the completion replaces. */
    readonly prefix: string;
    readonly items: SpelCompletion[];
}

const PROPERTY_PATH = /#((?:[\w-]+\.)*)([\w-]*)$/;
const BRACKET_ACCESS = /#((?:[\w-]+\.)*[\w-]+)\[['"]?([\w-]*)$/;

function isNode(value: unknown): value is SpelGrammarNode {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nodeAt(grammar: SpelGrammar, path: readonly string[]): SpelGrammarNode | undefined {
    let node: unknown = grammar;
    for (const property of path) {
        if (!isNode(node) || property === '_enums') {
            return undefined;
        }
        node = node[property];
    }
    return isNode(node) ? node : undefined;
}

/** The completions for the expression that ends at the cursor, such as `{#request.pa` or `{#request.headers['Au`. */
export function spelCompletions(grammar: SpelGrammar, textBeforeCursor: string): SpelCompletions {
    const bracket = BRACKET_ACCESS.exec(textBeforeCursor);
    if (bracket) {
        const [, path = '', prefix = ''] = bracket;
        const type = nodeAt(grammar, path.split('.'))?._type;
        const values = type ? (grammar._enums?.[type] ?? []) : [];
        return {
            prefix,
            items: values.filter(value => value.startsWith(prefix)).map(value => ({ label: value, insertText: value, detail: type ?? '' })),
        };
    }
    const property = PROPERTY_PATH.exec(textBeforeCursor);
    if (!property) {
        return { prefix: '', items: [] };
    }
    const [, path = '', prefix = ''] = property;
    const node = nodeAt(grammar, path.split('.').filter(Boolean));
    if (!node || node._type) {
        return { prefix, items: [] };
    }
    const items = Object.entries(node)
        .filter(([key, child]) => key !== '_type' && key !== '_enums' && isNode(child) && key.startsWith(prefix))
        .map(([key, child]) => ({ label: key, insertText: key, detail: isNode(child) ? (child._type ?? 'Object') : 'Object' }));
    return { prefix, items };
}
