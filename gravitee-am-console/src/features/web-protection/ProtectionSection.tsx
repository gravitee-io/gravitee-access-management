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
import { Card, CardContent, CardHeader, CardTitle } from '@gravitee/graphene-core';
import type { ReactNode } from 'react';
import { SettingToggle } from '../../app/components/SettingToggle';
import type { Inheritable } from './web-protection-form';

interface ProtectionSectionProps {
    readonly id: string;
    readonly title: string;
    readonly inheritHint: string;
    readonly enableLabel: string;
    readonly enableHint: string;
    readonly settings: Inheritable;
    readonly editable: boolean;
    readonly onChange: (patch: Inheritable) => void;
    readonly children?: ReactNode;
}

export function ProtectionSection({
    id,
    title,
    inheritHint,
    enableLabel,
    enableHint,
    settings,
    editable,
    onChange,
    children,
}: ProtectionSectionProps) {
    const inherited = settings.inherited !== false;
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">{title}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-5">
                <SettingToggle
                    id={`${id}-inherit`}
                    label="Inherit platform settings"
                    description={inheritHint}
                    checked={inherited}
                    disabled={!editable}
                    onChange={checked => onChange(checked ? { inherited: true, enabled: false } : { inherited: false })}
                />
                {!inherited && (
                    <SettingToggle
                        id={`${id}-enabled`}
                        label={enableLabel}
                        description={enableHint}
                        checked={settings.enabled === true}
                        disabled={!editable}
                        onChange={enabled => onChange({ enabled })}
                    />
                )}
                {!inherited && settings.enabled && children}
            </CardContent>
        </Card>
    );
}
