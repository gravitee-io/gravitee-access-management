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
});
