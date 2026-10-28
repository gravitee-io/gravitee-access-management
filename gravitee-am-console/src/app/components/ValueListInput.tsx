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
import { Button, Input, Item, ItemActions, ItemContent, ItemGroup } from '@gravitee/graphene-core';
import { PlusIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';

interface ValueListInputProps {
    /** The noun of one value, as in "Add redirect URI". */
    readonly label: string;
    readonly values: readonly string[];
    /** False hides the input and the remove actions, for a user without the update permission. */
    readonly editable: boolean;
    readonly placeholder?: string;
    readonly emptyText: string;
    readonly onChange: (values: string[]) => void;
    /** Called instead of onChange when the typed value is already in the list. */
    readonly onDuplicate: (value: string) => void;
    /** Called instead of onChange on a remove, so the page can confirm first, as some Angular lists do. */
    readonly onRemoveRequest?: (value: string) => void;
}

/** An editable list of values, for example redirect URIs: an input with Add, and a remove action per value. */
export function ValueListInput({
    label,
    values,
    editable,
    placeholder,
    emptyText,
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
                            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
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
            ) : (
                <ItemGroup role="list" aria-label={label} className="gap-2">
                    {values.map(value => (
                        <Item key={value} role="listitem" variant="outline" size="sm">
                            <ItemContent className="truncate font-mono text-sm" title={value}>
                                {value}
                            </ItemContent>
                            {editable && (
                                <ItemActions>
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon-xs"
                                        aria-label={`Remove ${value}`}
                                        onClick={() => remove(value)}
                                    >
                                        <XIcon aria-hidden />
                                    </Button>
                                </ItemActions>
                            )}
                        </Item>
                    ))}
                </ItemGroup>
            )}
        </div>
    );
}
