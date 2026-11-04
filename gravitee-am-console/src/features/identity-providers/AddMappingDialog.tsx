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
    Checkbox,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Field,
    FieldDescription,
    FieldLabel,
    FieldLegend,
    FieldSet,
    Input,
    Label,
    ScrollArea,
} from '@gravitee/graphene-core';
import { useState } from 'react';

export interface MapperTarget {
    readonly id: string;
    readonly name: string;
    /** AM refuses a mapping onto a system primary-owner role. */
    readonly disabled?: boolean;
}

function capitalize(text: string): string {
    return `${text[0].toUpperCase()}${text.slice(1)}`;
}

interface AddMappingDialogProps {
    /** "role" or "group". */
    readonly noun: string;
    readonly targets: readonly MapperTarget[];
    readonly onCancel: () => void;
    readonly onSave: (user: string, selected: string[]) => void;
}

/** Picks a user expression and the roles or groups it maps onto. */
export function AddMappingDialog({ noun, targets, onCancel, onSave }: AddMappingDialogProps) {
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
                    <FieldSet>
                        <FieldLegend variant="label">{`${capitalize(noun)}s`}</FieldLegend>
                        {targets.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No {noun}s exist yet.</p>
                        ) : (
                            <ScrollArea viewportProps={{ className: 'max-h-64' }}>
                                <div className="flex flex-col gap-2 p-1">
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
                            </ScrollArea>
                        )}
                    </FieldSet>
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
