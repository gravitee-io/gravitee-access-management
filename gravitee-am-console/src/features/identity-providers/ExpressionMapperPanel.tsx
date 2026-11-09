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
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, DataTableEmptyState, toast } from '@gravitee/graphene-core';
import { PlusIcon, Trash2Icon, UserIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { type ReactNode, useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { AddMappingDialog, type MapperTarget } from './AddMappingDialog';
import { MapperExamples } from './MapperExamples';

/** Target id to the user expressions mapped onto it. */
export type Mapper = Record<string, string[]>;

interface ExpressionMapperPanelProps {
    /** "role" or "group". */
    readonly noun: string;
    readonly title: string;
    readonly description: ReactNode;
    /** User expressions and the target each one maps onto. */
    readonly examples: readonly (readonly [string, string])[];
    readonly targets: readonly MapperTarget[];
    readonly mapper: Mapper;
    /** The toast when the user expression is already mapped onto the target. */
    readonly duplicateMessage: (user: string, targetName: string) => string;
    readonly canUpdate: boolean;
    readonly saving: boolean;
    readonly onSave: (mapper: Mapper) => void;
}

/**
 * User expressions (`attribute=value` or EL) mapped onto roles or groups at authentication. An add or a remove saves at
 * once.
 */
export function ExpressionMapperPanel({
    noun,
    title,
    description,
    examples,
    targets,
    mapper,
    duplicateMessage,
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
                errors.push(duplicateMessage(user, nameOf(target)));
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

    const selectable = targets.some(target => !target.disabled);
    const addButton = (
        <Button size="sm" disabled={saving || !selectable} onClick={() => setAdding(true)}>
            <PlusIcon />
            Add {noun} mapping
        </Button>
    );

    return (
        <>
            <Card>
                <CardHeader className="flex flex-row items-start justify-between gap-4">
                    <div className="flex flex-col gap-1.5">
                        <CardTitle>{title}</CardTitle>
                        <CardDescription>{description}</CardDescription>
                        <MapperExamples examples={examples} />
                    </div>
                    {canUpdate && keys.length > 0 && addButton}
                </CardHeader>
                <CardContent>
                    {keys.length === 0 ? (
                        <DataTableEmptyState
                            variant="first-use"
                            icon={<UsersIcon />}
                            title={`No ${noun} mappers yet`}
                            description={
                                selectable ? `Start adding users to ${noun}s.` : `This domain has no ${noun}s to map users onto yet.`
                            }
                            primaryAction={canUpdate ? addButton : undefined}
                        />
                    ) : (
                        <div className="flex flex-col">
                            {keys.map(target => (
                                <section key={target} className="border-b border-border py-3 last:border-b-0">
                                    <h2 className="text-sm font-semibold">{nameOf(target)}</h2>
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
