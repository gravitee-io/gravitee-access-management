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
import { Field, FieldContent, FieldDescription, FieldLabel, Switch } from '@gravitee/graphene-core';
import type { ReactNode } from 'react';

interface SettingToggleProps {
    readonly id: string;
    readonly label: string;
    readonly description?: ReactNode;
    readonly checked: boolean;
    readonly disabled?: boolean;
    readonly onChange: (checked: boolean) => void;
}

/** A labelled switch row with a description. The Angular `mat-slide-toggle` with its hint. */
export function SettingToggle({ id, label, description, checked, disabled, onChange }: SettingToggleProps) {
    const descriptionId = `${id}-description`;
    return (
        <Field orientation="horizontal">
            <FieldContent>
                <FieldLabel htmlFor={id}>{label}</FieldLabel>
                {description && <FieldDescription id={descriptionId}>{description}</FieldDescription>}
            </FieldContent>
            <Switch
                id={id}
                aria-describedby={description ? descriptionId : undefined}
                checked={checked}
                disabled={disabled}
                onCheckedChange={onChange}
            />
        </Field>
    );
}
