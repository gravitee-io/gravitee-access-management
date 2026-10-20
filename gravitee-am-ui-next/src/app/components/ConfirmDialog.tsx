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
    Dialog,
    DialogClose,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@gravitee/graphene-core';
import type { ReactNode } from 'react';

interface ConfirmDialogProps {
    readonly open: boolean;
    /** Example: "Delete scope". */
    readonly title: string;
    /** What happens on confirm, and whether it can be undone. */
    readonly description: ReactNode;
    /** The confirm button label. Example: "Delete scope". */
    readonly confirmLabel: string;
    /** A delete or another action that cannot be undone. */
    readonly destructive?: boolean;
    /** True while the confirmed action runs. Both buttons are disabled. */
    readonly pending?: boolean;
    readonly onOpenChange: (open: boolean) => void;
    readonly onConfirm: () => void;
}

/** The question before a delete or another action that needs a second click. Replaces the Angular `DialogService.confirm`. */
export function ConfirmDialog({
    open,
    title,
    description,
    confirmLabel,
    destructive,
    pending,
    onOpenChange,
    onConfirm,
}: ConfirmDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    <DialogDescription>{description}</DialogDescription>
                </DialogHeader>
                <DialogFooter>
                    <DialogClose asChild>
                        <Button variant="ghost" disabled={pending}>
                            Cancel
                        </Button>
                    </DialogClose>
                    <Button variant={destructive ? 'destructive' : 'default'} disabled={pending} onClick={onConfirm}>
                        {confirmLabel}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
