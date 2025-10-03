const { join } = require('path');

module.exports = {
  root: true,
  ignorePatterns: ['projects/**/*', 'src/app/domain/settings/openfga/openfga.component.ts', 'src/app/domain/settings/openfga/openfga.component.spec.ts', 'src/app/services/openfga.service.ts'],
  plugins: ['eslint-plugin-import', 'rxjs'],
  overrides: [
    {
      files: ['*.ts'],
      parser: '@typescript-eslint/parser',
      parserOptions: {
        ecmaVersion: 2019,
        sourceType: 'module',
        project: join(__dirname, './tsconfig.json'),
      },
      extends: [
        'eslint:recommended',
        'plugin:@typescript-eslint/recommended',
        'plugin:@angular-eslint/recommended',
        'plugin:@angular-eslint/template/process-inline-templates',
        'plugin:import/typescript',
        'plugin:rxjs/recommended',
        'plugin:prettier/recommended',
      ],
      rules: {
        '@angular-eslint/prefer-standalone': 'off',
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-unused-vars': [
          'error',
          { ignoreRestSiblings: true, argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
        ],
        'rxjs/no-subject-unsubscribe': ['off'],
        'import/order': [
          'error',
          {
            groups: ['external', 'builtin', 'internal', 'object', 'type', 'parent', 'index', 'sibling'],
            'newlines-between': 'always',
          },
        ],
      },
    },
    {
      files: ['*.html'],
      extends: ['plugin:@angular-eslint/template/recommended'],
      rules: {},
    },
  ],
};
