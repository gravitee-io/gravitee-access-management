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
import { Button, toast } from '@gravitee/graphene-core';
import { CopyIcon, InfoIcon } from '@gravitee/graphene-core/icons';
import type { ReactNode } from 'react';
import { copyText } from '../../lib/clipboard';

export function CodeBlock({ code, copyable = true }: { readonly code: string; readonly copyable?: boolean }) {
    const copy = () =>
        copyText(code)
            .then(() => toast.success('Copied to clipboard'))
            .catch(() => toast.error('Could not copy to the clipboard.'));
    return (
        <div className="relative rounded-md bg-muted">
            {copyable && (
                <Button
                    variant="ghost"
                    size="icon-xs"
                    className="absolute top-2 right-2"
                    aria-label="Copy to clipboard"
                    onClick={() => void copy()}
                >
                    <CopyIcon aria-hidden />
                </Button>
            )}
            <pre className="overflow-x-auto p-4 pr-10 font-mono text-xs leading-relaxed whitespace-pre">{code}</pre>
        </div>
    );
}

export function Note({ children }: { readonly children: ReactNode }) {
    return (
        <div className="flex gap-2 rounded-md border border-border p-3 text-sm text-muted-foreground">
            <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
            <div className="space-y-1">{children}</div>
        </div>
    );
}
