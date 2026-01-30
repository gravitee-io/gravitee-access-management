// Rely on zx globals
import { Orchestrator } from './lib/Orchestrator.mjs';
import { CircleCI } from './lib/CircleCI.mjs';
import { K8sProvider } from './lib/providers/K8sProvider.mjs';
import { DockerComposeProvider } from './lib/providers/DockerComposeProvider.mjs';
import { Config } from './lib/core/Config.mjs';
import { Helm } from './lib/infra/kubernetes/Helm.mjs';
import { MongoK8sStrategy } from './lib/strategies/database/MongoK8sStrategy.mjs';

/**
 * Migration Test Tool - Refactored ES Module Version
 */

const args = argv;
const command = args._[0];

const options = {
    fromTag: args['from-tag'] || '4.10.0',
    toTag: args['to-tag'] || 'latest',
    dbType: args['db-type'] || 'mongodb',
    providerName: args['provider'] || 'docker-compose',
    stage: args['stage'],
    testFilter: args['test-filter'],
    token: process.env.CIRCLECI_TOKEN
};

// 1. Initialize Infrastructure Provider
let provider;
switch (options.providerName) {
    case 'k8s':
        const helm = new Helm({ namespace: Config.k8s.namespace });
        let databaseStrategy;

        if (options.dbType === 'mongodb') {
            databaseStrategy = new MongoK8sStrategy({
                helm,
                namespace: Config.k8s.namespace,
                config: Config
            });
        } else {
            console.error(`❌ Unsupported database type for K8s: ${options.dbType}`);
            process.exit(1);
        }

        provider = new K8sProvider({
            namespace: Config.k8s.namespace,
            clusterName: 'am-migration',
            helm,
            databaseStrategy
        });
        break;
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
        'verify-all'
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
    console.log('  run        Run migration orchestration');
    console.log('\nOptions:');
    console.log('  --from-tag <tag>  Initial version (default: 4.10.5)');
    console.log('  --to-tag <tag>    Target version (default: latest)');
    console.log('  --db-type <type>  Database: mongodb or psql (default: mongodb)');
    console.log('  --provider <name> Infrastructure provider: docker-compose or k8s (default: docker-compose)');
    console.log('  --stage <name>    Specific stage to run');
    console.log('  --test-filter <pattern>  Specific test file or regex to run');
    console.log('\nStages:');
    console.log('  clean, k8s:setup, deploy-from, verify-baseline, upgrade-mapi, verify-mapi, upgrade-gw, verify-all');
}
