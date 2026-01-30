import path from 'path';

/**
 * Orchestrator manages the migration test lifecycle and stages.
 */
export class Orchestrator {
    constructor(provider, options) {
        this.provider = provider;
        this.options = options;
        this.projectRoot = process.cwd();
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
            if (!skipCleanup && typeof this.provider.cleanup === 'function') {
                await this.provider.cleanup();
            }
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
            default:
                throw new Error(`Unknown stage: ${stage}`);
        }
    }

    async runTests(message, npmScript, specPath) {
        console.log(message);
        if (typeof this.provider.prepareTests === 'function') {
            await this.provider.prepareTests();
        }

        const filter = this.options.testFilter || '';

        // Use zx globals for cd and within
        await within(async () => {
            const testDir = path.join(this.projectRoot, 'gravitee-am-test');
            cd(testDir);

            if (filter) {
                console.log(`🔍 Filtering tests by: ${filter}`);
                await $`npx jest --no-cache --config=api/config/ci.config.js ${filter}`;
            } else {
                await $`npm run ${npmScript} -- ${specPath || ''}`;
            }
        });
    }
}
