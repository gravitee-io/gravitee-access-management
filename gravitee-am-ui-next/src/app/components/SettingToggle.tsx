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
import { Label, Switch } from '@gravitee/graphene-core';
import type { ReactNode } from 'react';

interface SettingToggleProps {
    readonly id: string;
    readonly label: string;
    readonly description?: ReactNode;
    readonly checked: boolean;
    readonly disabled?: boolean;
    readonly onChange: (checked: boolean) => void;
    /** The fields of this setting, shown under it. */
    readonly children?: ReactNode;
}

/** One row of a settings card: a label, its description and a switch, with the setting's own fields below. */
export function SettingToggle({ id, label, description, checked, disabled, onChange, children }: SettingToggleProps) {
    return (
        <div className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0">
            <div className="flex items-start justify-between gap-4">
                <div className="flex flex-col gap-0.5">
                    <Label htmlFor={id}>{label}</Label>
                    {description ? (
                        <p id={`${id}-description`} className="text-sm text-muted-foreground">
                            {description}
                        </p>
                    ) : null}
                </div>
                <Switch
                    id={id}
                    aria-describedby={description ? `${id}-description` : undefined}
                    checked={checked}
                    disabled={disabled}
                    onCheckedChange={onChange}
                />
            </div>
            {children}
        </div>
    );
}
