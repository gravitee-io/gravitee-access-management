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
    Button,
    Card,
    CardContent,
    CardHeader,
    CardTitle,
    DataTableEmptyState,
    Field,
    FieldLabel,
    Input,
    toast,
} from '@gravitee/graphene-core';
import { KeyIcon } from '@gravitee/graphene-core/icons';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { DurationInput } from '../../app/components/DurationInput';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SearchField } from '../../app/components/SearchField';
import { SettingToggle } from '../../app/components/SettingToggle';
import { TagInput } from '../../app/components/TagInput';
import { UserAvatar } from '../../app/components/UserAvatar';
import { ValueListInput } from '../../app/components/ValueListInput';
import { type DurationDraft, draftToSeconds } from '../../lib/forms/duration';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';

const SAMPLE_PICTURE =
    'data:image/svg+xml;utf8,' +
    encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="#2563eb"/></svg>');

function KitCard({ title, state, children }: { readonly title: string; readonly state: unknown; readonly children: ReactNode }) {
    return (
        <Card>
            <CardHeader>
                <CardTitle>
                    <h2>{title}</h2>
                </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
                {children}
                <pre aria-label={`${title} state`} className="rounded-md bg-muted p-2 text-xs">
                    {JSON.stringify(state)}
                </pre>
            </CardContent>
        </Card>
    );
}

function SaveBarDemo() {
    const [saved, setSaved] = useState('Default domain');
    const [name, setName] = useState(saved);
    const [saving, setSaving] = useState(false);
    const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
    useEffect(() => () => clearTimeout(timer.current), []);
    const dirty = name !== saved;
    return (
        <KitCard title="SaveBar" state={{ saved, name, dirty, saving }}>
            <form
                id="kit-save-bar"
                onSubmit={event => {
                    event.preventDefault();
                    setSaving(true);
                    timer.current = setTimeout(() => {
                        setSaved(name);
                        setSaving(false);
                        toast.success('Saved');
                    }, 800);
                }}
            >
                <Field>
                    <FieldLabel htmlFor="kit-name">Name (edit to show the bar, clear to block Save)</FieldLabel>
                    <Input id="kit-name" value={name} onChange={event => setName(event.target.value)} />
                </Field>
            </form>
            <SaveBar formId="kit-save-bar" dirty={dirty} saving={saving} invalid={!name.trim()} onDiscard={() => setName(saved)} />
        </KitCard>
    );
}

function ConfirmDialogDemo() {
    const [open, setOpen] = useState(false);
    const [confirmed, setConfirmed] = useState(0);
    return (
        <KitCard title="ConfirmDialog" state={{ open, confirmed }}>
            <Button variant="destructive" className="self-start" onClick={() => setOpen(true)}>
                Delete scope
            </Button>
            <ConfirmDialog
                open={open}
                title="Delete scope"
                description="The scope is deleted. This cannot be undone."
                confirmLabel="Delete"
                destructive
                onOpenChange={setOpen}
                onConfirm={() => {
                    setConfirmed(count => count + 1);
                    setOpen(false);
                }}
            />
        </KitCard>
    );
}

function SettingToggleDemo() {
    const [checked, setChecked] = useState(false);
    return (
        <KitCard title="SettingToggle" state={{ checked }}>
            <SettingToggle
                id="kit-toggle"
                label="Enable MFA"
                description="Users must enroll a factor at the next login."
                checked={checked}
                onChange={setChecked}
            />
            <SettingToggle id="kit-toggle-disabled" label="Disabled setting" checked disabled onChange={() => undefined} />
        </KitCard>
    );
}

function TagInputDemo() {
    const [values, setValues] = useState(['openid', 'profile']);
    return (
        <KitCard title="TagInput" state={{ values }}>
            <Field>
                <FieldLabel htmlFor="kit-tags">Scopes</FieldLabel>
                <TagInput
                    id="kit-tags"
                    values={values}
                    placeholder="Type a scope, then Enter"
                    onChange={setValues}
                    onDuplicate={value => toast.error(`${value} is already in the list`)}
                />
            </Field>
        </KitCard>
    );
}

function ValueListInputDemo() {
    const [values, setValues] = useState(['https://app.example.com/callback']);
    const [editable, setEditable] = useState(true);
    return (
        <KitCard title="ValueListInput" state={{ values, editable }}>
            <SettingToggle id="kit-editable" label="Editable" checked={editable} onChange={setEditable} />
            <ValueListInput
                label="redirect URI"
                values={values}
                editable={editable}
                placeholder="https://"
                emptyText="No redirect URI."
                onChange={setValues}
                onDuplicate={value => toast.error(`${value} is already in the list`)}
            />
        </KitCard>
    );
}

function SearchFieldDemo() {
    const [query, setQuery] = useState('');
    return (
        <KitCard title="SearchField" state={{ query }}>
            <SearchField label="scopes" onSearch={setQuery} />
        </KitCard>
    );
}

function DurationInputDemo() {
    const [value, setValue] = useState<DurationDraft>({ time: '2', unit: 'hours' });
    return (
        <KitCard title="DurationInput" state={{ ...value, seconds: draftToSeconds(value) }}>
            <Field>
                <FieldLabel htmlFor="kit-duration">Expiry</FieldLabel>
                <DurationInput id="kit-duration" label="Expiry" value={value} onChange={setValue} />
            </Field>
        </KitCard>
    );
}

function UserAvatarDemo() {
    return (
        <KitCard title="UserAvatar" state="no state">
            <div className="flex items-center gap-3">
                <UserAvatar user={{ username: 'alice' }} />
                <UserAvatar user={{ name: 'bob' }} size="lg" />
                <UserAvatar user={{ username: 'carol', picture: SAMPLE_PICTURE }} />
                <UserAvatar user={{ username: 'dave', picture: '/missing-avatar.png' }} />
            </div>
        </KitCard>
    );
}

function EmptyStateDemo() {
    return (
        <KitCard title="Empty state (Graphene DataTableEmptyState)" state="no state">
            <div className="rounded-lg border border-border">
                <DataTableEmptyState
                    variant="first-use"
                    icon={<KeyIcon />}
                    title="No scopes"
                    description="A scope limits what an application can do with a token."
                    primaryAction={<Button>Create a scope</Button>}
                />
            </div>
        </KitCard>
    );
}

export function KitPage() {
    useBreadcrumbs([{ label: 'Component kit' }]);
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Component kit" description="The shared parts of the console. This page exists only in development builds." />
            <SaveBarDemo />
            <ConfirmDialogDemo />
            <SettingToggleDemo />
            <TagInputDemo />
            <ValueListInputDemo />
            <SearchFieldDemo />
            <DurationInputDemo />
            <UserAvatarDemo />
            <EmptyStateDemo />
        </div>
    );
}
