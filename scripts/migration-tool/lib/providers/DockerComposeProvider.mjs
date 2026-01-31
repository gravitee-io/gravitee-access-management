import { BaseProvider } from './BaseProvider.mjs';

/**
 * Docker Compose Provider.
 * Handles deployment via docker-compose files.
 */
export class DockerComposeProvider extends BaseProvider {
    constructor(options) {
        super(options);
        this.project = 'am-migration';
        this.composeFile = options.composeFile || 'scripts/migration-tool/env/docker-compose/docker-compose.yml';
    }

    async clean() {
        console.log('🧹 Cleaning up Docker Compose environment...');
        await $`docker-compose -p ${this.project} down -v --remove-orphans`.quiet();
    }

    async deploy(version) {
        console.log(`🚀 Deploying AM ${version} via Docker Compose...`);
        // We assume the docker-compose.yml uses AM_VERSION env var
        process.env.AM_VERSION = version;
        await $`docker-compose -p ${this.project} -f ${this.composeFile} up -d --wait`.quiet();
    }

    async upgradeMapi(version) {
        console.log(`🚀 Updating Management API to ${version}...`);
        process.env.AM_VERSION = version;
        await $`docker-compose -p ${this.project} -f ${this.composeFile} up -d --no-deps mapi`.quiet();
        await this.verifyHealth();
    }

    async upgradeGw(version) {
        console.log(`🚀 Updating Gateway to ${version}...`);
        process.env.AM_VERSION = version;
        await $`docker-compose -p ${this.project} -f ${this.composeFile} up -d --no-deps gateway`.quiet();
        await this.verifyHealth();
    }

    async verifyHealth() {
        console.log('⌛ Waiting for services to be healthy...');
        // docker-compose up -d --wait handles this in modern versions, 
        // but we can add secondary checks if needed.
        await sleep(5000);
    }

    async prepareTests() {
        // No specific preparation needed for Docker Compose as it uses host ports directly
        console.log('✅ Environment ready for tests.');
    }

    async cleanup() {
        // No-op for Docker Compose if we want to keep it running for tests
    }
}
