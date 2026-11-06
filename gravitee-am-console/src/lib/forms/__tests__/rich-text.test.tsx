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
import { render } from '@testing-library/react';
import { richText } from '../rich-text';

function html(text: string): string {
    return render(<p>{richText(text)}</p>).container.firstElementChild!.innerHTML;
}

describe('richText', () => {
    it('returns a text without markup as is', () => {
        expect(richText('Plain text.')).toBe('Plain text.');
    });

    it('keeps bold, italic, code and line breaks, including the Angular </br>', () => {
        expect(html('Set <i><b>(GIVEN_KEY)</b></i> or <code>JWKS_URL</code>.<br/>Then save.</br>Done.')).toBe(
            'Set <em><strong>(GIVEN_KEY)</strong></em> or <code class="font-mono text-xs">JWKS_URL</code>.<br>Then save.<br>Done.',
        );
    });

    it('keeps an http link in a new tab, and gives the text of a link without a safe href', () => {
        expect(html('See <a href="https://gravitee.io">docs</a>, <a href="javascript:alert(1)">this</a> or <a>that</a>.')).toBe(
            'See <a href="https://gravitee.io" target="_blank" rel="noreferrer" class="underline">docs</a>, this or that.',
        );
    });

    it('gives the text of any other tag, and runs no script', () => {
        expect(html('A <img src=x onerror="window.hacked=1"><script>window.hacked=1</script><span style="color:red">red</span>.')).toBe(
            'A red.',
        );
        expect((window as { hacked?: number }).hacked).toBeUndefined();
    });
});
