// Rely on zx globals for $, cd, within, etc.
import { parseArgs } from 'node:util';
import { readFileSync, existsSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';

// Load .env from repo root and scripts/ so CIRCLECI_TOKEN etc. are available (e.g. for trigger)
const cwd = process.cwd();
for (const rel of ['.env', 'scripts/.env']) {
    const path = join(cwd, rel);
    if (existsSync(path)) {
        const content = readFileSync(path, 'utf8');
        for (const line of content.split('\n')) {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#')) {
                const eq = trimmed.indexOf('=');
                if (eq > 0) {
                    const key = trimmed.slice(0, eq).trim();
                    let value = trimmed.slice(eq + 1).trim();
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.slice(1, -1);
                    }
                    if (!process.env[key]) process.env[key] = value;
                }
            }
        }
    }
}

import { Orchestrator } from './lib/Orchestrator.mjs';
import { CircleCI } from './lib/CircleCI.mjs';
import { K8sProvider } from './lib/providers/K8sProvider.mjs';
import { DockerComposeProvider } from './lib/providers/DockerComposeProvider.mjs';
import { Config } from './lib/core/Config.mjs';
import { resolveFloatingTag } from './lib/core/VersionResolver.mjs';
import { SeedWorktree } from './lib/core/SeedWorktree.mjs';
import { Helm } from './lib/infra/kubernetes/Helm.mjs';
import { Kubectl } from './lib/infra/kubernetes/Kubectl.mjs';
import { MongoK8sStrategy } from './lib/strategies/database/MongoK8sStrategy.mjs';
import { PostgresK8sStrategy } from './lib/strategies/database/PostgresK8sStrategy.mjs';

/**
 * Migration Test Tool - Refactored ES Module Version
 * CLI options parsed via Node util.parseArgs (single source of truth).
 * When run under zx, process.argv may not include invocation args; fall back to zx global argv.
 */

const argvSlice = process.argv.slice(2);
const parsed = parseArgs({
    args: argvSlice,
    options: {
        'from-tag': { type: 'string' },
        'to-tag': { type: 'string' },
        'db-type': { type: 'string' },
        provider: { type: 'string' },
        namespace: { type: 'string' },
        registry: { type: 'string' },
        stage: { type: 'string' },
        'test-filter': { type: 'string' },
        'test-label': { type: 'string' },
        'test-dir': { type: 'string' },
        'with-downgrade': { type: 'boolean', default: false },
        'no-seed-worktree': { type: 'boolean', default: false },
        'keep-worktrees': { type: 'boolean', default: false },
    },
    allowPositionals: true,
    strict: false,
});

// When launcher runs script via zx, first positional can be the script path (index.mjs); command is then the next arg
const zxArgv = typeof argv === 'undefined' ? null : argv;
const knownCommands = ['run', 'setup', 'teardown', 'trigger'];
const positionals = parsed.positionals;
const firstIsCommand = positionals.length > 0 && knownCommands.includes(positionals[0]);
const firstIsScriptPath = positionals.length > 0 && (positionals[0].endsWith('.mjs') || positionals[0].includes('migration-tool'));

let command;
let raw;
if (firstIsCommand) {
    command = positionals[0];
    raw = parsed.values;
} else if (firstIsScriptPath && positionals.length > 1) {
    command = positionals[1];
    raw = parsed.values;
} else if (positionals.length === 0 && zxArgv?._?.length > 0) {
    command = zxArgv._[0];
    raw = {
        'from-tag': zxArgv['from-tag'],
        'to-tag': zxArgv['to-tag'],
        'db-type': zxArgv['db-type'],
        provider: zxArgv.provider,
        namespace: zxArgv.namespace,
        registry: zxArgv.registry,
        stage: zxArgv.stage,
        'test-filter': zxArgv['test-filter'],
        'test-label': zxArgv['test-label'],
        'test-dir': zxArgv['test-dir'],
        'with-downgrade': zxArgv['with-downgrade'],
        'no-seed-worktree': zxArgv['no-seed-worktree'],
        'keep-worktrees': zxArgv['keep-worktrees'],
    };
} else {
    command = positionals.length > 0 ? positionals[0] : undefined;
    raw = parsed.values;
}

const testDirRaw = raw['test-dir'] ?? Config.test.dir;
const testDir = testDirRaw ? (isAbsolute(testDirRaw) ? testDirRaw : join(cwd, testDirRaw)) : join(cwd, Config.test.dir);

const options = {
    fromTag: raw['from-tag'] ?? '4.10.0',
    toTag: raw['to-tag'] ?? 'latest',
    dbType: raw['db-type'] ?? 'mongodb',
    providerName: raw.provider ?? 'docker-compose',
    // K8s namespace: --namespace overrides AM_K8S_NAMESPACE, which overrides the 'gravitee-am' default.
    namespace: raw.namespace ?? Config.k8s.namespace,
    registry: raw.registry ?? undefined,
    stage: raw.stage ?? undefined,
    testFilter: (raw['test-filter'] ?? '').trim() || undefined,
    testLabel: (raw['test-label'] ?? '').trim() || undefined,
    testDir,
    withDowngrade: raw['with-downgrade'] === true,
    // Seed each tag from a git worktree of that tag (its own SDK + scripts); on by default.
    seedFromWorktree: raw['no-seed-worktree'] !== true,
    keepWorktrees: raw['keep-worktrees'] === true,
    token: process.env.CIRCLECI_TOKEN,
};

// Resolve floating tags (latest, 4, 4.11, ...) to the concrete X.Y.Z they point to, so deploy,
// seeding, worktrees and the CircleCI trigger all run against a real version. Fails fast.
// Only runs for commands that actually consume fromTag/toTag — teardown only uses the provider
// and must remain offline-tolerant. The help/no-command path must also stay network-free.
const COMMANDS_NEEDING_TAGS = ['run', 'setup', 'trigger'];
if (command && COMMANDS_NEEDING_TAGS.includes(command)) {
    for (const key of ['fromTag', 'toTag']) {
        const tag = options[key];
        try {
            const resolved = await resolveFloatingTag(tag);
            if (resolved !== tag) {
                console.log(`🔖 Resolved --${key === 'fromTag' ? 'from' : 'to'}-tag ${tag} → ${resolved}`);
                options[key] = resolved;
            }
        } catch (e) {
            console.error(`❌ ${e.message}`);
            process.exit(1);
        }
    }
}

// 1. Initialize Infrastructure Provider
let provider;
switch (options.providerName) {
    case 'k8s': {
        const namespace = options.namespace;
        const helm = new Helm({ namespace });
        const kubectl = new Kubectl({ namespace });
        let databaseStrategy;

        if (options.dbType === 'mongodb') {
            databaseStrategy = new MongoK8sStrategy({
                helm,
                namespace,
                config: Config,
                kubectl
            });
        } else if (options.dbType === 'postgres') {
            databaseStrategy = new PostgresK8sStrategy({
                helm,
                namespace,
                config: Config,
                kubectl
            });
        } else {
            console.error(`❌ Unsupported database type for K8s: ${options.dbType}. Use mongodb or postgres.`);
            process.exit(1);
        }

        const releases = Config.getK8sReleases(options.dbType);
        provider = new K8sProvider({
            namespace,
            clusterName: 'am-migration',
            helm,
            kubectl,
            databaseStrategy,
            releases,
            registry: options.registry
        });
        break;
    }
    case 'docker-compose':
        provider = new DockerComposeProvider({
            project: 'am-migration'
        });
        break;
    default:
        console.error(`❌ Unknown provider: ${options.providerName}`);
        process.exit(1);
}

// 2. Command pattern: each command is an async (ctx) => exitCode
const commands = {
    async trigger({ options }) {
        if (!options.token) {
            console.error('❌ CIRCLECI_TOKEN environment variable is required for trigger command');
            return 1;
        }
        const ci = new CircleCI(options.token);
        await ci.triggerPipeline({
            fromTag: options.fromTag,
            toTag: options.toTag,
            dbType: options.dbType,
            provider: options.providerName,
            testFilter: options.testFilter,
            withDowngrade: options.withDowngrade,
            registry: options.registry
        });
        return 0;
    },

    async setup({ provider, options }) {
        if (typeof provider.ensureCluster === 'function') {
            await provider.ensureCluster();
        }
        const orchestrator = new Orchestrator(provider, options);
        try {
            await orchestrator.run(['clean', 'k8s:setup', 'deploy-from'], { skipCleanup: true });
            console.log('\n✅ Environment setup complete. Ready for manual testing.');
            return 0;
        } catch (e) {
            return 1;
        }
    },

    async teardown({ provider }) {
        try {
            await provider.clean();
        } catch (e) {
            console.log('⚠️  Clean skipped (cluster unreachable):', e.message);
        }
        if (typeof provider.teardownCluster === 'function') {
            await provider.teardownCluster();
        }
        console.log('✅ Teardown complete.');
        return 0;
    },

    async run({ provider, options }) {
        if (typeof provider.ensureCluster === 'function') {
            await provider.ensureCluster();
        }
        const seedWorktree = options.seedFromWorktree
            ? await SeedWorktree.create({ keep: options.keepWorktrees })
            : null;
        const orchestrator = new Orchestrator(provider, options, seedWorktree);
        const allStages = [
            'clean',
            'k8s:setup',
            'deploy-from',
            'seed-alpha',
            'verify-alpha',
            'upgrade-mapi',
            'seed-beta',
            'verify-alpha',
            'verify-beta',
            'upgrade-gw',
            'verify-alpha',
            'verify-beta',
            ...(options.withDowngrade
                ? ['downgrade-gw', 'verify-alpha', 'verify-beta', 'downgrade-mapi', 'verify-alpha', 'verify-beta']
                : [])
        ];
        const stagesToRun = options.stage ? [options.stage] : allStages;
        try {
            await orchestrator.run(stagesToRun);
            return 0;
        } catch (e) {
            return 1;
        }
    }
};

const ctx = { provider, options };
const handler = commands[command];
if (handler) {
    const code = await handler(ctx);
    process.exit(code ?? 0);
}
printHelp();
process.exit(0);

function printHelp() {
    console.log('Usage: migration-test [command] [options]');
    console.log('\nCommands:');
    console.log('  trigger    Trigger CircleCI pipeline');
    console.log('  setup      One-time environment setup (Clean + K8s Setup + Deploy From)');
    console.log('  teardown   Remove resources and delete Kind cluster (k8s)');
    console.log('  run        Run migration orchestration');
    console.log('\nOptions (use --key <value> or --key=<value>):');
    console.log('  --from-tag <tag>   Initial AM version (default: 4.10.0)');
    console.log('  --to-tag <tag>     Target AM version (default: latest). Floating tags (latest, 4, 4.11) are resolved to a concrete X.Y.Z.');
    console.log('  --db-type <type>  Database: mongodb or postgres (default: mongodb)');
    console.log('  --provider <name> Infrastructure: docker-compose or k8s (default: docker-compose)');
    console.log('  --namespace <ns>  K8s namespace to deploy into (default: gravitee-am, or AM_K8S_NAMESPACE)');
    console.log('  --registry <host> Override AM image repositories, e.g. graviteeio.azurecr.io');
    console.log('  --stage <name>    Run only this stage');
    console.log('  --test-filter <path>  Run only Jest tests matching path (e.g. specs/gateway/refresh-token.jest.spec.ts)');
    console.log('  --test-label <alpha|beta>  Seed channel asserted by ad-hoc migration Jest tests (default: alpha)');
    console.log('  --test-dir <path>   Test suite directory (default from config; use full path to override)');
    console.log('  --with-downgrade  After the full upgrade, downgrade back to from-tag and re-verify');
    console.log('  --no-seed-worktree  Seed from the current checkout for all tags (disable per-tag worktree seeding)');
    console.log('  --keep-worktrees  Do not delete the .worktrees/seed-<ref> dirs after the run (debugging)');
    console.log('\nSeeding source:');
    console.log('  By default each tag is seeded from a git worktree of that tag (its own SDK + scripts),');
    console.log('  so alpha reflects --from-tag and beta reflects --to-tag. AM itself runs from the published');
    console.log('  Docker image; the worktree only provides the TS seed tooling (npm ci once per worktree).');
    console.log('  Going-forward: tags predating the migration-seeding framework transparently fall back to');
    console.log('  the current checkout. Use --no-seed-worktree to force current-checkout seeding everywhere.');
    console.log('\nStages (default pipeline order):');
    console.log('  clean, k8s:setup, deploy-from, seed-alpha, verify-alpha, upgrade-mapi, seed-beta,');
    console.log('  verify-alpha, verify-beta, upgrade-gw, verify-alpha, verify-beta');
    console.log('  (+ with --with-downgrade: downgrade-gw, verify-alpha, verify-beta, downgrade-mapi, verify-alpha, verify-beta)');
    console.log('  alpha = the --from-tag seeded domain; beta = the --to-tag seeded domain');
    console.log('  aliases: seed → seed-alpha, seed-upgrade → seed-beta');
    console.log('  Ad-hoc single-stage verify (honors --test-label): verify');
    console.log('  Note: floating tags (latest, 4, 4.11) on --from-tag/--to-tag are resolved to the concrete X.Y.Z they point to before the run.');
}
