import { spawn } from 'node:child_process';
import { once } from 'node:events';

/**
 * Orchestrator manages the migration test lifecycle and stages.
 * testDir and test env come from options and provider; it has no knowledge of a specific test suite.
 */
export class Orchestrator {
    constructor(provider, options, seedWorktree = null) {
        this.provider = provider;
        this.options = options;
        this.projectRoot = process.cwd();
        // Optional: when set (and options.seedFromWorktree), seed stages run from a git worktree of
        // the from/to tag so each version seeds with its own SDK + scripts. null => current checkout.
        this.seedWorktree = seedWorktree;
        // Timestamp of the last log scan; each verify scan only covers logs since then so the
        // summary stays focused and the same ERROR lines aren't re-printed every stage.
        this._lastScanAt = Date.now();
    }

    async run(stages, options = {}) {
        const { skipCleanup = false } = options;
        console.log(`🛠️ Running Migration Orchestration`);
        console.log(`📝 Tags: ${this.options.fromTag} -> ${this.options.toTag}`);

        try {
            for (const stage of stages) {
                console.log(`\n▶️ Stage: ${stage}`);
                await this.executeStage(stage);
            }
        } catch (error) {
            console.error(`\n❌ Error during orchestration: ${error.message}`);
            if (error.stderr) console.error(`Stderr: ${error.stderr}`);
            throw error;
        } finally {
            // Final log sweep before cleanup so an error mid-pipeline still gets surfaced.
            await this.scanProviderLogs();
            if (!skipCleanup && typeof this.provider.cleanup === 'function') {
                await this.provider.cleanup();
            }
            if (!skipCleanup && this.seedWorktree) {
                await this.seedWorktree.cleanup();
            }
        }
    }

    /**
     * Scan the provider's component logs for ERROR lines (report-only). No-op for providers
     * that don't implement scanLogsForErrors (e.g. DockerComposeProvider). Covers logs since
     * the previous scan so each call's summary stays focused.
     */
    async scanProviderLogs() {
        if (typeof this.provider.scanLogsForErrors !== 'function') return;
        const elapsedSec = Math.max(1, Math.ceil((Date.now() - this._lastScanAt) / 1000));
        this._lastScanAt = Date.now();
        try {
            await this.provider.scanLogsForErrors({ since: `${elapsedSec}s` });
        } catch (e) {
            console.warn(`⚠️  Log scan skipped: ${e.message}`);
        }
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
            case 'seed-alpha':
                await this.seedStage(this.options.fromTag, 'alpha');
                break;
            case 'upgrade-mapi':
                await this.provider.upgradeMapi(this.options.toTag);
                break;
            case 'upgrade-gw':
                await this.provider.upgradeGw(this.options.toTag);
                break;
            case 'seed-upgrade':
            case 'seed-beta':
                await this.seedStage(this.options.toTag, 'beta');
                break;
            case 'downgrade-mapi':
                await this.provider.upgradeMapi(this.options.fromTag);
                break;
            case 'downgrade-gw':
                await this.provider.upgradeGw(this.options.fromTag);
                break;
            // Structured verification: the "alpha" channel is the --from-tag seeded domain; the "beta"
            // channel is the --to-tag seeded domain. The same verify runs at several points in the
            // pipeline (post-mapi-upgrade, post-gw-upgrade, post-downgrade) — pipeline order says which.
            case 'verify-alpha':
                await this.runTests('🔍 Verifying alpha domain...', 'ci:migration', 'specs/migration', 'alpha');
                break;
            case 'verify-beta':
                await this.runTests('🔍 Verifying beta domain...', 'ci:migration', 'specs/migration', 'beta');
                break;
            // Generic verify stage for ad-hoc / single-stage debugging; the asserted channel comes from
            // --test-label (falling back to "alpha"). Not part of the default pipeline.
            case 'verify':
                await this.runTests('🔍 Running migration verification...', 'ci:migration', 'specs/migration');
                break;
            default:
                throw new Error(`Unknown stage: ${stage}`);
        }
    }

    async runTests(message, npmScript, specPath, label) {
        console.log(message);
        const testDir = this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run tests');
        }
        const filter = (this.options.testFilter || '').trim();

        const jestEnv = {
            ...process.env,
            ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {}),
            AM_MIGRATION_TEST_LABEL: label || this.getMigrationTestLabel()
        };
        Object.assign(process.env, jestEnv);

        if (typeof this.provider.prepareTests === 'function') {
            await this.provider.prepareTests();
        }

        await within(async () => {
            cd(testDir);

            if (filter) {
                console.log(`🔍 Filtering tests by: ${filter}`);
                const args = ['jest', '--no-cache', '--config=api/config/ci.config.js', `--testPathPatterns=${filter}`];
                await this._runJestWithEnv(testDir, jestEnv, args);
            } else {
                await $`npm run ${npmScript} -- ${specPath || ''}`;
            }
        });

        // After the verify stage, surface any ERROR lines logged by Gateway/MAPI (report-only).
        await this.scanProviderLogs();
    }

    /**
     * Seed one version under a channel label. When a SeedWorktree is configured (and enabled), the
     * seed runs from a worktree of `tag` so it uses that version's own SDK + scripts; otherwise (or
     * when the tag predates the seeding framework) it falls back to the current checkout.
     */
    async seedStage(tag, label) {
        const args = ['--version', toMinorVersion(tag), '--label', label];
        let seedDir = null;
        if (this.seedWorktree && this.options.seedFromWorktree) {
            seedDir = await this.seedWorktree.resolveSeedDir(tag);
        }
        if (seedDir) {
            console.log(`🌱 Seeding ${label} from worktree: ${seedDir}`);
            await this.runSeed(args, seedDir);
        } else {
            await this.runSeed(args);
        }
    }

    async runSeed(args, seedDir) {
        const testDir = seedDir || this.options.testDir;
        if (!testDir) {
            throw new Error('options.testDir is required to run migration seed');
        }
        const env = { ...process.env, ...(typeof this.provider.getTestEnv === 'function' ? this.provider.getTestEnv() : {}) };
        // Ensure the MAPI/gateway port-forwards are up so the seed can reach localhost:8093.
        // Mirrors runTests(); idempotent when tunnels are already started (e.g. after deploy-from).
        if (typeof this.provider.prepareTests === 'function') {
            await this.provider.prepareTests();
        }
        const child = spawn('npm', ['run', 'migration:seed', '--', ...args], {
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

    getMigrationTestLabel() {
        return this.options.testLabel || 'alpha';
    }
}

function toMinorVersion(version) {
    const match = String(version).match(/^(\d+)\.(\d+)/);
    if (!match) {
        throw new Error(`Invalid AM version: ${version}`);
    }
    return `${match[1]}.${match[2]}`;
}
