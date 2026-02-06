export default {
    testEnvironment: 'node',
    transform: {},
    cacheDirectory: '/tmp/am-migration-jest-cache',
    testMatch: ['**/?(*.)+(spec|test).?([mc])[jt]s?(x)'],
    moduleFileExtensions: ['js', 'mjs', 'cjs', 'ts', 'jsx', 'tsx', 'json', 'node'],
    moduleNameMapper: {
        '^(\\.{1,2}/.*)\\.js$': '$1',
    },
};
