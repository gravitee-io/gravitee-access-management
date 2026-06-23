import { jest } from '@jest/globals';

const { isConcreteVersion, resolveFloatingTag } = await import('../../lib/core/VersionResolver.mjs');

describe('isConcreteVersion', () => {
    it('accepts plain X.Y.Z', () => {
        expect(isConcreteVersion('4.11.9')).toBe(true);
    });
    it('rejects floating and variant tags', () => {
        for (const t of ['latest', '4', '4.11', '4.11.9-debian', 'latest-noble']) {
            expect(isConcreteVersion(t)).toBe(false);
        }
    });
});

describe('resolveFloatingTag', () => {
    afterEach(() => {
        delete global.fetch;
        jest.restoreAllMocks();
    });

    function mockHub({ tagDigest, pages }) {
        // pages: array of arrays of {name, digest}
        global.fetch = jest.fn(async (url) => {
            if (url.includes('/tags/latest/') || url.includes('/tags/4.11/')) {
                return { ok: true, json: async () => ({ name: 'x', digest: tagDigest }) };
            }
            const m = url.match(/[?&]page=(\d+)/);
            const pageNum = m ? Number(m[1]) : 1;
            const results = pages[pageNum - 1] || [];
            return { ok: true, json: async () => ({ results, next: pageNum < pages.length ? 'more' : null }) };
        });
    }

    it('returns concrete tag unchanged without any fetch', async () => {
        global.fetch = jest.fn();
        await expect(resolveFloatingTag('4.11.9')).resolves.toBe('4.11.9');
        expect(global.fetch).not.toHaveBeenCalled();
    });

    it('resolves latest to the X.Y.Z sharing its digest', async () => {
        mockHub({
            tagDigest: 'sha256:abc',
            pages: [[
                { name: 'latest', digest: 'sha256:abc' },
                { name: '4', digest: 'sha256:abc' },
                { name: '4.11', digest: 'sha256:abc' },
                { name: '4.11.9', digest: 'sha256:abc' },
                { name: '4.11.8', digest: 'sha256:other' },
            ]],
        });
        await expect(resolveFloatingTag('latest')).resolves.toBe('4.11.9');
    });

    it('finds a match on a later page', async () => {
        mockHub({
            tagDigest: 'sha256:abc',
            pages: [
                [{ name: '4.10.5', digest: 'sha256:nope' }],
                [{ name: '4.11.9', digest: 'sha256:abc' }],
            ],
        });
        await expect(resolveFloatingTag('latest')).resolves.toBe('4.11.9');
    });

    it('throws when no X.Y.Z shares the digest', async () => {
        mockHub({
            tagDigest: 'sha256:abc',
            pages: [[{ name: '4.11.9', digest: 'sha256:different' }]],
        });
        await expect(resolveFloatingTag('latest')).rejects.toThrow(/Could not resolve floating tag "latest"/);
    });

    it('throws when the tag lookup 404s', async () => {
        global.fetch = jest.fn(async () => ({ ok: false, status: 404, json: async () => ({}) }));
        await expect(resolveFloatingTag('latest')).rejects.toThrow(/latest/);
    });

    it('throws with HTTP status when a list-page returns a non-OK response', async () => {
        global.fetch = jest.fn(async (url) => {
            if (url.includes('/tags/latest/')) {
                return { ok: true, json: async () => ({ name: 'latest', digest: 'sha256:abc' }) };
            }
            // First list page returns an error
            return { ok: false, status: 500, json: async () => ({}) };
        });
        await expect(resolveFloatingTag('latest')).rejects.toThrow(/HTTP 500/);
    });
});
