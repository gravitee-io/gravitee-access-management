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
import { Fragment, type ReactNode } from 'react';

const SAFE_URL = /^https?:\/\//i;
const MARKUP = /<[a-z/][^>]*>/i;

function toNodes(nodes: NodeListOf<ChildNode>, prefix: string): ReactNode[] {
    return Array.from(nodes).map((node, index) => toNode(node, `${prefix}${index}`));
}

function toNode(node: ChildNode, key: string): ReactNode {
    if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent;
    }
    if (!(node instanceof Element)) {
        return null;
    }
    const children = toNodes(node.childNodes, `${key}.`);
    switch (node.tagName.toLowerCase()) {
        case 'b':
        case 'strong':
            return <strong key={key}>{children}</strong>;
        case 'i':
        case 'em':
            return <em key={key}>{children}</em>;
        case 'code':
            return (
                <code key={key} className="font-mono text-xs">
                    {children}
                </code>
            );
        case 'br':
            return <br key={key} />;
        case 'script':
        case 'style':
            return null;
        case 'a': {
            const href = node.getAttribute('href');
            return href && SAFE_URL.test(href) ? (
                <a key={key} href={href} target="_blank" rel="noreferrer" className="underline">
                    {children}
                </a>
            ) : (
                <Fragment key={key}>{children}</Fragment>
            );
        }
        default:
            return <Fragment key={key}>{children}</Fragment>;
    }
}

/**
 * The HTML of a plugin description as React elements. Only bold, italic, code,
 * line breaks and http(s) links keep their markup. A script or style is dropped. Any other tag gives its text.
 */
export function richText(text: string): ReactNode {
    if (!MARKUP.test(text)) {
        return text;
    }
    return toNodes(new DOMParser().parseFromString(text, 'text/html').body.childNodes, '');
}
