// Rely on zx globals

/**
 * CircleCI Helper for triggering migration test pipelines.
 */
export class CircleCI {
    constructor(token) {
        this.token = token;
        this.projectSlug = 'gh/gravitee-io/gravitee-access-management';
    }

    async triggerPipeline(options) {
        const branch = options.branch || await this.getCurrentBranch();
        console.log(`🚀 Triggering CircleCI pipeline on branch: ${branch}`);

        const url = `https://circleci.com/api/v2/project/${this.projectSlug}/pipeline`;
        const body = {
            branch: branch,
            parameters: {
                migration_test_from_tag: options.fromTag,
                migration_test_to_tag: options.toTag,
                migration_test_db_type: options.dbType,
                migration_test_provider: options.provider,
                ...(options.testFilter != null && options.testFilter !== '' ? { migration_test_filter: options.testFilter } : {}),
                ...(options.withDowngrade ? { migration_test_with_downgrade: 'true' } : {})
            }
        };

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Circle-Token': this.token
            },
            body: JSON.stringify(body)
        });

        const data = await response.json();
        if (response.ok) {
            console.log(`✅ Pipeline triggered: ${data.id}`);
            console.log(`🔗 Monitor: https://app.circleci.com/pipelines/${this.projectSlug}?branch=${branch}`);
        } else {
            console.error('❌ Failed to trigger pipeline:', data);
            process.exit(1);
        }
    }

    async getCurrentBranch() {
        const branch = await $`git rev-parse --abbrev-ref HEAD`.quiet();
        return branch.stdout.trim();
    }
}
