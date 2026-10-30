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
import {
    Field,
    FieldDescription,
    FieldLabel,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@gravitee/graphene-core';

const UNSPECIFIED = 'unspecified';

interface OptionSelectProps {
    readonly id: string;
    readonly label: string;
    readonly hint: string;
    readonly value: string | undefined;
    readonly options: readonly string[];
    readonly disabled: boolean;
    readonly onChange: (value: string | undefined) => void;
}

/** "unspecified" maps to `undefined`, so the key is left out of the saved settings. */
export function OptionSelect({ id, label, hint, value, options, disabled, onChange }: OptionSelectProps) {
    return (
        <Field>
            <FieldLabel htmlFor={id}>{label}</FieldLabel>
            <Select
                value={value ?? UNSPECIFIED}
                disabled={disabled}
                onValueChange={next => onChange(next === UNSPECIFIED ? undefined : (next ?? undefined))}
            >
                <SelectTrigger id={id} className="w-full">
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    {[UNSPECIFIED, ...options].map(option => (
                        <SelectItem key={option} value={option}>
                            {option}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <FieldDescription>{hint}</FieldDescription>
        </Field>
    );
}
