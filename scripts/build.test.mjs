import test from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  copyFileSync,
  writeFileSync,
  readFileSync,
  existsSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const script = fileURLToPath(new URL("./build.sh", import.meta.url));
const stub = `#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const name = path.basename(process.argv[1]);
const root = process.env.FIXTURE_ROOT;
fs.appendFileSync(process.env.EVENTS, JSON.stringify({name, args: process.argv.slice(2), cwd: process.cwd(), mavenOpts: process.env.MAVEN_OPTS}) + '\\n');
fs.writeSync(1, name + ' output' + String.fromCharCode(10));
fs.writeSync(2, name + ' diagnostic' + String.fromCharCode(10));
const status = Number(process.env[name === 'npm' ? 'NPM_EXIT' : 'MVN_EXIT'] || 0);
if (name === 'npm' && process.argv.includes('analyze')) {
  if (process.env.ANALYZE_EXIT) process.exit(Number(process.env.ANALYZE_EXIT));
  if (process.env.NO_ANALYSIS_REPORT !== '1') {
    fs.mkdirSync(path.join(root, 'frontend/reports/static-analysis'), {recursive: true});
    fs.writeFileSync(path.join(root, 'frontend/reports/static-analysis/summary.json'), '{}');
  }
  process.exit(0);
}
if (status) process.exit(status);
if (name === 'npm') {
  if (process.env.NO_INDEX !== '1') {
    fs.mkdirSync(path.join(root, 'frontend/dist/assets'), {recursive: true});
    fs.writeFileSync(path.join(root, 'frontend/dist/index.html'), '<div id="root"></div>');
    fs.writeFileSync(path.join(root, 'frontend/dist/assets/current.js'), 'current');
  }
} else {
  if (process.argv.includes('clean')) fs.rmSync(path.join(root, 'backend/target'), {recursive: true, force: true});
  fs.mkdirSync(path.join(root, 'backend/target'), {recursive: true});
  if (process.env.NO_PMD_LOG !== '1') fs.writeFileSync(path.join(root, 'backend/target/pmd.txt'), process.env.PMD_FINDINGS || '');
  const count = Number(process.env.JAR_COUNT || '1');
  for (let i = 0; i < count; i++) fs.writeFileSync(path.join(root, 'backend/target', 'fixture-' + i + '.jar'), 'fixture');
}
`;

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "mtgcmd build test "));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const root = join(directory, "repo with spaces");
  for (const folder of ["scripts", "frontend", "backend", "bin", "elsewhere"])
    mkdirSync(join(root, folder), { recursive: true });
  copyFileSync(script, join(root, "scripts/build.sh"));
  for (const tool of ["npm", "mvn"])
    writeFileSync(join(root, "bin", tool), stub, { mode: 0o755 });
  const eventsFile = join(directory, "events.jsonl");
  return {
    root,
    events: () =>
      existsSync(eventsFile)
        ? readFileSync(eventsFile, "utf8")
            .trim()
            .split("\n")
            .filter(Boolean)
            .map((line) => JSON.parse(line))
        : [],
    run: (args = [], overrides = {}) =>
      spawnSync("bash", [join(root, "scripts/build.sh"), ...args], {
        cwd: join(root, "elsewhere"),
        encoding: "utf8",
        timeout: 5000,
        env: {
          ...process.env,
          // Fake build tools are ordinary programs, not nested Node test workers.
          NODE_TEST_CONTEXT: undefined,
          PATH: `${join(root, "bin")}:${process.env.PATH}`,
          FIXTURE_ROOT: root,
          EVENTS: eventsFile,
          MAVEN_OPTS: "-Dfixture-marker=true",
          ...overrides,
        },
      }),
  };
}

test("builds frontend before clean Java verification from another directory", (t) => {
  const f = fixture(t);
  const stale = join(f.root, "backend/target/classes/static/assets/stale.js");
  mkdirSync(dirname(stale), { recursive: true });
  writeFileSync(stale, "old");
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  const events = f.events();
  assert.deepEqual(
    events.map((e) => e.name),
    ["npm", "npm", "mvn"],
  );
  assert.deepEqual(events[0].args, [
    "--prefix",
    join(f.root, "frontend"),
    "run",
    "analyze",
  ]);
  assert.deepEqual(events[1].args, [
    "--prefix",
    join(f.root, "frontend"),
    "run",
    "build",
  ]);
  assert.deepEqual(events[2].args, [
    "-f",
    join(f.root, "backend/pom.xml"),
    "clean",
    "verify",
  ]);
  assert.equal(events[2].mavenOpts, "-Dfixture-marker=true");
  assert.equal(existsSync(stale), false);
  assert.ok(
    result.stdout.includes(join(f.root, "backend/target/fixture-0.jar")),
  );
  assert.match(result.stdout, /npm output[\s\S]*mvn output/);
  assert.match(result.stderr, /npm diagnostic[\s\S]*mvn diagnostic/);
  assert.ok(
    result.stdout.includes(
      `Frontend analysis: ${join(f.root, "frontend/reports/static-analysis/summary.json")}`,
    ),
  );
});

test("explicit skip mode packages without claiming tests passed", (t) => {
  const f = fixture(t);
  const result = f.run(["--skip-tests"]);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(f.events()[2].args, [
    "-f",
    join(f.root, "backend/pom.xml"),
    "clean",
    "package",
    "-DskipTests",
  ]);
  assert.match(result.stdout, /tests skipped/i);
  assert.doesNotMatch(result.stdout, /tests passed/i);
  assert.match(result.stdout, /Frontend analysis:.*summary\.json/);
});

test("help does not execute build tools", (t) => {
  const f = fixture(t);
  const result = f.run(["--help"]);
  assert.equal(result.status, 0);
  assert.match(result.stdout, /Usage:.*build\.sh/);
  assert.deepEqual(f.events(), []);
});

for (const args of [
  ["--unknown"],
  ["extra"],
  ["--skip-tests", "extra"],
  ["--help", "--skip-tests"],
  ["--skip-tests", "--skip-tests"],
]) {
  test(`rejects invalid arguments ${args.join(" ")} before invoking tools`, (t) => {
    const f = fixture(t);
    const result = f.run(args);
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Usage:/);
    assert.deepEqual(f.events(), []);
  });
}

test("frontend failure preserves exit code and does not invoke Maven", (t) => {
  const f = fixture(t);
  const result = f.run([], { NPM_EXIT: "17" });
  assert.equal(result.status, 17);
  assert.deepEqual(
    f.events().map((e) => e.name),
    ["npm", "npm"],
  );
  assert.doesNotMatch(result.stdout, /Build complete/i);
});

test("missing frontend index prevents packaging", (t) => {
  const f = fixture(t);
  const result = f.run([], { NO_INDEX: "1" });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /index\.html/);
  assert.deepEqual(
    f.events().map((e) => e.name),
    ["npm", "npm"],
  );
});

test("Maven failure preserves exit code and does not report success", (t) => {
  const f = fixture(t);
  const result = f.run([], { MVN_EXIT: "23" });
  assert.equal(result.status, 23);
  assert.deepEqual(
    f.events().map((e) => e.name),
    ["npm", "npm", "mvn"],
  );
  assert.doesNotMatch(result.stdout, /Build complete/i);
});

for (const count of [0, 2]) {
  test(`rejects artifact count ${count} rather than reporting a guessed jar`, (t) => {
    const f = fixture(t);
    const result = f.run([], { JAR_COUNT: String(count) });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /exactly one.*jar/i);
    assert.doesNotMatch(result.stdout, /Build complete/i);
  });
}

for (const args of [[], ["--skip-tests"]]) {
  test(`analysis failure blocks compilation in ${args.length ? "skip" : "default"} mode`, (t) => {
    const f = fixture(t);
    const result = f.run(args, { ANALYZE_EXIT: "19" });
    assert.equal(result.status, 19);
    assert.equal(f.events().length, 1);
    assert.doesNotMatch(result.stdout, /Build complete/);
  });
  test(`missing analysis report blocks packaging in ${args.length ? "skip" : "default"} mode`, (t) => {
    const f = fixture(t);
    const result = f.run(args, { NO_ANALYSIS_REPORT: "1" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /analysis.*summary\.json/i);
    assert.equal(f.events().length, 1);
  });
  for (const findings of [
    "",
    "Card.java:7:\tSystemPrintln:\tAvoid printing directly.\n",
  ]) {
    test(`reports the PMD log with ${findings ? "findings" : "no findings"} in ${args.length ? "skip" : "default"} mode`, (t) => {
      const f = fixture(t);
      const result = f.run(args, { PMD_FINDINGS: findings });
      assert.equal(result.status, 0, result.stderr);
      const log = join(f.root, "backend/target/pmd.txt");
      assert.equal(readFileSync(log, "utf8"), findings);
      assert.ok(result.stdout.includes(`PMD findings log: ${log}`));
      assert.match(result.stdout, /Build complete/);
    });
  }
  test(`missing PMD log prevents success in ${args.length ? "skip" : "default"} mode`, (t) => {
    const f = fixture(t);
    const result = f.run(args, { NO_PMD_LOG: "1" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /PMD.*pmd\.txt/i);
    assert.doesNotMatch(result.stdout, /Build complete/);
  });
}
