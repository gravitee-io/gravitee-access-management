#!/usr/bin/env zx

// Usage: npx zx scripts/migration-test.mjs --from-tag 4.10.5 --to-tag latest --db-type mongodb

$.verbose = false;

const fromTag = argv['from-tag'];
const toTag = argv['to-tag'];
const dbType = argv['db-type'] || 'mongodb';
const circleToken = process.env.CIRCLECI_TOKEN;

if (!fromTag || !toTag) {
    console.error(chalk.red('Error: Missing required arguments.'));
    console.log('Usage: npx zx scripts/migration-test.mjs --from-tag <tag> --to-tag <tag> [--db-type <type>]');
    process.exit(1);
}

if (!circleToken) {
    console.error(chalk.red('Error: CIRCLECI_TOKEN environment variable is not set.'));
    process.exit(1);
}

console.log(chalk.blue(`🚀 Triggering Migration Test: ${fromTag} -> ${toTag} (${dbType})`));

const projectSlug = 'gh/gravitee-io/gravitee-access-management';
const url = `https://circleci.com/api/v2/project/${projectSlug}/pipeline`;

try {
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Circle-Token': circleToken,
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            parameters: {
                migration_test_from_tag: fromTag,
                migration_test_to_tag: toTag,
                migration_test_db_type: dbType,
            }
        }),
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`CircleCI API Request Failed: ${response.status} ${response.statusText}\n${errorText}`);
    }

    const data = await response.json();
    const pipelineId = data.id;
    const pipelineNumber = data.number;

    console.log(chalk.green(`✅ Pipeline triggered successfully!`));
    console.log(`Pipeline ID: ${pipelineId}`);
    console.log(`Pipeline Number: ${pipelineNumber}`);
    console.log(chalk.blue(`🔗 Dashboard: https://app.circleci.com/pipelines/${projectSlug}/${pipelineNumber}`));

} catch (error) {
    console.error(chalk.red(`❌ Failed to trigger pipeline:`));
    console.error(error.message);
    process.exit(1);
}
