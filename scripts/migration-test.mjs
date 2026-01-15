#!/usr/bin/env zx

/**
 * Migration Test Trigger Script
 * 
 * Triggers the CircleCI migration test workflow via API.
 * 
 * Usage:
 *   npx zx scripts/migration-test.mjs --from-tag <tag> --to-tag <tag> [--db-type <type>]
 * 
 * Environment Variables:
 *   CIRCLECI_TOKEN: Your CircleCI personal API token (Required)
 */

$.verbose = false;

// --- Argument Parsing & Validation ---

if (argv.help || argv.h) {
    console.log(`
${chalk.bold('Migration Test Trigger Script')}

Triggers the CircleCI migration test workflow.

${chalk.bold('Usage:')}
  npx zx scripts/migration-test.mjs --from-tag <tag> --to-tag <tag> [options]

${chalk.bold('Options:')}
  --from-tag <tag>  Source version tag (e.g., "4.10.5") [Required]
  --to-tag <tag>    Target version tag (e.g., "latest", "4.11.0") [Required]
  --db-type <type>  Database type (default: "mongodb")
  --help, -h        Show this help message

${chalk.bold('Environment:')}
  CIRCLECI_TOKEN    CircleCI API Token [Required]
  `);
    process.exit(0);
}

const fromTag = argv['from-tag'];
const toTag = argv['to-tag'];
const dbType = argv['db-type'] || 'mongodb';
const circleToken = process.env.CIRCLECI_TOKEN;

const errors = [];
if (!fromTag) errors.push('Missing required argument: --from-tag');
if (!toTag) errors.push('Missing required argument: --to-tag');
if (!circleToken) errors.push('Missing environment variable: CIRCLECI_TOKEN');

if (errors.length > 0) {
    console.error(chalk.red('Error: Validation Failed'));
    errors.forEach(err => console.error(chalk.red(`  - ${err}`)));
    console.log('\nUse --help for usage information.');
    process.exit(1);
}

// Basic format check for tags (optional but helpful)
// Warning only, as tags can vary
if (fromTag !== 'latest' && !/^\d+\.\d+/.test(fromTag)) {
    console.warn(chalk.yellow(`Warning: --from-tag "${fromTag}" does not look like a standard version number.`));
}

// --- Execution ---

console.log(chalk.blue(`🚀 Triggering Migration Test`));
console.log(`From: ${chalk.bold(fromTag)}`);
console.log(`To:   ${chalk.bold(toTag)}`);
console.log(`DB:   ${chalk.bold(dbType)}`);

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

    console.log(chalk.green(`\n✅ Pipeline triggered successfully!`));
    console.log(`Pipeline ID: ${pipelineId}`);
    console.log(`Pipeline Number: ${pipelineNumber}`);
    console.log(chalk.blue(`🔗 Dashboard: https://app.circleci.com/pipelines/${projectSlug}/${pipelineNumber}`));

} catch (error) {
    console.error(chalk.red(`\n❌ Failed to trigger pipeline:`));
    console.error(error.message);
    process.exit(1);
}
