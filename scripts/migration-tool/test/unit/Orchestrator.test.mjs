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

    test('seed-alpha stage seeds the from-tag version under the alpha label', async () => {
        orchestrator.runSeed = jest.fn();

        await orchestrator.run(['seed-alpha']);

        expect(orchestrator.runSeed).toHaveBeenCalledWith(['--version', '4.10', '--label', 'alpha']);
    });

    test('seed-beta stage seeds the to-tag version under the beta label', async () => {
        options.toTag = '4.11.0';
        orchestrator.runSeed = jest.fn();

        await orchestrator.run(['seed-beta']);

        expect(orchestrator.runSeed).toHaveBeenCalledWith(['--version', '4.11', '--label', 'beta']);
    });

    test('seed-beta seeds the to-tag version even for a same-minor (patch) migration', async () => {
        options.fromTag = '4.10.7';
        options.toTag = '4.10.8';
        orchestrator.runSeed = jest.fn();

        await orchestrator.run(['seed-beta']);

        expect(orchestrator.runSeed).toHaveBeenCalledWith(['--version', '4.10', '--label', 'beta']);
    });

    test('seed / seed-upgrade remain available as backward-compatible aliases', async () => {
        options.toTag = '4.11.0';
        orchestrator.runSeed = jest.fn();

        await orchestrator.run(['seed']);
        await orchestrator.run(['seed-upgrade']);

        expect(orchestrator.runSeed).toHaveBeenNthCalledWith(1, ['--version', '4.10', '--label', 'alpha']);
        expect(orchestrator.runSeed).toHaveBeenNthCalledWith(2, ['--version', '4.11', '--label', 'beta']);
    });

    test('verify-alpha asserts the alpha channel', async () => {
        options.toTag = '4.11.8';
        orchestrator.runTests = jest.fn();

        await orchestrator.run(['verify-alpha']);

        expect(orchestrator.runTests).toHaveBeenCalledWith(expect.any(String), 'ci:migration', 'specs/migration', 'alpha');
    });

    test('verify-beta asserts the beta channel', async () => {
        options.toTag = '4.11.8';
        orchestrator.runTests = jest.fn();

        await orchestrator.run(['verify-beta']);

        expect(orchestrator.runTests).toHaveBeenCalledWith(expect.any(String), 'ci:migration', 'specs/migration', 'beta');
    });

    test('migration tests should default to the alpha label', () => {
        expect(orchestrator.getMigrationTestLabel()).toBe('alpha');
    });

    test('migration tests should use explicit testLabel when provided', () => {
        options.testLabel = 'beta';

        expect(orchestrator.getMigrationTestLabel()).toBe('beta');
    });
});
