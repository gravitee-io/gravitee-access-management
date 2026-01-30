/**
 * Database Strategy Interface
 * 
 * Defines the contract for database deployment strategies.
 * All properties and methods must be implemented by concrete strategies.
 */
export class DatabaseStrategy {
    constructor(config) {
        this.config = config;
    }

    /**
     * Deploys the database.
     * @returns {Promise<void>}
     */
    async deploy() {
        throw new Error('deploy() must be implemented by concrete strategy');
    }

    /**
     * Cleans up the database resources.
     * @returns {Promise<void>}
     */
    async clean() {
        throw new Error('clean() must be implemented by concrete strategy');
    }

    /**
     * Waits for the database to be ready.
     * @returns {Promise<void>}
     */
    async waitForReady() {
        throw new Error('waitForReady() must be implemented by concrete strategy');
    }
}
