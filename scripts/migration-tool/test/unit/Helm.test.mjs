import { Helm } from '../../lib/infra/kubernetes/Helm.mjs';
import { jest } from '@jest/globals';

/**
 * TDD for Helm Wrapper
 */
describe('Helm', () => {
    let helm;
    let mockShell;

    beforeEach(() => {
        // Mock zx shell fluent API factory
        const createMockSession = (result = { exitCode: 0, stdout: '' }) => {
            const session = Promise.resolve(result);
            session.quiet = () => session;
            session.nothrow = () => session;
            return session;
        };

        mockShell = jest.fn().mockImplementation(() => createMockSession());

        helm = new Helm({
            namespace: 'gravitee-am',
            shell: mockShell
        });
    });

    test('should install or upgrade a chart with set values', async () => {
        await helm.installOrUpgrade('am', 'graviteeio/am', {
            valuesFile: 'values.yaml',
            wait: true,
            createNamespace: true,
            version: '4.7.0',
            set: {
                'api.image.tag': '4.10.0',
                'license.key': 'dGVzdC1saWNlbnNl'
            }
        });

        const args = mockShell.mock.calls[0];
        const flags = args[1];

        expect(flags).toContain('--install');
        expect(flags).toContain('am');
        expect(flags).toContain('graviteeio/am');
        expect(flags).toContain('-f');
        expect(flags).toContain('values.yaml');
        expect(flags).toContain('--set');
        expect(flags).toContain('api.image.tag=4.10.0');
        expect(flags).toContain('license.key=dGVzdC1saWNlbnNl');
        expect(flags).toContain('--version');
        expect(flags).toContain('4.7.0');
    });

    test('should install or upgrade with multiple values files (base + override)', async () => {
        await helm.installOrUpgrade('am-mapi', 'graviteeio/am', {
            valuesFiles: ['am-mongodb-common.yaml', 'am-mongodb-mapi.yaml'],
            wait: true
        });

        const args = mockShell.mock.calls[0];
        const flags = args[1];
        expect(flags).toContain('-f');
        expect(flags).toContain('am-mongodb-common.yaml');
        expect(flags).toContain('am-mongodb-mapi.yaml');
        const fIndexes = flags.flatMap((f, i) => (f === '-f' ? [i] : []));
        expect(fIndexes.length).toBe(2);
        expect(flags[fIndexes[0] + 1]).toBe('am-mongodb-common.yaml');
        expect(flags[fIndexes[1] + 1]).toBe('am-mongodb-mapi.yaml');
    });

    test('should add a repository', async () => {
        await helm.repoAdd('bitnami', 'https://charts.bitnami.com/bitnami');

        const args = mockShell.mock.calls[0];
        expect(args[1]).toBe('bitnami');
        expect(args[2]).toBe('https://charts.bitnami.com/bitnami');
    });
});
