import { Orchestrator } from '../../lib/Orchestrator.mjs';
import { jest } from '@jest/globals';

/**
 * TDD for Orchestrator
 */
describe('Orchestrator', () => {
    let orchestrator;
    let mockProvider;
    let options;

    beforeEach(() => {
        mockProvider = {
            clean: jest.fn(),
            setup: jest.fn(),
            deploy: jest.fn(),
            upgradeMapi: jest.fn(),
            upgradeGw: jest.fn(),
            prepareTests: jest.fn(),
            cleanup: jest.fn()
        };
        options = {
            fromTag: '4.10.0',
            toTag: 'latest'
        };
        orchestrator = new Orchestrator(mockProvider, options);
    });

    test('should execute stages in order', async () => {
        await orchestrator.run(['clean', 'deploy-from']);

        expect(mockProvider.clean).toHaveBeenCalled();
        expect(mockProvider.deploy).toHaveBeenCalledWith('4.10.0');
    });

    test('should call cleanup in finally block', async () => {
        mockProvider.clean.mockRejectedValue(new Error('failed'));

        try {
            await orchestrator.run(['clean']);
        } catch (e) {
            // expected
        }

        expect(mockProvider.cleanup).toHaveBeenCalled();
    });

    test('should skip cleanup when skipCleanup is true', async () => {
        await orchestrator.run(['clean'], { skipCleanup: true });

        expect(mockProvider.clean).toHaveBeenCalled();
        expect(mockProvider.cleanup).not.toHaveBeenCalled();
    });

    test('downgrade stages should call upgradeMapi/upgradeGw with fromTag', async () => {
        await orchestrator.run(['downgrade-mapi', 'downgrade-gw']);

        expect(mockProvider.upgradeMapi).toHaveBeenCalledWith('4.10.0');
        expect(mockProvider.upgradeGw).toHaveBeenCalledWith('4.10.0');
    });
});
