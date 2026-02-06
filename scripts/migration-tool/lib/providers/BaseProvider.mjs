export class BaseProvider {
    constructor(options) {
        this.options = options;
    }

    async clean() {
        throw new Error('clean() not implemented');
    }

    async deploy(version) {
        throw new Error('deploy() not implemented');
    }

    async upgradeMapi(version) {
        throw new Error('upgradeMapi() not implemented');
    }

    async upgradeGw(version) {
        throw new Error('upgradeGw() not implemented');
    }

    async prepareTests() {}

    async cleanup() {}
}
