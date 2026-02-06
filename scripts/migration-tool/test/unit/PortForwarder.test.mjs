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
        const mockSpawn = jest.fn();

        forwarder = new PortForwarder({
            namespace: 'gravitee-am',
            shell: mockShell,
            spawn: mockSpawn
        });
    });

    test('should start a port forward and return pid', async () => {
        const mockChild = { pid: 1234, unref: jest.fn() };
        forwarder.spawn.mockReturnValue(mockChild);

        const pid = await forwarder.start('svc/am-management-api', 8093, 83);

        expect(pid).toBe(1234);
        expect(forwarder.spawn).toHaveBeenCalled();
    });

    test('should stop a port forward by pid', async () => {
        // This will likely use process.kill or shell `kill`
        await forwarder.stop(1234);
        const args = mockShell.mock.calls[0];
        expect(args[1]).toBe(1234);
    });
});
