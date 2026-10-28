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
import { Button, useLayoutConfig } from '@gravitee/graphene-core';
import { useEffect, useRef } from 'react';

interface SaveBarProps {
    /** The id of the `<form>` that Save submits. */
    readonly formId: string;
    readonly dirty: boolean;
    readonly saving: boolean;
    readonly invalid?: boolean;
    readonly onDiscard: () => void;
}

/** Renders in the sticky layout banner while `dirty` is true. */
export function SaveBar({ formId, dirty, saving, invalid = false, onDiscard }: SaveBarProps) {
    // onDiscard changes on each render. In the deps, it would set the banner in a loop.
    const discard = useRef(onDiscard);
    useEffect(() => {
        discard.current = onDiscard;
    });

    useLayoutConfig(
        {
            banner: dirty ? (
                <div className="flex items-center justify-between gap-4 border-b border-border bg-background px-6 py-3">
                    <p className="text-sm">You have unsaved changes.</p>
                    <div className="flex gap-2">
                        <Button type="button" variant="outline" disabled={saving} onClick={() => discard.current()}>
                            Discard
                        </Button>
                        <Button type="submit" form={formId} disabled={saving || invalid}>
                            {saving ? 'Saving…' : 'Save'}
                        </Button>
                    </div>
                </div>
            ) : null,
            bannerSticky: true,
        },
        [formId, dirty, saving, invalid],
    );

    return null;
}
