import { Orchestrator } from '../../lib/Orchestrator.mjs';
import { jest } from '@jest/globals';
import { EventEmitter } from 'node:events';

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

    test('run should return results with status and duration per stage', async () => {
        const results = await orchestrator.run(['clean', 'deploy-from']);

        expect(results).toHaveLength(2);
        expect(results[0]).toEqual(expect.objectContaining({ stage: 'clean', status: 'passed' }));
        expect(results[0].durationMs).toBeGreaterThanOrEqual(0);
        expect(results[1]).toEqual(expect.objectContaining({ stage: 'deploy-from', status: 'passed' }));
    });

    test('run should mark failed stage and skip remaining', async () => {
        mockProvider.deploy.mockRejectedValue(new Error('deploy failed'));

        try {
            await orchestrator.run(['clean', 'deploy-from', 'k8s:setup']);
        } catch (e) {
            // expected
        }

        expect(orchestrator.results).toHaveLength(3);
        expect(orchestrator.results[0]).toEqual(expect.objectContaining({ stage: 'clean', status: 'passed' }));
        expect(orchestrator.results[1]).toEqual(expect.objectContaining({ stage: 'deploy-from', status: 'failed' }));
        expect(orchestrator.results[1].error).toBe('deploy failed');
        expect(orchestrator.results[2]).toEqual(expect.objectContaining({ stage: 'k8s:setup', status: 'skipped' }));
    });

    test('run should call _writeSummaryHtml with results', async () => {
        orchestrator._writeSummaryHtml = jest.fn();
        await orchestrator.run(['clean']);

        expect(orchestrator._writeSummaryHtml).toHaveBeenCalledWith(
            expect.arrayContaining([
                expect.objectContaining({ stage: 'clean', status: 'passed' })
            ])
        );
    });

    test('run should call _writeSummaryHtml even on failure', async () => {
        mockProvider.clean.mockRejectedValue(new Error('failed'));
        orchestrator._writeSummaryHtml = jest.fn();

        try { await orchestrator.run(['clean']); } catch (_) { /* expected */ }

        expect(orchestrator._writeSummaryHtml).toHaveBeenCalledWith(
            expect.arrayContaining([
                expect.objectContaining({ stage: 'clean', status: 'failed' })
            ])
        );
    });

    test('run should print summary table even on failure', async () => {
        mockProvider.clean.mockRejectedValue(new Error('clean failed'));
        const consoleSpy = jest.spyOn(console, 'log');

        try {
            await orchestrator.run(['clean']);
        } catch (e) {
            // expected
        }

        const summaryCall = consoleSpy.mock.calls.find(c => typeof c[0] === 'string' && c[0].includes('Stage'));
        expect(summaryCall).toBeDefined();
        consoleSpy.mockRestore();
    });
});

describe('Orchestrator seed stages', () => {
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
            cleanup: jest.fn(),
            getTestEnv: jest.fn().mockReturnValue({
                AM_GATEWAY_URL: 'http://localhost:8091',
                AM_DOMAIN_DATA_PLANE_ID: 'dp1'
            })
        };
        options = {
            fromTag: '4.10.0',
            toTag: '4.11.0',
            fromVersion: '4.10',
            toVersion: '4.11',
            testDir: '/fake/test-dir'
        };
    });

    test('seed stage should call runSeed with fromVersion', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);
        orchestrator.runSeed = jest.fn();

        await orchestrator.executeStage('seed');

        expect(orchestrator.runSeed).toHaveBeenCalledWith('4.10');
    });

    test('seed-upgrade stage should call runSeed with toVersion and fromVersion', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);
        orchestrator.runSeed = jest.fn();

        await orchestrator.executeStage('seed-upgrade');

        expect(orchestrator.runSeed).toHaveBeenCalledWith('4.11', { fromVersion: '4.10' });
    });

    test('runSeed should spawn npm run migration:seed with correct args', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);

        const fakeChild = new EventEmitter();
        const spawnFn = jest.fn().mockReturnValue(fakeChild);
        orchestrator._spawn = spawnFn;

        const seedPromise = orchestrator.runSeed('4.10.0');
        fakeChild.emit('exit', 0);
        await seedPromise;

        expect(spawnFn).toHaveBeenCalledWith(
            'npm',
            ['run', 'migration:seed', '--', '--to-version', '4.10.0'],
            expect.objectContaining({
                cwd: '/fake/test-dir',
                stdio: 'inherit',
                shell: false
            })
        );
    });

    test('runSeed with fromVersion should pass --from-version arg', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);

        const fakeChild = new EventEmitter();
        const spawnFn = jest.fn().mockReturnValue(fakeChild);
        orchestrator._spawn = spawnFn;

        const seedPromise = orchestrator.runSeed('4.11', { fromVersion: '4.10' });
        fakeChild.emit('exit', 0);
        await seedPromise;

        expect(spawnFn).toHaveBeenCalledWith(
            'npm',
            ['run', 'migration:seed', '--', '--to-version', '4.11', '--from-version', '4.10'],
            expect.objectContaining({
                cwd: '/fake/test-dir',
                stdio: 'inherit',
                shell: false
            })
        );
    });

    test('runSeed should merge provider test env into spawn env', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);

        const fakeChild = new EventEmitter();
        const spawnFn = jest.fn().mockReturnValue(fakeChild);
        orchestrator._spawn = spawnFn;

        const seedPromise = orchestrator.runSeed('4.10.0');
        fakeChild.emit('exit', 0);
        await seedPromise;

        const spawnEnv = spawnFn.mock.calls[0][2].env;
        expect(spawnEnv.AM_GATEWAY_URL).toBe('http://localhost:8091');
        expect(spawnEnv.AM_DOMAIN_DATA_PLANE_ID).toBe('dp1');
    });

    test('runSeed should throw when exit code is non-zero', async () => {
        const orchestrator = new Orchestrator(mockProvider, options);

        const fakeChild = new EventEmitter();
        orchestrator._spawn = jest.fn().mockReturnValue(fakeChild);

        const seedPromise = orchestrator.runSeed('4.10.0');
        fakeChild.emit('exit', 1);

        await expect(seedPromise).rejects.toThrow('Seed process exited with code 1');
    });

    test('runSeed should throw when testDir is missing', async () => {
        const optionsNoDir = { ...options, testDir: undefined };
        const orchestrator = new Orchestrator(mockProvider, optionsNoDir);

        await expect(orchestrator.runSeed('4.10.0')).rejects.toThrow('options.testDir is required to run seed');
    });
});
