import js from '@eslint/js';
import compat from 'eslint-plugin-compat';
import esx from 'eslint-plugin-es-x';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

// Guardarraíles de compatibilidad (E6, INC-001), con dos herramientas porque cubren cosas distintas:
// - es-x `restrict-to-es2023`: prohíbe built-ins y sintaxis de ECMAScript 2024 en adelante, la línea
//   base de Safari 17.0. Detecta Promise.withResolvers y Object.groupBy (Safari 17.4).
// - compat/compat: APIs web (DOM, fetch, etc.) contra el browserslist de package.json. NO revisa los
//   built-ins de ECMAScript: por sí solo no habría detectado el defecto de E6.
export default [
  { ignores: ['dist/**', 'coverage/**', 'test-results/**', 'playwright-report/**'] },
  js.configs.recommended,
  {
    files: ['src/**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: { compat, 'react-hooks': reactHooks },
    rules: {
      'compat/compat': 'error',
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
  { ...esx.configs['flat/restrict-to-es2023'], files: ['src/**/*.{js,jsx}'], ignores: ['src/**/*.test.{js,jsx}'] },
  {
    files: ['src/**/*.test.{js,jsx}', 'src/test-setup.js', 'e2e/**', '*.config.js'],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
    rules: { 'compat/compat': 'off' },
  },
];
