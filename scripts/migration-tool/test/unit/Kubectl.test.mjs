import { Kubectl } from '../../lib/infra/kubernetes/Kubectl.mjs';
import { jest } from '@jest/globals';

/**
 * TDD for Kubectl Wrapper
 */
describe('Kubectl', () => {
    let kubectl;
    let mockShell;

    beforeEach(() => {
        // Mock zx shell fluent API factory
        const createMockSession = (result = { exitCode: 0, stdout: '{}' }) => {
            const session = Promise.resolve(result);
            session.quiet = () => session;
            session.nothrow = () => session;
            return session;
        };

        mockShell = jest.fn().mockImplementation(() => createMockSession());

        kubectl = new Kubectl({
            namespace: 'gravitee-am',
            shell: mockShell
        });
    });

    test('should check if secret exists', async () => {
        // Success case
        mockShell.mockImplementation(() => {
            const session = Promise.resolve({ exitCode: 0 });
            session.quiet = () => session;
            return session;
        });
        const exists = await kubectl.secretExists('my-secret');
        expect(exists).toBe(true);
    });

    test('should return false if secret does not exist', async () => {
        // Error case
        mockShell.mockImplementation(() => {
            const session = Promise.reject({ exitCode: 1 });
            session.quiet = () => session;
            return session;
        });
        const exists = await kubectl.secretExists('non-existent');
        expect(exists).toBe(false);
    });

    test('should create secret from file', async () => {
        await kubectl.createSecretFromFile('my-secret', '/path/to/file', 'my-key');

        const args = mockShell.mock.calls[0];
        // kubectl create secret generic ${name} -n ${this.namespace} --from-file=${keyName}=${filePath}
        expect(args[0][0]).toContain('kubectl create secret generic');
        expect(args[1]).toBe('my-secret');
        expect(args[2]).toBe('gravitee-am');
        expect(args[3]).toBe('my-key');
        expect(args[4]).toBe('/path/to/file');
    });

    test('should get pods by selector', async () => {
        const mockPods = {
            items: [{ metadata: { name: 'pod-1' } }]
        };
        mockShell.mockImplementation(() => {
            const session = Promise.resolve({ stdout: JSON.stringify(mockPods) });
            session.quiet = () => session;
            return session;
        });

        const pods = await kubectl.getPods('app=api');
        expect(pods).toHaveLength(1);
        expect(pods[0].metadata.name).toBe('pod-1');
    });

    test('should ensure namespace exists (idempotent)', async () => {
        await kubectl.ensureNamespace();
        const call = mockShell.mock.calls[0];
        const parts = call[0];
        const values = call.slice(1);
        const cmd = parts.reduce((acc, p, i) => acc + p + (values[i] !== undefined ? String(values[i]) : ''), '');
        expect(cmd).toContain('kubectl create namespace');
        expect(cmd).toContain('gravitee-am');
        expect(cmd).toContain('apply -f -');
    });

    test('should create secret from literals (idempotent)', async () => {
        await kubectl.createSecretGeneric('mongo-auth', {
            'mongodb-root-password': 'root',
            'mongodb-passwords': 'user'
        });
        const call = mockShell.mock.calls[0];
        const parts = call[0];
        const values = call.slice(1);
        const cmd = parts.reduce((acc, p, i) => acc + p + (values[i] !== undefined ? String(values[i]) : ''), '');
        expect(cmd).toContain('kubectl create secret generic');
        expect(cmd).toContain('mongo-auth');
        expect(cmd).toContain('--from-literal');
        expect(cmd).toContain('apply -f -');
    });

    test('checkClusterReachable resolves when cluster-info succeeds', async () => {
        mockShell.mockImplementation(() => Promise.resolve({ exitCode: 0 }));
        await expect(kubectl.checkClusterReachable()).resolves.toBeUndefined();
        const cmdParts = mockShell.mock.calls[0][0];
        const cmd = Array.isArray(cmdParts) ? cmdParts.join('') : String(cmdParts);
        expect(cmd).toContain('kubectl cluster-info');
    });

    test('checkClusterReachable throws clear error when cluster unreachable', async () => {
        mockShell.mockRejectedValue(new Error('connection refused'));
        await expect(kubectl.checkClusterReachable()).rejects.toThrow(
            'Kubernetes cluster unreachable. Start a local cluster first (e.g. kind create cluster --name am-migration).'
        );
    });

    test('logs returns pod stdout for a selector', async () => {
        mockShell.mockImplementation(() => {
            const session = Promise.resolve({ stdout: 'line1\nERROR boom' });
            session.quiet = () => session;
            session.nothrow = () => session;
            return session;
        });

        const out = await kubectl.logs('app.kubernetes.io/component=gateway');

        expect(out).toContain('ERROR boom');
        const args = mockShell.mock.calls[0][1]; // first interpolated value = args array
        expect(args).toEqual(expect.arrayContaining(['logs', '-l', 'app.kubernetes.io/component=gateway']));
        expect(args).not.toContain('--previous');
    });

    test('logs appends previous-container logs when previous:true', async () => {
        const responses = [
            { stdout: 'current ERROR a' },
            { stdout: 'previous ERROR b' }
        ];
        let call = 0;
        mockShell.mockImplementation(() => {
            const session = Promise.resolve(responses[call++] ?? { stdout: '' });
            session.quiet = () => session;
            session.nothrow = () => session;
            return session;
        });

        const out = await kubectl.logs('app=gateway', { since: '120s', previous: true });

        expect(out).toContain('current ERROR a');
        expect(out).toContain('previous ERROR b');
        const firstArgs = mockShell.mock.calls[0][1];
        expect(firstArgs).toContain('--since=120s');
        const secondArgs = mockShell.mock.calls[1][1];
        expect(secondArgs).toContain('--previous');
    });

    test('logs returns empty string on failure (never throws)', async () => {
        mockShell.mockImplementation(() => {
            const session = Promise.reject(new Error('no such pod'));
            session.quiet = () => session;
            session.nothrow = () => session;
            return session;
        });
        await expect(kubectl.logs('app=gateway')).resolves.toBe('');
    });
});
