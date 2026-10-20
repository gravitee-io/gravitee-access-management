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
import { Button } from '@gravitee/graphene-core';

interface SaveBarProps {
    /** False hides the bar, for a user without the update permission. */
    readonly canEdit?: boolean;
    /** `form.formState.isDirty` for a react-hook-form page. */
    readonly dirty: boolean;
    /** `mutation.isPending`. */
    readonly saving: boolean;
    /** Blocks Save while the form has a validation error. */
    readonly invalid?: boolean;
    /** Omit inside a `<form>`: Save then submits the form. */
    readonly onSave?: () => void;
    /** `() => form.reset()` for a react-hook-form page. */
    readonly onDiscard: () => void;
}

/** The Save and Discard buttons of a settings form. Both stay disabled until the form is dirty. */
export function SaveBar({ canEdit = true, dirty, saving, invalid, onSave, onDiscard }: SaveBarProps) {
    if (!canEdit) {
        return null;
    }
    return (
        <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" disabled={!dirty || saving} onClick={onDiscard}>
                Discard changes
            </Button>
            <Button type={onSave ? 'button' : 'submit'} disabled={!dirty || saving || invalid} onClick={onSave}>
                {saving ? 'Saving…' : 'Save'}
            </Button>
        </div>
    );
}
