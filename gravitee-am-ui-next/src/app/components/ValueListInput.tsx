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
import { Badge, Button, Input } from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';

interface ValueListInputProps {
    /** The noun of one value, as in "Add request URI" and "Remove <value>". */
    readonly label: string;
    readonly values: readonly string[];
    /** False hides the input and the remove actions, for a user without the update permission. */
    readonly editable: boolean;
    readonly placeholder?: string;
    readonly emptyText: string;
    /** Shows the values in a monospace font. */
    readonly mono?: boolean;
    /** Shows the values as badges on one wrapping line, not as a bordered list. */
    readonly chips?: boolean;
    readonly onChange: (values: string[]) => void;
    /** Called instead of onChange when the typed value is already in the list. */
    readonly onDuplicate: (value: string) => void;
    /** Called instead of onChange on a remove, so the page can confirm first, as some Angular lists do. */
    readonly onRemoveRequest?: (value: string) => void;
}

/** An editable list of strings: an input with Add, and a remove action per value. The Angular input plus `ngx-datatable` lists. */
export function ValueListInput({
    label,
    values,
    editable,
    placeholder,
    emptyText,
    mono = true,
    chips = false,
    onChange,
    onDuplicate,
    onRemoveRequest,
}: ValueListInputProps) {
    const [draft, setDraft] = useState('');

    function add() {
        const value = draft.trim();
        if (!value) {
            return;
        }
        if (values.includes(value)) {
            onDuplicate(value);
            return;
        }
        onChange([...values, value]);
        setDraft('');
    }

    function remove(value: string) {
        if (onRemoveRequest) {
            onRemoveRequest(value);
        } else {
            onChange(values.filter(other => other !== value));
        }
    }

    return (
        <div className="flex flex-col gap-3">
            {editable && (
                <div className="flex gap-2">
                    <Input
                        aria-label={`Add ${label}`}
                        placeholder={placeholder}
                        value={draft}
                        onChange={event => setDraft(event.target.value)}
                        onKeyDown={event => {
                            if (event.key === 'Enter') {
                                event.preventDefault();
                                add();
                            }
                        }}
                    />
                    <Button type="button" variant="outline" onClick={add} disabled={!draft.trim()}>
                        <PlusIcon aria-hidden />
                        Add
                    </Button>
                </div>
            )}
            {values.length === 0 ? (
                <p className="text-sm text-muted-foreground">{emptyText}</p>
            ) : chips ? (
                <ul className="flex flex-wrap gap-2">
                    {values.map(value => (
                        <li key={value}>
                            <Badge variant="secondary" className={mono ? 'gap-1 pr-1 font-mono' : 'gap-1 pr-1'}>
                                {value}
                                {editable && (
                                    <button
                                        type="button"
                                        aria-label={`Remove ${value}`}
                                        onClick={() => remove(value)}
                                        className="rounded-sm opacity-70 hover:opacity-100"
                                    >
                                        <XIcon className="size-3" aria-hidden />
                                    </button>
                                )}
                            </Badge>
                        </li>
                    ))}
                </ul>
            ) : (
                <ul className="rounded-md border border-border">
                    {values.map(value => (
                        <li
                            key={value}
                            className="flex items-center justify-between gap-2 border-b border-border px-3 py-2 last:border-b-0"
                        >
                            <span className={mono ? 'truncate font-mono text-sm' : 'truncate text-sm'} title={value}>
                                {value}
                            </span>
                            {editable && (
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon-xs"
                                    aria-label={`Remove ${value}`}
                                    onClick={() => remove(value)}
                                >
                                    <XIcon aria-hidden />
                                </Button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
