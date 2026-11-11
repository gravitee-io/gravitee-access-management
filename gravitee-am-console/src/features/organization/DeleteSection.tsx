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
import { Button, Card, CardContent, CardHeader, CardTitle } from '@gravitee/graphene-core';
import { useState } from 'react';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';

interface DeleteSectionProps {
    readonly heading: string;
    readonly description: string;
    readonly confirmTitle: string;
    readonly confirmDescription: string;
    readonly pending: boolean;
    /** `close` dismisses the confirmation, for a delete that leaves the page open. */
    readonly onDelete: (close: () => void) => void;
}

/** The danger zone card of a detail page, with the confirmation of its Delete button. */
export function DeleteSection({ heading, description, confirmTitle, confirmDescription, pending, onDelete }: DeleteSectionProps) {
    const [confirmOpen, setConfirmOpen] = useState(false);
    return (
        <>
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Danger zone</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex flex-1 flex-col gap-0.5">
                            <p className="text-sm font-medium">{heading}</p>
                            <p className="text-sm text-muted-foreground">{description}</p>
                        </div>
                        <Button variant="destructive" disabled={pending} onClick={() => setConfirmOpen(true)}>
                            Delete
                        </Button>
                    </div>
                </CardContent>
            </Card>
            <ConfirmDialog
                open={confirmOpen}
                title={confirmTitle}
                description={confirmDescription}
                confirmLabel="Delete"
                destructive
                pending={pending}
                onOpenChange={setConfirmOpen}
                onConfirm={() => onDelete(() => setConfirmOpen(false))}
            />
        </>
    );
}
