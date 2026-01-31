// Rely on zx globals for $, cd, within, etc.
import { parseArgs } from 'node:util';
import { Orchestrator } from './lib/Orchestrator.mjs';
import { CircleCI } from './lib/CircleCI.mjs';
import { K8sProvider } from './lib/providers/K8sProvider.mjs';
import { DockerComposeProvider } from './lib/providers/DockerComposeProvider.mjs';
import { Config } from './lib/core/Config.mjs';
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
        stage: { type: 'string' },
        'test-filter': { type: 'string' },
        'with-downgrade': { type: 'boolean', default: false },
    },
    allowPositionals: true,
    strict: false,
});

// When launcher runs script via zx, first positional can be the script path (index.mjs); command is then the next arg
const zxArgv = typeof argv === 'undefined' ? null : argv;
const knownCommands = ['run', 'setup', 'trigger'];
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
        stage: zxArgv.stage,
        'test-filter': zxArgv['test-filter'],
        'with-downgrade': zxArgv['with-downgrade'],
    };
} else {
    command = positionals.length > 0 ? positionals[0] : undefined;
    raw = parsed.values;
}

const options = {
    fromTag: raw['from-tag'] ?? '4.10.0',
    toTag: raw['to-tag'] ?? 'latest',
    dbType: raw['db-type'] ?? 'mongodb',
    providerName: raw.provider ?? 'docker-compose',
    stage: raw.stage ?? undefined,
    testFilter: (raw['test-filter'] ?? '').trim() || undefined,
    withDowngrade: raw['with-downgrade'] === true,
    token: process.env.CIRCLECI_TOKEN,
};

// 1. Initialize Infrastructure Provider
let provider;
switch (options.providerName) {
    case 'k8s': {
        const namespace = Config.k8s.namespace;
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

        // Multi-dataplane: 1 API + 2 Gateways (am-mapi, am-gateway-dp1, am-gateway-dp2)
        const releases = options.dbType === 'postgres'
            ? [
                { name: 'am-mapi', valuesPath: Config.k8s.valuesPathPostgresMapi, component: 'mapi' },
                { name: 'am-gateway-dp1', valuesPath: Config.k8s.valuesPathPostgresGatewayDp1, component: 'gateway' },
                { name: 'am-gateway-dp2', valuesPath: Config.k8s.valuesPathPostgresGatewayDp2, component: 'gateway' }
            ]
            : [
                { name: 'am-mapi', valuesPath: Config.k8s.valuesPathMongoMapi, component: 'mapi' },
                { name: 'am-gateway-dp1', valuesPath: Config.k8s.valuesPathMongoGatewayDp1, component: 'gateway' },
                { name: 'am-gateway-dp2', valuesPath: Config.k8s.valuesPathMongoGatewayDp2, component: 'gateway' }
            ];
        provider = new K8sProvider({
            namespace,
            clusterName: 'am-migration',
            helm,
            kubectl,
            databaseStrategy,
            releases
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

// 2. Main Entry Point Logic
if (command === 'trigger') {
    if (!options.token) {
        console.error('❌ CIRCLECI_TOKEN environment variable is required for trigger command');
        process.exit(1);
    }
    const ci = new CircleCI(options.token);
    await ci.triggerPipeline({
        fromTag: options.fromTag,
        toTag: options.toTag,
        dbType: options.dbType,
        provider: options.providerName
    });
} else if (command === 'setup') {
    const orchestrator = new Orchestrator(provider, options);
    try {
        await orchestrator.run(['clean', 'k8s:setup', 'deploy-from'], { skipCleanup: true });
        console.log('\n✅ Environment setup complete. Ready for manual testing.');
        process.exit(0);
    } catch (e) {
        process.exit(1);
    }
} else if (command === 'run') {
    const orchestrator = new Orchestrator(provider, options);

    const allStages = [
        'clean',
        'k8s:setup',
        'deploy-from',
        'verify-baseline',
        'upgrade-mapi',
        'verify-mapi',
        'upgrade-gw',
        'verify-all',
        ...(options.withDowngrade
            ? ['downgrade-mapi', 'verify-after-downgrade-mapi', 'downgrade-gw', 'verify-after-downgrade']
            : [])
    ];

    const stagesToRun = options.stage ? [options.stage] : allStages;
    try {
        await orchestrator.run(stagesToRun);
    } catch (e) {
        process.exit(1);
    }
} else {
    printHelp();
}

function printHelp() {
    console.log('Usage: migration-test [command] [options]');
    console.log('\nCommands:');
    console.log('  trigger    Trigger CircleCI pipeline');
    console.log('  setup      One-time environment setup (Clean + K8s Setup + Deploy From)');
    console.log('  run        Run migration orchestration');
    console.log('\nOptions (use --key <value> or --key=<value>):');
    console.log('  --from-tag <tag>   Initial AM version (default: 4.10.0)');
    console.log('  --to-tag <tag>     Target AM version (default: latest)');
    console.log('  --db-type <type>  Database: mongodb or postgres (default: mongodb)');
    console.log('  --provider <name> Infrastructure: docker-compose or k8s (default: docker-compose)');
    console.log('  --stage <name>    Run only this stage');
    console.log('  --test-filter <path>  Run only Jest tests matching path (e.g. specs/gateway/refresh-token.jest.spec.ts)');
    console.log('  --with-downgrade  After verify-all, downgrade back to from-tag and verify');
    console.log('\nStages:');
    console.log('  clean, k8s:setup, deploy-from, verify-baseline, upgrade-mapi, verify-mapi, upgrade-gw, verify-all');
    console.log('  (+ with --with-downgrade: downgrade-mapi, verify-after-downgrade-mapi, downgrade-gw, verify-after-downgrade)');
}
