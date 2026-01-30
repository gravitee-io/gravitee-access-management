import { PortForwarder } from '../../lib/infra/kubernetes/PortForwarder.mjs';
import { jest } from '@jest/globals';

/**
 * TDD for PortForwarder
 */
describe('PortForwarder', () => {
    let forwarder;
    let mockShell;

    beforeEach(() => {
        const createMockSession = (result = { exitCode: 0, stdout: '' }) => {
            const session = Promise.resolve(result);
            session.quiet = () => session;
            return session;
        };

        mockShell = jest.fn().mockImplementation(() => createMockSession());

        forwarder = new PortForwarder({
            namespace: 'gravitee-am',
            shell: mockShell
        });
    });

    test('should start a port forward and return pid', async () => {
        // Mock the background process execution
        // zx shell in background returns a ProcessPromise which has a .child property with pid
        const mockChild = { pid: 1234, kill: jest.fn(), unref: jest.fn() };
        mockShell.mockImplementation(() => {
            const p = Promise.resolve({});
            p.child = mockChild;
            p.quiet = () => p;
            return p;
        });

        const pid = await forwarder.start('svc/am-management-api', 8093, 83);

        expect(pid).toBe(1234);
        expect(mockShell).toHaveBeenCalled();
    });

    test('should stop a port forward by pid', async () => {
        // This will likely use process.kill or shell `kill`
        await forwarder.stop(1234);
        const args = mockShell.mock.calls[0];
        expect(args[1]).toBe(1234);
    });
});
