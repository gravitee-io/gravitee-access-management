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
    CardDescription,
    CardHeader,
    CardTitle,
    Checkbox,
    DataTableEmptyState,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
    Label,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon, Trash2Icon, UserIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { type ReactNode, useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';

export interface MapperTarget {
    readonly id: string;
    readonly name: string;
    /** AM refuses a mapping onto a system primary-owner role. */
    readonly disabled?: boolean;
}

/** Target id to the user expressions mapped onto it. */
export type Mapper = Record<string, string[]>;

interface ExpressionMapperPanelProps {
    /** "role" or "group". */
    readonly noun: string;
    readonly title: string;
    readonly description: ReactNode;
    readonly targets: readonly MapperTarget[];
    readonly mapper: Mapper;
    readonly canUpdate: boolean;
    readonly saving: boolean;
    readonly onSave: (mapper: Mapper) => void;
}

function capitalize(text: string): string {
    return `${text[0].toUpperCase()}${text.slice(1)}`;
}

function AddMappingDialog({
    noun,
    targets,
    onCancel,
    onSave,
}: {
    readonly noun: string;
    readonly targets: readonly MapperTarget[];
    readonly onCancel: () => void;
    readonly onSave: (user: string, selected: string[]) => void;
}) {
    const [user, setUser] = useState('');
    const [selected, setSelected] = useState<string[]>([]);
    const toggle = (id: string, on: boolean) => setSelected(current => (on ? [...current, id] : current.filter(other => other !== id)));

    return (
        <Dialog open onOpenChange={open => !open && onCancel()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>{`${capitalize(noun)} mappings`}</DialogTitle>
                    <DialogDescription>Map the users matching an expression onto {noun}s.</DialogDescription>
                </DialogHeader>
                <div className="grid gap-5">
                    <Field>
                        <FieldLabel htmlFor="mapper-user">Users</FieldLabel>
                        <Input
                            id="mapper-user"
                            className="font-mono"
                            placeholder="Use attributeName=attributeValue or EL syntax."
                            value={user}
                            onChange={event => setUser(event.target.value)}
                        />
                        <FieldDescription>
                            For example memberOf=administrators, or {"{#profile['my-claim'] == 'default'}"}.
                        </FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel>{`${capitalize(noun)}s`}</FieldLabel>
                        {targets.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No {noun}s exist yet.</p>
                        ) : (
                            <div className="flex flex-col gap-2">
                                {targets.map(target => (
                                    <div key={target.id} className="flex items-center gap-2">
                                        <Checkbox
                                            id={`mapper-${target.id}`}
                                            checked={selected.includes(target.id)}
                                            disabled={target.disabled}
                                            onCheckedChange={checked => toggle(target.id, checked === true)}
                                        />
                                        <Label htmlFor={`mapper-${target.id}`}>{target.name}</Label>
                                    </div>
                                ))}
                            </div>
                        )}
                    </Field>
                </div>
                <DialogFooter>
                    <Button variant="ghost" onClick={onCancel}>
                        Cancel
                    </Button>
                    <Button disabled={!user.trim() || selected.length === 0} onClick={() => onSave(user.trim(), selected)}>
                        Save
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

/**
 * The Angular `ProviderRolesComponent` and `ProviderGroupsComponent`: user expressions (`attribute=value` or EL) mapped
 * onto roles or groups at authentication. An add or a remove saves at once, as in Angular.
 */
export function ExpressionMapperPanel({
    noun,
    title,
    description,
    targets,
    mapper,
    canUpdate,
    saving,
    onSave,
}: ExpressionMapperPanelProps) {
    const [adding, setAdding] = useState(false);
    const [pendingRemove, setPendingRemove] = useState<{ target: string; user: string } | null>(null);
    const nameOf = (id: string) => targets.find(target => target.id === id)?.name ?? id;
    const keys = Object.keys(mapper);

    const add = (user: string, selected: string[]) => {
        const next: Mapper = structuredClone(mapper);
        const errors: string[] = [];
        let mapped = false;
        for (const target of selected) {
            const users = next[target];
            if (!users) {
                next[target] = [user];
                mapped = true;
            } else if (!users.includes(user)) {
                users.push(user);
                mapped = true;
            } else {
                errors.push(`'${user}' has already the '${nameOf(target)}' ${noun}`);
            }
        }
        setAdding(false);
        if (mapped) {
            onSave(next);
        }
        errors.forEach(message => toast.error(message));
    };

    const remove = () => {
        if (!pendingRemove) {
            return;
        }
        const { target, user } = pendingRemove;
        setPendingRemove(null);
        const next: Mapper = structuredClone(mapper);
        next[target] = next[target].filter(other => other !== user);
        if (next[target].length === 0) {
            delete next[target];
        }
        onSave(next);
    };

    const addButton = (
        <Button size="sm" disabled={saving} onClick={() => setAdding(true)}>
            <PlusIcon />
            Add {noun} mapping
        </Button>
    );

    return (
        <>
            <Card>
                <CardHeader className="flex flex-row items-start justify-between gap-4">
                    <div className="flex flex-col gap-1.5">
                        <CardTitle className="text-base">{title}</CardTitle>
                        <CardDescription>{description}</CardDescription>
                    </div>
                    {canUpdate && keys.length > 0 && addButton}
                </CardHeader>
                <CardContent>
                    {keys.length === 0 ? (
                        <DataTableEmptyState
                            variant="first-use"
                            icon={<UsersIcon />}
                            title={`No ${noun} mappers yet`}
                            description={`Start adding users to ${noun}s.`}
                            primaryAction={canUpdate ? addButton : undefined}
                        />
                    ) : (
                        <div className="flex flex-col">
                            {keys.map(target => (
                                <section key={target} className="border-b border-border py-3 last:border-b-0">
                                    <h3 className="text-sm font-semibold">{nameOf(target)}</h3>
                                    <ul className="mt-2 flex flex-col gap-1">
                                        {mapper[target].map(user => (
                                            <li key={user} className="flex items-center justify-between gap-2">
                                                <span className="flex min-w-0 items-center gap-2">
                                                    <UserIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                                                    <code className="truncate font-mono text-sm">{user}</code>
                                                </span>
                                                {canUpdate && (
                                                    <Button
                                                        variant="ghost"
                                                        size="icon"
                                                        disabled={saving}
                                                        aria-label={`Remove ${user} from ${nameOf(target)}`}
                                                        onClick={() => setPendingRemove({ target, user })}
                                                    >
                                                        <Trash2Icon />
                                                    </Button>
                                                )}
                                            </li>
                                        ))}
                                    </ul>
                                </section>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {adding && <AddMappingDialog noun={noun} targets={targets} onCancel={() => setAdding(false)} onSave={add} />}

            <ConfirmDialog
                open={pendingRemove !== null}
                title={`Delete entry from ${noun}`}
                description={
                    pendingRemove ? `Are you sure you want to remove this entry from the '${nameOf(pendingRemove.target)}' ${noun}?` : ''
                }
                confirmLabel="Remove"
                destructive
                onOpenChange={open => !open && setPendingRemove(null)}
                onConfirm={remove}
            />
        </>
    );
}
