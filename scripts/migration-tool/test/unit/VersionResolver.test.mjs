import { jest } from '@jest/globals';

// Mock execFileSync for git operations
const mockExecFileSync = jest.fn();
jest.unstable_mockModule('node:child_process', () => ({
    execFileSync: mockExecFileSync
}));

// Mock fetch for Docker Hub API
const mockFetch = jest.fn();
global.fetch = mockFetch;

const {
    parseSemverTag,
    parseBranchTag,
    resolveFromGit,
    resolveFromDockerHub,
    resolveTagToVersion,
    validateMigrationParams
} = await import('../../lib/core/VersionResolver.mjs');

describe('parseSemverTag', () => {
    test('parses release version 4.10.0 to 4.10', () => {
        expect(parseSemverTag('4.10.0')).toBe('4.10');
    });

    test('parses pre-release 4.11.0-alpha.3 to 4.11', () => {
        expect(parseSemverTag('4.11.0-alpha.3')).toBe('4.11');
    });

    test('parses snapshot 4.12.0-SNAPSHOT to 4.12', () => {
        expect(parseSemverTag('4.12.0-SNAPSHOT')).toBe('4.12');
    });

    test('returns null for master-latest', () => {
        expect(parseSemverTag('master-latest')).toBeNull();
    });

    test('returns null for latest', () => {
        expect(parseSemverTag('latest')).toBeNull();
    });

    test('returns null for 4-10-x-latest', () => {
        expect(parseSemverTag('4-10-x-latest')).toBeNull();
    });

    test('parses 4.10 (no patch) to 4.10', () => {
        expect(parseSemverTag('4.10')).toBe('4.10');
    });

    test('parses v4.10.0 with v prefix to 4.10', () => {
        expect(parseSemverTag('v4.10.0')).toBe('4.10');
    });
});

describe('parseBranchTag', () => {
    test('master-latest returns branch master', () => {
        const result = parseBranchTag('master-latest');
        expect(result).toEqual({ branch: 'master', version: null });
    });

    test('4-10-x-latest returns version 4.10', () => {
        const result = parseBranchTag('4-10-x-latest');
        expect(result).toEqual({ branch: null, version: '4.10' });
    });

    test('4-11-x-latest returns version 4.11', () => {
        const result = parseBranchTag('4-11-x-latest');
        expect(result).toEqual({ branch: null, version: '4.11' });
    });

    test('returns null for semver tag 4.10.0', () => {
        expect(parseBranchTag('4.10.0')).toBeNull();
    });

    test('returns null for latest', () => {
        expect(parseBranchTag('latest')).toBeNull();
    });
});

describe('resolveFromGit', () => {
    beforeEach(() => {
        mockExecFileSync.mockReset();
    });

    test('resolves master branch to version from pom.xml', () => {
        mockExecFileSync.mockReturnValue(
            '        <version>4.12.0-SNAPSHOT</version>\n'
        );
        expect(resolveFromGit('master')).toBe('4.12');
        expect(mockExecFileSync).toHaveBeenCalledWith(
            'git',
            ['show', 'origin/master:gravitee-am-service/pom.xml'],
            expect.any(Object)
        );
    });

    test('throws when branch not found', () => {
        mockExecFileSync.mockImplementation(() => {
            throw new Error('fatal: path not found');
        });
        expect(() => resolveFromGit('nonexistent')).toThrow(/Could not resolve version from git branch/);
    });
});

describe('resolveFromDockerHub', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    test('resolves latest to actual version via Docker Hub API', async () => {
        // First call: get tag digest
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => ({ name: 'latest', images: [{ digest: 'sha256:abc' }] })
        });
        // Second call: list tags to find semver match
        mockFetch.mockResolvedValueOnce({
            ok: true,
            json: async () => ({
                results: [
                    { name: 'latest', images: [{ digest: 'sha256:abc' }] },
                    { name: '4.10.6', images: [{ digest: 'sha256:abc' }] },
                    { name: '4.10', images: [{ digest: 'sha256:abc' }] },
                ]
            })
        });
        const result = await resolveFromDockerHub('latest');
        expect(result).toBe('4.10');
    });

    test('throws when tag not found', async () => {
        mockFetch.mockResolvedValue({ ok: false, status: 404 });
        await expect(resolveFromDockerHub('nonexistent')).rejects.toThrow();
    });
});

describe('resolveTagToVersion', () => {
    beforeEach(() => {
        mockExecFileSync.mockReset();
        mockFetch.mockReset();
    });

    test('resolves 4.10.0 directly without git or network', async () => {
        const result = await resolveTagToVersion('4.10.0');
        expect(result).toBe('4.10');
        expect(mockExecFileSync).not.toHaveBeenCalled();
        expect(mockFetch).not.toHaveBeenCalled();
    });

    test('resolves 4.11.0-alpha.3 directly', async () => {
        expect(await resolveTagToVersion('4.11.0-alpha.3')).toBe('4.11');
    });

    test('resolves 4-10-x-latest from tag pattern', async () => {
        expect(await resolveTagToVersion('4-10-x-latest')).toBe('4.10');
        expect(mockExecFileSync).not.toHaveBeenCalled();
    });

    test('resolves master-latest via git', async () => {
        mockExecFileSync.mockReturnValue(
            '        <version>4.12.0-SNAPSHOT</version>\n'
        );
        expect(await resolveTagToVersion('master-latest')).toBe('4.12');
    });

    test('throws for unresolvable tag', async () => {
        await expect(resolveTagToVersion('garbage')).rejects.toThrow(/Cannot resolve version/);
    });
});

describe('validateMigrationParams', () => {
    beforeEach(() => {
        mockExecFileSync.mockReset();
        mockFetch.mockReset();
    });

    test('valid params: 4.10.0 to 4.11.0-alpha.3', async () => {
        const result = await validateMigrationParams({
            fromTag: '4.10.0',
            toTag: '4.11.0-alpha.3'
        });
        expect(result).toEqual({ fromVersion: '4.10', toVersion: '4.11' });
    });

    test('rejects same version: 4.11.0 and 4.11.0-alpha.3 both resolve to 4.11', async () => {
        await expect(validateMigrationParams({
            fromTag: '4.11.0',
            toTag: '4.11.0-alpha.3'
        })).rejects.toThrow(/both resolve to 4\.11/);
    });

    test('rejects from > to: 4.12.0 to 4.10.0', async () => {
        await expect(validateMigrationParams({
            fromTag: '4.12.0',
            toTag: '4.10.0'
        })).rejects.toThrow(/must be older/);
    });

    test('rejects ACR tag without registry: master-latest', async () => {
        mockExecFileSync.mockReturnValue('        <version>4.12.0-SNAPSHOT</version>\n');
        await expect(validateMigrationParams({
            fromTag: '4.10.0',
            toTag: 'master-latest'
        })).rejects.toThrow(/--registry/);
    });

    test('accepts ACR tag with registry', async () => {
        mockExecFileSync.mockReturnValue('        <version>4.12.0-SNAPSHOT</version>\n');
        const result = await validateMigrationParams({
            fromTag: '4.10.0',
            toTag: 'master-latest',
            registry: 'graviteeio.azurecr.io'
        });
        expect(result).toEqual({ fromVersion: '4.10', toVersion: '4.12' });
    });

    test('rejects unresolvable tag with clear message', async () => {
        await expect(validateMigrationParams({
            fromTag: 'garbage',
            toTag: '4.11.0'
        })).rejects.toThrow(/Cannot resolve version/);
    });
});
