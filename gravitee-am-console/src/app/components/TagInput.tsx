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
    /** Also adds the typed value when the input loses focus. */
    readonly addOnBlur?: boolean;
    /** Also adds the typed value on a space, and splits a pasted text on its spaces. For values that hold no space. */
    readonly addOnSpace?: boolean;
    readonly placeholder?: string;
    readonly disabled?: boolean;
    readonly invalid?: boolean;
    readonly describedBy?: string;
    readonly onBlur?: () => void;
}

export function TagInput({
    id,
    values,
    onChange,
    onDuplicate,
    addOnBlur = false,
    addOnSpace = false,
    placeholder,
    disabled,
    invalid,
    describedBy,
    onBlur,
}: TagInputProps) {
    const [text, setText] = useState('');

    function add(input = text) {
        const tokens = (addOnSpace ? input.split(/\s+/) : [input]).map(token => token.trim()).filter(Boolean);
        const next = [...values];
        for (const token of tokens) {
            if (next.includes(token)) {
                onDuplicate?.(token);
            } else {
                next.push(token);
            }
        }
        if (next.length > values.length) {
            onChange(next);
        }
        // A single duplicate keeps its text, so the user can fix it.
        if (next.length > values.length || tokens.length > 1) {
            setText('');
        }
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
                    aria-invalid={invalid}
                    aria-describedby={describedBy}
                    onKeyDown={event => {
                        if ((event.key === 'Enter' || (addOnSpace && event.key === ' ')) && !event.nativeEvent.isComposing) {
                            event.preventDefault();
                            add();
                        }
                    }}
                    onPaste={event => {
                        const pasted = event.clipboardData.getData('text');
                        if (addOnSpace && /\s/.test(pasted)) {
                            event.preventDefault();
                            add(text + pasted);
                        }
                    }}
                    onBlur={() => {
                        if (addOnBlur) {
                            add();
                        }
                        onBlur?.();
                    }}
                />
            </ComboboxChips>
        </Combobox>
    );
}
