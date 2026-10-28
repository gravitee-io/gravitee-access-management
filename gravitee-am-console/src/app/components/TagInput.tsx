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
import { Combobox, ComboboxChip, ComboboxChips, ComboboxChipsInput } from '@gravitee/graphene-core';
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
        <Combobox
            multiple
            value={[...values]}
            onValueChange={value => onChange(value as string[])}
            inputValue={text}
            // Base UI clears the input on Enter. Ignore that, so a duplicate keeps its text.
            onInputValueChange={(value, details) => details.reason !== 'input-clear' && setText(value)}
            disabled={disabled}
        >
            <ComboboxChips>
                {values.map(value => (
                    <ComboboxChip key={value} removeAriaLabel={`Remove ${value}`}>
                        {value}
                    </ComboboxChip>
                ))}
                <ComboboxChipsInput
                    id={id}
                    placeholder={values.length === 0 ? placeholder : undefined}
                    onKeyDown={event => {
                        if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
                            event.preventDefault();
                            add();
                        }
                    }}
                />
            </ComboboxChips>
        </Combobox>
    );
}
