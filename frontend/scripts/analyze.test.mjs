import test from "node:test";
import assert from "node:assert/strict";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawnSync } from "node:child_process";

const frontend = fileURLToPath(new URL("..", import.meta.url));
const sourceFiles = [
  "scripts/analyze.mjs",
  "eslint.config.js",
  "eslint.analysis.config.js",
  "knip.json",
];

function fixture(t, source = "export const value = 1; console.log(value);") {
  for (const name of sourceFiles) {
    assert.ok(existsSync(join(frontend, name)), `${name} is not implemented`);
  }
  const root = mkdtempSync(join(tmpdir(), "mtgcmd analysis "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  for (const name of sourceFiles) {
    mkdirSync(dirname(join(root, name)), { recursive: true });
    copyFileSync(join(frontend, name), join(root, name));
  }
  symlinkSync(
    join(frontend, "node_modules"),
    join(root, "node_modules"),
    "dir",
  );
  const dependencies = JSON.parse(
    readFileSync(join(frontend, "package.json")),
  ).devDependencies;
  writeFileSync(
    join(root, "package.json"),
    JSON.stringify({
      name: "analysis-fixture",
      type: "module",
      scripts: { analyze: "node scripts/analyze.mjs", lint: "eslint ." },
      devDependencies: Object.fromEntries(
        [
          "eslint",
          "typescript-eslint",
          "eslint-plugin-react-hooks",
          "eslint-plugin-jsx-a11y",
          "knip",
        ].map((name) => [name, dependencies[name]]),
      ),
    }),
  );
  writeFileSync(
    join(root, "tsconfig.json"),
    JSON.stringify({
      compilerOptions: {
        target: "ES2022",
        module: "ESNext",
        moduleResolution: "Bundler",
        jsx: "react-jsx",
        strict: true,
      },
      include: ["src", "tests"],
    }),
  );
  mkdirSync(join(root, "src"));
  writeFileSync(join(root, "src/main.tsx"), source);
  return {
    root,
    reports: join(root, "reports/static-analysis"),
    write(name, content) {
      mkdirSync(dirname(join(root, name)), { recursive: true });
      writeFileSync(join(root, name), content);
    },
    run(overrides = {}) {
      return spawnSync(process.execPath, [join(root, "scripts/analyze.mjs")], {
        cwd: tmpdir(),
        encoding: "utf8",
        timeout: 60000,
        env: {
          ...process.env,
          NODE_TEST_CONTEXT: undefined,
          NO_COLOR: "1",
          ...overrides,
        },
      });
    },
    read(name) {
      return readFileSync(join(root, "reports/static-analysis", name), "utf8");
    },
  };
}

test("clean analysis produces complete reports from outside the project", (t) => {
  const f = fixture(t);
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  for (const file of [
    "eslint.json",
    "eslint.txt",
    "knip.json",
    "knip.txt",
    "summary.json",
    "summary.txt",
  ]) {
    assert.ok(existsSync(join(f.reports, file)), file);
  }
  const summary = JSON.parse(f.read("summary.json"));
  assert.equal(summary.status, "completed");
  assert.equal(summary.eslint.errors, 0);
  assert.equal(summary.eslint.warnings, 0);
  assert.equal(summary.knip.issues, 0);
});

test("type-aware and accessibility findings remain advisory", (t) => {
  const f = fixture(
    t,
    'Promise.resolve(1); export const picture = <img src="card.png" />;',
  );
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  const messages = JSON.parse(f.read("eslint.json")).flatMap((r) => r.messages);
  for (const rule of [
    "@typescript-eslint/no-floating-promises",
    "jsx-a11y/alt-text",
  ]) {
    assert.ok(
      messages.some((m) => m.ruleId === rule && m.severity === 1),
      rule,
    );
    assert.ok(f.read("eslint.txt").includes(rule));
  }
});

test("all three complexity rules flag production functions", (t) => {
  const branches = Array.from(
    { length: 12 },
    (_, i) => `if (value === ${i}) console.log(value);`,
  ).join("\n");
  const lines = Array.from({ length: 85 }, () => "console.log(value);").join(
    "\n",
  );
  const f = fixture(
    t,
    `export function large(value: number) {\n${branches}\n${lines}\n}
    export function nested(value: number) {
      if (value) { if (value) { if (value) { if (value) { if (value) { console.log(value); } } } } }
    }`,
  );
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  const messages = JSON.parse(f.read("eslint.json")).flatMap((r) => r.messages);
  for (const rule of ["complexity", "max-depth", "max-lines-per-function"]) {
    assert.ok(
      messages.some((m) => m.ruleId === rule && m.severity === 1),
      rule,
    );
  }
});

test("test functions are excluded from complexity but still receive typed checks", (t) => {
  const f = fixture(t);
  const lines = Array.from({ length: 85 }, () => "console.log(1);").join("\n");
  f.write(
    "src/example.test.ts",
    `export function testBody() {\n${lines}\nPromise.resolve(1);\n}`,
  );
  assert.equal(f.run().status, 0);
  const messages = JSON.parse(f.read("eslint.json")).find((r) =>
    r.filePath.endsWith("example.test.ts"),
  ).messages;
  assert.ok(
    messages.some(
      (m) => m.ruleId === "@typescript-eslint/no-floating-promises",
    ),
  );
  assert.ok(!messages.some((m) => m.ruleId === "max-lines-per-function"));
});

test("unused files and exports are reported without failing analysis", (t) => {
  const f = fixture(t, 'import { used } from "./shared"; console.log(used);');
  f.write("src/shared.ts", "export const used = 1; export const unused = 2;");
  f.write("src/orphan.ts", "export const orphan = 1;");
  assert.equal(f.run().status, 0);
  const report = f.read("knip.json");
  assert.match(report, /orphan\.ts/);
  assert.match(report, /unused/);
  assert.ok(JSON.parse(f.read("summary.json")).knip.issues > 0);
});

test("unused manifest dependencies are reported without failing analysis", (t) => {
  const f = fixture(t);
  const manifest = JSON.parse(
    readFileSync(join(f.root, "package.json"), "utf8"),
  );
  manifest.dependencies = { "unused-fixture-dependency": "1.0.0" };
  f.write("package.json", JSON.stringify(manifest));
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  assert.match(f.read("knip.json"), /unused-fixture-dependency/);
});

test("existing lint errors still fail and are reported", (t) => {
  const f = fixture(t, "export const unsafe: any = 1; console.log(unsafe);");
  const result = f.run();
  assert.equal(result.status, 1, result.stderr);
  const messages = JSON.parse(f.read("eslint.json")).flatMap((r) => r.messages);
  assert.ok(
    messages.some(
      (m) =>
        m.ruleId === "@typescript-eslint/no-explicit-any" && m.severity === 2,
    ),
  );
  assert.ok(!existsSync(join(f.reports, "summary.json")));
  assert.match(f.read("failure.txt"), /ESLint/);
});

test("parse failures cannot masquerade as advisory findings", (t) => {
  const f = fixture(t, "export const = ;");
  assert.equal(f.run().status, 1);
  assert.ok(
    JSON.parse(f.read("eslint.json")).some((r) => r.fatalErrorCount > 0),
  );
  assert.ok(!existsSync(join(f.reports, "summary.json")));
});

test("failed rerun invalidates success summaries and preserves unrelated files", (t) => {
  const f = fixture(t);
  assert.equal(f.run().status, 0);
  f.write("reports/static-analysis/notes.txt", "keep me");
  f.write("knip.json", "{ invalid");
  assert.equal(f.run().status, 1);
  assert.ok(!existsSync(join(f.reports, "summary.json")));
  assert.ok(!existsSync(join(f.reports, "summary.txt")));
  assert.equal(f.read("notes.txt"), "keep me");
  assert.match(f.read("failure.txt"), /Knip/i);
});

test("invalid ESLint configuration fails clearly", (t) => {
  const f = fixture(t);
  f.write("eslint.analysis.config.js", 'throw new Error("broken config");');
  assert.equal(f.run().status, 1);
  assert.match(f.read("failure.txt"), /broken config/);
});

test("symlinked report directories are refused", (t) => {
  const f = fixture(t);
  const external = mkdtempSync(join(tmpdir(), "mtgcmd external "));
  t.after(() => rmSync(external, { recursive: true, force: true }));
  symlinkSync(external, join(f.root, "reports"), "dir");
  const result = f.run();
  assert.equal(result.status, 1);
  assert.match(result.stderr, /symlink/i);
  assert.ok(!existsSync(join(external, "static-analysis")));
});

test("a successful rerun replaces findings and removes old failure markers", (t) => {
  const f = fixture(t, "export const unsafe: any = 1;");
  assert.equal(f.run().status, 1);
  f.write("src/main.tsx", "export const value = 1; console.log(value);");
  assert.equal(f.run().status, 0);
  assert.equal(JSON.parse(f.read("summary.json")).eslint.errors, 0);
  assert.ok(!existsSync(join(f.reports, "failure.txt")));
});

test("report cleanup failure cannot leave an old successful summary", (t) => {
  const f = fixture(t);
  assert.equal(f.run().status, 0);
  rmSync(join(f.reports, "eslint.json"));
  mkdirSync(join(f.reports, "eslint.json"));
  assert.equal(f.run().status, 1);
  assert.ok(!existsSync(join(f.reports, "summary.json")));
  assert.ok(!existsSync(join(f.reports, "summary.txt")));
});

test("tool spawn failures and malformed Knip output are blocking", (t) => {
  const f = fixture(t);
  const directory = mkdtempSync(join(tmpdir(), "mtgcmd tool stub "));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const hook = join(directory, "hook.mjs");
  for (const outcome of [
    { error: { message: "missing executable" }, status: null, stderr: "" },
    { status: 2, stderr: "internal tool failure" },
    { status: 0, stdout: "not json", stderr: "" },
    { status: 0, stdout: JSON.stringify({ issues: "invalid" }), stderr: "" },
    {
      status: 0,
      stdout: JSON.stringify({
        issues: [{ file: "main.ts", exports: "invalid" }],
      }),
      stderr: "",
    },
  ]) {
    writeFileSync(
      hook,
      `import cp from 'node:child_process';
      import {syncBuiltinESMExports} from 'node:module';
      cp.spawnSync = () => (${JSON.stringify(outcome)}); syncBuiltinESMExports();`,
    );
    const result = f.run({
      NODE_OPTIONS: `--import=${pathToFileURL(hook).href}`,
    });
    assert.equal(result.status, 1, result.stderr);
    assert.match(f.read("failure.txt"), /Knip/);
    assert.ok(!existsSync(join(f.reports, "summary.json")));
  }
});
