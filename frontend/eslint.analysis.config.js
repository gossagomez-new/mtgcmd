import tseslint from "typescript-eslint";
import a11y from "eslint-plugin-jsx-a11y";
import baseline from "./eslint.config.js";

function advisory(rules) {
  return Object.fromEntries(
    Object.entries(rules).map(([name, value]) => {
      const [severity, ...options] = Array.isArray(value) ? value : [value];
      return [
        name,
        [severity === "off" || severity === 0 ? "off" : "warn", ...options],
      ];
    }),
  );
}

const typedRules = tseslint.configs.recommendedTypeCheckedOnly.at(-1).rules;

export default tseslint.config(
  ...baseline,
  {
    files: ["src/**/*.{ts,tsx}", "tests/**/*.{ts,tsx}", "*.ts"],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: advisory(typedRules),
  },
  {
    files: ["src/**/*.tsx"],
    plugins: { "jsx-a11y": a11y },
    rules: advisory(a11y.flatConfigs.recommended.rules),
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/**/*.test.{ts,tsx}"],
    rules: {
      complexity: ["warn", { max: 10 }],
      "max-depth": ["warn", 4],
      "max-lines-per-function": [
        "warn",
        { max: 80, skipBlankLines: true, skipComments: true },
      ],
    },
  },
);
