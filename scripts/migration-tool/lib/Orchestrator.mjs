import { spawn } from 'node:child_process';
import { once } from 'node:events';
import process from 'node:process';
import { $, cd, within } from 'zx';

/**
 * Orchestrator manages the migration test lifecycle and stages.
 * testDir and test env come from options and provider; it has no knowledge of a specific test suite.
 */
export class Orchestrator {
    constructor(provider, options) {
        this.provider = provider;
        this.options = options;
        this.projectRoot = process.cwd();
        this._spawn = spawn;
    }

    async run(stages, options = {}) {
        const { skipCleanup = false } = options;
        console.log(`🛠️ Running Migration Orchestration`);
        console.log(`📝 Tags: ${this.options.fromTag} -> ${this.options.toTag}`);

        this.results = stages.map(stage => ({ stage, status: 'skipped', durationMs: 0 }));
        const results = this.results;
        let failedError = null;

        try {
            for (let i = 0; i < stages.length; i++) {
                const stage = stages[i];
                console.log(`\n▶️ Stage: ${stage}`);
                const start = Date.now();
                try {
                    this._currentStage = stage;
                    await this.executeStage(stage);
                    results[i].status = 'passed';
                    results[i].durationMs = Date.now() - start;
                    console.log(`  ✓ ${stage} (${this._formatDuration(results[i].durationMs)})`);
                } catch (error) {
                    results[i].status = 'failed';
                    results[i].durationMs = Date.now() - start;
                    results[i].error = error.message;
                    console.error(`\n❌ ${stage} failed: ${error.message}`);
                    if (error.stderr) console.error(`Stderr: ${error.stderr}`);
                    failedError = error;
                    break;
                }
            }
        } finally {
            this._printSummary(results);
            if (!skipCleanup && typeof this.provider.cleanup === 'function') {
                await this.provider.cleanup();
            }
        }

        if (failedError) throw failedError;
        return results;
    }

    _formatDuration(ms) {
        if (ms < 1000) return `${ms}ms`;
        return `${(ms / 1000).toFixed(1)}s`;
    }

    _printSummary(results) {
        const maxStageLen = Math.max(...results.map(r => r.stage.length), 5);
        const header = `${'Stage'.padEnd(maxStageLen)}  Status     Duration`;
        const separator = '─'.repeat(header.length);

        console.log(`\n${separator}`);
        console.log(header);
        console.log(separator);
        for (const r of results) {
            const icon = r.status === 'passed' ? '✓' : r.status === 'failed' ? '✗' : '-';
            const status = `${icon} ${r.status}`.padEnd(10);
            const duration = r.status === 'skipped' ? '-' : this._formatDuration(r.durationMs);
            console.log(`${r.stage.padEnd(maxStageLen)}  ${status} ${duration}`);
        }
        console.log(separator);

        const failed = results.find(r => r.status === 'failed');
        if (failed) {
            console.log(`Result: FAILED at ${failed.stage}`);
        } else if (results.every(r => r.status === 'passed')) {
            console.log('Result: ALL PASSED');
        }
        console.log('');
    }

    async executeStage(stage) {
        switch (stage) {
            case 'clean':
                await this.provider.clean();
                break;
            case 'k8s:setup':
                if (typeof this.provider.setup === 'function') {
                    await this.provider.setup();
                } else {
                    console.log('⏭️ Skipping setup for current provider.');
                }
                break;
            case 'deploy-from':
                await this.provider.deploy(this.options.fromTag);
                break;
            case 'seed':
                await this.runSeed(this.options.fromVersion);
                break;
            case 'seed-upgrade':
                await this.runSeed(this.options.toVersion, { fromVersion: this.options.fromVersion });
                break;
            case 'verify-baseline':
                await this.runTests('🔍 Running baseline tests...', 'ci:management:parallel', 'specs/management');
                break;
            case 'upgrade-mapi':
                await this.provider.upgradeMapi(this.options.toTag);
                break;
            case 'verify-mapi':
                await this.runTests('🔍 Verifying MAPI upgrade...', 'ci:management:parallel', 'specs/management');
                break;
            case 'upgrade-gw':
                await this.provider.upgradeGw(this.options.toTag);
                break;
            case 'verify-all':
                await this.runTests('🔍 Final verification...', 'ci:gateway', 'specs/gateway');
                break;
            case 'downgrade-mapi':
                await this.provider.upgradeMapi(this.options.fromTag);
                break;
            case 'verify-after-downgrade-mapi':
                await this.runTests('🔍 Verifying after MAPI downgrade...', 'ci:management:parallel', 'specs/management');
                break;
            case 'downgrade-gw':
                await this.provider.upgradeGw(this.options.fromTag);
                break;
            case 'verify-after-downgrade':
                await this.runTests('🔍 Verifying after downgrade...', 'ci:gateway', 'specs/gateway');
                break;
            default:
                throw new Error(`Unknown stage: ${stage}`);
        }
    }

    async runTests(message, npmScript, specPath) {
        console.log(message);
        const testDir = this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run tests');
        }
        const filter = (this.options.testFilter || '').trim();

        const stageName = this._currentStage || 'unknown';
        const jestEnv = {
            ...process.env,
            ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {}),
            JEST_JUNIT_OUTPUT_NAME: `junit-migration-${stageName}.xml`,
            JEST_HTML_REPORT_NAME: `report-${stageName}.html`
        };
        Object.assign(process.env, jestEnv);

        if (typeof this.provider.prepareTests === 'function') {
            await this.provider.prepareTests();
        }

        await within(async () => {
            cd(testDir);

            if (filter) {
                console.log(`🔍 Filtering tests by: ${filter}`);
                const args = ['jest', '--no-cache', '--config=api/config/ci.config.js', `--testPathPattern=${filter}`];
                await this._runJestWithEnv(testDir, jestEnv, args);
            } else {
                await $`npm run ${npmScript} -- ${specPath || ''}`;
            }
        });
    }

    async _runJestWithEnv(testDir, env, args) {
        const child = spawn('npx', args, {
            cwd: testDir,
            env: { ...process.env, ...env },
            stdio: 'inherit',
            shell: false,
        });
        const [code] = await once(child, 'exit');
        if (code !== 0) {
            throw new Error(`Jest exited with code ${code}`);
        }
    }

    async runSeed(version, { fromVersion } = {}) {
        console.log(`🌱 Seeding test data for version ${version}${fromVersion ? ` (from ${fromVersion})` : ''}...`);
        const testDir = this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run seed');
        }
        const env = {
            ...process.env,
            ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {})
        };
        const args = ['run', 'migration:seed', '--', '--to-version', version];
        if (fromVersion) {
            args.push('--from-version', fromVersion);
        }
        const child = this._spawn('npm', args, {
            cwd: testDir,
            env,
            stdio: 'inherit',
            shell: false,
        });
        const [code] = await once(child, 'exit');
        if (code !== 0) {
            throw new Error(`Seed process exited with code ${code}`);
        }
    }
}
