import test from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  rmSync,
  existsSync,
  symlinkSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import {
  summarize,
  compare,
  normalizePath,
  fingerprint,
  loadReview,
} from "./analysis-review.mjs";
const cli = fileURLToPath(new URL("./review-analysis.mjs", import.meta.url));
const hash = (text) => createHash("sha256").update(text).digest("hex");
function row(
  filePath = "src/App.tsx",
  messages = [
    {
      ruleId: "complexity",
      severity: 1,
      message: "Function 'App' has a complexity of 22. Maximum allowed is 10.",
      line: 5,
      column: 1,
    },
  ],
) {
  return {
    filePath,
    messages,
    errorCount: messages.filter((m) => m.severity === 2).length,
    warningCount: messages.filter((m) => m.severity === 1).length,
    fatalErrorCount: messages.filter((m) => m.fatal).length,
  };
}
const unused = {
  files: ["src/unused.ts"],
  issues: [
    {
      file: "src/module.ts",
      exports: [{ name: "unused", line: 1, col: 1 }],
      owners: ["fixture"],
    },
  ],
};
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "frontend review "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, "src"));
  writeFileSync(join(root, "src/main.tsx"), "export {};");
  const reports = join(root, "reports/static-analysis");
  mkdirSync(reports, { recursive: true });
  const eslint = JSON.stringify([row()]);
  const knip = JSON.stringify({ issues: [] });
  writeFileSync(join(reports, "eslint.json"), eslint);
  writeFileSync(join(reports, "knip.json"), knip);
  const summary = {
    schemaVersion: 1,
    status: "completed",
    eslint: { errors: 0, warnings: 1, fatalErrors: 0 },
    knip: { issues: 0 },
  };
  writeFileSync(join(reports, "summary.json"), JSON.stringify(summary));
  return {
    root,
    reports,
    summary,
    eslint,
    knip,
    run: (...args) => {
      const result = spawnSync(
        process.execPath,
        [cli, "--root", root, "--reports", reports, ...args],
        {
          cwd: tmpdir(),
          encoding: "utf8",
          env: { ...process.env, NODE_TEST_CONTEXT: undefined },
        },
      );
      assert.ifError(result.error);
      return result;
    },
    save: (summary) =>
      writeFileSync(join(reports, "summary.json"), JSON.stringify(summary)),
  };
}

test("summaries retain occurrence counts, scopes, native metrics and unused files", () => {
  const result = summarize(
    [
      row(),
      row("src/a.test.ts"),
      row("tests/ui.spec.ts"),
      row("scripts/config.mjs"),
    ],
    unused,
    "/repo/frontend",
  );
  assert.equal(result.totals.eslint.warnings, 4);
  assert.equal(result.totals.knip.issues, 2);
  assert.deepEqual(result.scopes.production, { eslint: 1, knip: 2 });
  assert.deepEqual(result.scopes.tests, { eslint: 2, knip: 0 });
  assert.deepEqual(result.scopes.other, { eslint: 1, knip: 0 });
  assert.equal(result.byRule["eslint:complexity"], 4);
  assert.equal(result.byFile["src/unused.ts"], 1);
  assert.deepEqual(result.findings[0].metric, { actual: 22, limit: 10 });
});
test("empty reports and unknown/native parser diagnostics remain explicit", () => {
  assert.equal(
    summarize([], { issues: [] }, "/repo/frontend").findings.length,
    0,
  );
  const result = summarize(
    [
      row("src/a.ts", [
        { ruleId: null, severity: 2, fatal: true, message: "Parse error" },
      ]),
    ],
    { issues: [] },
    "/repo/frontend",
  );
  assert.deepEqual(result.totals.eslint, {
    errors: 1,
    warnings: 0,
    fatalErrors: 1,
  });
  assert.equal(result.findings[0].rule, "<parser>");
  assert.equal(result.findings[0].metric, undefined);
});
test("native function length/depth metrics are extracted only when supplied", () => {
  const result = summarize(
    [
      row("src/a.ts", [
        {
          ruleId: "max-lines-per-function",
          severity: 1,
          message:
            "Function 'A' has too many lines (142). Maximum allowed is 80.",
        },
        {
          ruleId: "max-depth",
          severity: 1,
          message: "Blocks are nested too deeply (5). Maximum allowed is 4.",
        },
        {
          ruleId: "complexity",
          severity: 1,
          message: "Unrecognized localized message",
        },
      ]),
    ],
    { issues: [] },
    "/repo/frontend",
  );
  assert.ok(
    result.findings.some(
      (f) => f.metric?.actual === 142 && f.metric.limit === 80,
    ),
  );
  assert.ok(
    result.findings.some((f) => f.metric?.actual === 5 && f.metric.limit === 4),
  );
  assert.ok(
    result.findings.some((f) => f.message.includes("localized") && !f.metric),
  );
});
test("paths normalize across roots and Windows checkouts without accepting traversal", () => {
  assert.equal(
    normalizePath("/old/frontend/src/a.ts", "/old/frontend"),
    "src/a.ts",
  );
  assert.equal(
    normalizePath("C:\\work\\frontend\\tests\\a.spec.ts", "C:\\work\\frontend"),
    "tests/a.spec.ts",
  );
  assert.equal(normalizePath("src\\a.ts", "/new/frontend"), "src/a.ts");
  for (const path of [
    "../secret",
    "/elsewhere/a.ts",
    "src/../../a.ts",
    "",
    "src\u0000a.ts",
  ])
    assert.throws(() => normalizePath(path, "/repo/frontend"));
});
test("comparisons are multisets, ignore moved lines and expose renamed files", () => {
  const before = summarize([row(), row()], { issues: [] }, "/old/frontend");
  const after = summarize(
    [row("/new/frontend/src/App.tsx", [{ ...row().messages[0], line: 90 }])],
    { issues: [] },
    "/new/frontend",
  );
  const delta = compare(after, before);
  assert.equal(delta.unchanged.length, 1);
  assert.equal(delta.resolved.length, 1);
  assert.equal(delta.new.length, 0);
  const renamed = compare(
    summarize([row("src/Renamed.tsx")], { issues: [] }, "/repo/frontend"),
    after,
  );
  assert.equal(renamed.new.length, 1);
  assert.equal(renamed.resolved.length, 1);
});
test("Knip locations can move without changing identity; category/message changes stay visible", () => {
  const before = summarize([], unused, "/repo/frontend");
  const after = summarize(
    [],
    {
      ...unused,
      issues: [
        {
          file: "src/module.ts",
          exports: [{ name: "unused", line: 42, col: 3 }],
        },
      ],
    },
    "/repo/frontend",
  );
  assert.equal(compare(after, before).unchanged.length, 2);
  const changed = summarize(
    [row("src/a.ts", [{ ...row().messages[0], message: "different" }])],
    { issues: [] },
    "/repo/frontend",
  );
  assert.equal(
    compare(
      changed,
      summarize([row("src/a.ts")], { issues: [] }, "/repo/frontend"),
    ).new.length,
    1,
  );
});
for (const [name, eslint, knip] of [
  ["non-array ESLint", {}, { issues: [] }],
  [
    "bad severity",
    [row("src/a.ts", [{ severity: 9, message: "x" }])],
    { issues: [] },
  ],
  ["bad counts", [{ ...row(), warningCount: 0 }], { issues: [] }],
  ["missing Knip issues", [], {}],
  [
    "invalid Knip category",
    [],
    { issues: [{ file: "src/a.ts", exports: "bad" }] },
  ],
  ["invalid unused files", [], { files: [1], issues: [] }],
])
  test(`rejects ${name}`, () =>
    assert.throws(() => summarize(eslint, knip, "/repo/frontend")));

test("legacy reports are unverified and failed/missing completion cannot appear clean", async (t) => {
  const f = fixture(t);
  assert.equal((await loadReview(f)).freshness, "unverified");
  writeFileSync(join(f.reports, "failure.txt"), "failed tool");
  await assert.rejects(loadReview(f), /incomplete/i);
  assert.equal(f.run().status, 2);
});
test("provenance binds source/config inputs and raw report bytes", async (t) => {
  const f = fixture(t);
  const provenance = {
    sourceFingerprint: await fingerprint(f.root),
    reportHashes: { eslint: hash(f.eslint), knip: hash(f.knip) },
    generatedAt: "2026-10-16T00:00:00.000Z",
    nodeVersion: process.version,
  };
  f.save({ ...f.summary, provenance });
  assert.equal((await loadReview(f)).freshness, "current");
  mkdirSync(join(f.root, "dist"));
  writeFileSync(join(f.root, "dist/app.js"), "generated");
  assert.equal((await loadReview(f)).freshness, "current");
  writeFileSync(join(f.root, "src/main.tsx"), "changed");
  assert.equal((await loadReview(f)).freshness, "stale");
  writeFileSync(join(f.reports, "eslint.json"), f.eslint + "\n");
  await assert.rejects(loadReview(f), /hash|provenance/i);
});
test("configuration changes affect fingerprint and source symlinks are rejected", async (t) => {
  const f = fixture(t);
  const before = await fingerprint(f.root);
  writeFileSync(join(f.root, "package-lock.json"), "{}");
  assert.notEqual(await fingerprint(f.root), before);
  symlinkSync(join(f.root, "src/main.tsx"), join(f.root, "src/link.ts"));
  await assert.rejects(fingerprint(f.root), /symlink/i);
});
test("invalid summary totals and malformed raw data are rejected", async (t) => {
  const f = fixture(t);
  f.save({ ...f.summary, eslint: { ...f.summary.eslint, warnings: 0 } });
  await assert.rejects(loadReview(f), /count|total/i);
  writeFileSync(join(f.reports, "eslint.json"), "bad JSON");
  await assert.rejects(loadReview(f));
});
test("CLI writes fresh artifacts and compares an explicit saved baseline from any cwd", (t) => {
  const f = fixture(t);
  const baseline = join(f.root, "baseline.json");
  const markdown = join(f.root, "review.md");
  const result = f.run("--json", baseline, "--markdown", markdown);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /unverified|Unverified/);
  const saved = JSON.parse(readFileSync(baseline, "utf8"));
  assert.equal(saved.kind, "frontend-analysis-review");
  assert.match(readFileSync(markdown, "utf8"), /complexity/);
  const compared = f.run(
    "--baseline",
    baseline,
    "--json",
    join(f.root, "next.json"),
  );
  assert.equal(compared.status, 0, compared.stderr);
  assert.equal(
    JSON.parse(readFileSync(join(f.root, "next.json"), "utf8")).comparison
      .unchanged.length,
    1,
  );
});
test("invalid baselines and CLI arguments fail without changing existing files", (t) => {
  const f = fixture(t);
  const baseline = join(f.root, "baseline.json");
  writeFileSync(baseline, "{}");
  assert.equal(f.run("--baseline", baseline).status, 1);
  for (const args of [
    ["--unknown"],
    ["--json"],
    ["--root", f.root],
    ["--reports", f.reports],
  ])
    assert.equal(f.run(...args).status, 1);
  assert.equal(readFileSync(baseline, "utf8"), "{}");
});
test("outputs refuse overwrites, aliases and symlink parents without partial artifacts", (t) => {
  const f = fixture(t);
  const existing = join(f.root, "existing.md");
  const fresh = join(f.root, "new.json");
  writeFileSync(existing, "keep");
  assert.equal(f.run("--json", fresh, "--markdown", existing).status, 1);
  assert.equal(existsSync(fresh), false);
  assert.equal(readFileSync(existing, "utf8"), "keep");
  assert.equal(f.run("--json", fresh, "--markdown", fresh).status, 1);
  assert.equal(existsSync(fresh), false);
  symlinkSync(f.reports, join(f.root, "link"), "dir");
  assert.equal(f.run("--json", join(f.root, "link/output.json")).status, 1);
  assert.equal(existsSync(join(f.reports, "output.json")), false);
});

test("native summary key order is immaterial", async (t) => {
  const f = fixture(t);
  f.save({ ...f.summary, eslint: { warnings: 1, fatalErrors: 0, errors: 0 } });
  assert.equal((await loadReview(f)).freshness, "unverified");
});
test("missing raw artifacts cannot be reported as completed", async (t) => {
  const f = fixture(t);
  rmSync(join(f.reports, "knip.json"));
  assert.equal(f.run().status, 2);
});
test("baseline versions, tampered totals and malformed findings are rejected", async (t) => {
  const f = fixture(t);
  const path = join(f.root, "baseline.json");
  const valid = await loadReview(f);
  for (const value of [
    { ...valid, schemaVersion: 99 },
    { ...valid, status: "failed" },
    { ...valid, totals: {} },
    { ...valid, findings: [{ ...valid.findings[0], file: "../outside.ts" }] },
  ]) {
    writeFileSync(path, JSON.stringify(value));
    await assert.rejects(loadReview({ ...f, baseline: path }));
  }
});
test("fingerprints detect new/deleted inputs while ignoring dependency and generated trees", async (t) => {
  const f = fixture(t);
  const before = await fingerprint(f.root);
  mkdirSync(join(f.root, "node_modules"));
  writeFileSync(join(f.root, "node_modules/dependency.js"), "ignored");
  assert.equal(await fingerprint(f.root), before);
  writeFileSync(join(f.root, "src/new.ts"), "new");
  assert.notEqual(await fingerprint(f.root), before);
  rmSync(join(f.root, "src/new.ts"));
  assert.equal(await fingerprint(f.root), before);
  symlinkSync(join(f.root, "src"), join(f.root, "reports/link"), "dir");
  assert.equal(await fingerprint(f.root), before);
});

test("a completion marker cannot legitimize enforced ESLint errors", async (t) => {
  const f = fixture(t);
  const eslint = JSON.stringify([
    row("src/a.ts", [
      { ruleId: null, severity: 2, fatal: true, message: "Parse failed" },
    ]),
  ]);
  writeFileSync(join(f.reports, "eslint.json"), eslint);
  f.save({ ...f.summary, eslint: { errors: 1, warnings: 0, fatalErrors: 1 } });
  assert.equal(f.run().status, 2);
});
test("output symlinks never overwrite their targets and missing markers are incomplete", (t) => {
  const f = fixture(t);
  const target = join(f.root, "target.json");
  writeFileSync(target, "keep");
  const alias = join(f.root, "alias.json");
  symlinkSync(target, alias);
  assert.equal(f.run("--json", alias).status, 1);
  assert.equal(readFileSync(target, "utf8"), "keep");
  rmSync(join(f.reports, "summary.json"));
  assert.equal(f.run().status, 2);
});
