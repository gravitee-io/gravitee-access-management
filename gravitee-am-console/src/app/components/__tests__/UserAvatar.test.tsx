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
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import { UserAvatar } from '../UserAvatar';

// jsdom loads no image.
class LoadedImage extends EventTarget {
    complete = true;
    naturalWidth = 1;
    set src(_value: string) {
        queueMicrotask(() => this.dispatchEvent(new Event('load')));
    }
}

describe('UserAvatar', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('shows the first letter of the username without a picture', () => {
        renderWithGraphene(<UserAvatar user={{ username: 'alice', name: 'Bob' }} />);

        expect(screen.getByRole('img', { name: 'alice' })).toHaveTextContent('A');
    });

    it('falls back to the name without a username', () => {
        renderWithGraphene(<UserAvatar user={{ name: 'bob' }} />);

        expect(screen.getByRole('img', { name: 'bob' })).toHaveTextContent('B');
    });

    it('shows the picture', async () => {
        vi.stubGlobal('Image', LoadedImage);
        renderWithGraphene(<UserAvatar user={{ username: 'alice', picture: 'https://a.test/alice.png' }} />);

        expect(await screen.findByRole('img', { name: 'alice' })).toHaveAttribute('src', 'https://a.test/alice.png');
    });

    it('takes the picture from the additional information', async () => {
        vi.stubGlobal('Image', LoadedImage);
        renderWithGraphene(<UserAvatar user={{ username: 'alice', additionalInformation: { picture: 'https://a.test/info.png' } }} />);

        expect(await screen.findByRole('img', { name: 'alice' })).toHaveAttribute('src', 'https://a.test/info.png');
    });
});
