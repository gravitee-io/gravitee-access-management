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
import { Button, Collapsible, CollapsibleContent, CollapsibleTrigger } from '@gravitee/graphene-core';
import { ChevronDownIcon, ChevronRightIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';

/** Example mappings, each a source and the value it maps onto, behind a toggle that starts closed. */
export function MapperExamples({ examples }: { readonly examples: readonly (readonly [string, string])[] }) {
    const [open, setOpen] = useState(false);
    const width = Math.max(...examples.map(([source]) => source.length));

    return (
        <Collapsible open={open} onOpenChange={setOpen}>
            <CollapsibleTrigger asChild>
                <Button type="button" variant="link" size="sm" className="h-auto gap-1 p-0 text-muted-foreground">
                    {open ? <ChevronDownIcon aria-hidden /> : <ChevronRightIcon aria-hidden />}
                    {open ? 'Hide examples' : 'Show examples'}
                </Button>
            </CollapsibleTrigger>
            <CollapsibleContent>
                <pre className="mt-2 overflow-x-auto rounded-md bg-muted px-3 py-2 font-mono text-xs">
                    {examples.map(([source, target]) => `${source.padEnd(width)}  →  ${target}`).join('\n')}
                </pre>
            </CollapsibleContent>
        </Collapsible>
    );
}
