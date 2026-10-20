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
import { Badge, Input } from '@gravitee/graphene-core';
import { XIcon } from '@gravitee/graphene-core/icons';
import { useState } from 'react';

interface TagInputProps {
    readonly id: string;
    readonly values: readonly string[];
    readonly onChange: (values: string[]) => void;
    /** Called instead of onChange when the typed value is already in the list. */
    readonly onDuplicate?: (value: string) => void;
    readonly placeholder?: string;
    readonly disabled?: boolean;
}

/** A list of strings as removable badges. Enter adds the typed value. The Angular `mat-chip-grid` with an input. */
export function TagInput({ id, values, onChange, onDuplicate, placeholder, disabled }: TagInputProps) {
    const [text, setText] = useState('');

    function add() {
        const value = text.trim();
        if (!value) {
            return;
        }
        if (values.includes(value)) {
            onDuplicate?.(value);
            return;
        }
        onChange([...values, value]);
        setText('');
    }

    return (
        <div className="flex flex-col gap-2">
            {values.length > 0 && (
                <ul className="flex flex-wrap gap-1.5">
                    {values.map(value => (
                        <li key={value}>
                            <Badge variant="secondary" className="gap-1 pr-1">
                                {value}
                                <button
                                    type="button"
                                    aria-label={`Remove ${value}`}
                                    disabled={disabled}
                                    onClick={() => onChange(values.filter(other => other !== value))}
                                    className="rounded-sm opacity-70 hover:opacity-100 disabled:pointer-events-none"
                                >
                                    <XIcon className="size-3" aria-hidden />
                                </button>
                            </Badge>
                        </li>
                    ))}
                </ul>
            )}
            <Input
                id={id}
                value={text}
                placeholder={placeholder}
                disabled={disabled}
                onChange={event => setText(event.target.value)}
                onKeyDown={event => {
                    if (event.key === 'Enter') {
                        event.preventDefault();
                        add();
                    }
                }}
            />
        </div>
    );
}
