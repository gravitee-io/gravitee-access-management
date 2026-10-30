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
import { StarIcon, TagIcon } from '@gravitee/graphene-core/icons';

interface DomainPreferenceTogglesProps {
    readonly name: string;
    readonly isDefault: boolean;
    readonly isPinned: boolean;
    readonly disabled: boolean;
    readonly onToggleDefault: () => void;
    readonly onTogglePin: () => void;
}

export function DomainPreferenceToggles({
    name,
    isDefault,
    isPinned,
    disabled,
    onToggleDefault,
    onTogglePin,
}: DomainPreferenceTogglesProps) {
    return (
        <div className="flex justify-end gap-1">
            <Button
                variant="ghost"
                size="icon-xs"
                aria-label={`Default domain ${name}`}
                aria-pressed={isDefault}
                title={isDefault ? 'Unset default domain' : 'Set as default domain'}
                disabled={disabled}
                onClick={onToggleDefault}
            >
                <StarIcon className={isDefault ? 'size-4 fill-current text-primary' : 'size-4'} aria-hidden />
            </Button>
            <Button
                variant="ghost"
                size="icon-xs"
                aria-label={`Pin ${name}`}
                aria-pressed={isPinned}
                title={isPinned ? 'Unpin domain' : 'Pin domain'}
                disabled={disabled}
                onClick={onTogglePin}
            >
                <TagIcon className={isPinned ? 'size-4 fill-current text-primary' : 'size-4'} aria-hidden />
            </Button>
        </div>
    );
}
