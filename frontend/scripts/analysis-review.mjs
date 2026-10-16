import { isDeepStrictEqual } from "node:util";
import { createHash } from "node:crypto";
import { lstat, readdir, readFile } from "node:fs/promises";
import { posix } from "node:path";

export const digest = (value) =>
  createHash("sha256").update(value).digest("hex");
const requireValue = (condition, message) => {
  if (!condition) throw new Error(message);
};
const object = (value) =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const integer = (value) => Number.isInteger(value) && value >= 0;
const order = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

export function normalizePath(file, root) {
  requireValue(
    typeof file === "string" && file && !file.includes("\0"),
    "Invalid finding path",
  );
  const path = file.replaceAll("\\", "/");
  const base = posix.normalize(root.replaceAll("\\", "/")).replace(/\/$/, "");
  const absolute = path.startsWith("/") || /^[a-z]:\//i.test(path);
  const normalized = posix.normalize(path);
  const result = absolute
    ? posix.relative(base, normalized)
    : normalized.replace(/^\.\//, "");
  requireValue(
    result &&
      result !== "." &&
      !result.startsWith("../") &&
      result !== ".." &&
      !posix.isAbsolute(result) &&
      !/^[a-z]:/i.test(result),
    "Finding path is outside the frontend root",
  );
  return result;
}
function scope(file) {
  if (file.startsWith("tests/") || /\.(test|spec)\.[cm]?[jt]sx?$/.test(file))
    return "tests";
  return file.startsWith("src/") ? "production" : "other";
}
function nativeMetric(rule, message) {
  const patterns = {
    complexity: /complexity of (\d+)\. Maximum allowed is (\d+)/,
    "max-lines-per-function":
      /too many lines \((\d+)\)\. Maximum allowed is (\d+)/,
    "max-depth": /nested too deeply \((\d+)\)\. Maximum allowed is (\d+)/,
  };
  const match = patterns[rule]?.exec(message);
  return match
    ? { actual: Number(match[1]), limit: Number(match[2]) }
    : undefined;
}
function eslintFindings(rows, root) {
  requireValue(Array.isArray(rows), "Invalid ESLint report");
  return rows.flatMap((row) => {
    requireValue(
      object(row) && Array.isArray(row.messages),
      "Invalid ESLint file result",
    );
    const file = normalizePath(row.filePath, root);
    const findings = row.messages.map((message) => {
      requireValue(
        object(message) &&
          [1, 2].includes(message.severity) &&
          typeof message.message === "string" &&
          (typeof message.ruleId === "string" || message.ruleId === null),
        "Invalid ESLint diagnostic",
      );
      for (const key of ["line", "column"])
        requireValue(
          message[key] === undefined || integer(message[key]),
          "Invalid diagnostic location",
        );
      requireValue(
        message.fatal === undefined || typeof message.fatal === "boolean",
        "Invalid fatal diagnostic",
      );
      const rule = message.ruleId ?? "<parser>";
      return {
        tool: "eslint",
        file,
        scope: scope(file),
        rule,
        severity: message.severity,
        message: message.message,
        line: message.line ?? null,
        column: message.column ?? null,
        fatal: message.fatal ?? false,
        ...(nativeMetric(rule, message.message)
          ? { metric: nativeMetric(rule, message.message) }
          : {}),
      };
    });
    requireValue(
      row.errorCount === findings.filter((f) => f.severity === 2).length &&
        row.warningCount === findings.filter((f) => f.severity === 1).length &&
        row.fatalErrorCount === findings.filter((f) => f.fatal).length,
      "ESLint diagnostic count disagreement",
    );
    return findings;
  });
}
export function knipFindings(data, root) {
  requireValue(
    object(data) &&
      Array.isArray(data.issues) &&
      (data.files === undefined || Array.isArray(data.files)),
    "Invalid Knip report",
  );
  const finding = (path, rule, message, location = {}) => {
    const file = normalizePath(path, root);
    return {
      tool: "knip",
      file,
      scope: scope(file),
      rule,
      severity: 1,
      message,
      line: location.line ?? null,
      column: location.col ?? null,
      fatal: false,
    };
  };
  const files = (data.files ?? []).map((file) =>
    finding(file, "files", "Unused file"),
  );
  return [
    ...files,
    ...data.issues.flatMap((row) => {
      requireValue(object(row), "Invalid Knip file result");
      normalizePath(row.file, root);
      return Object.entries(row).flatMap(([category, values]) => {
        if (["file", "owners"].includes(category)) return [];
        requireValue(Array.isArray(values), "Invalid Knip issue category");
        return values.map((value) => {
          requireValue(
            object(value) && typeof value.name === "string",
            "Invalid Knip issue",
          );
          for (const key of ["line", "col"])
            requireValue(
              value[key] === undefined || integer(value[key]),
              "Invalid Knip location",
            );
          // Locations are separate from identity; native categories and symbol names remain visible.
          return finding(row.file, category, value.name, value);
        });
      });
    }),
  ];
}
function aggregate(findings) {
  const result = {
    totals: {
      eslint: { errors: 0, warnings: 0, fatalErrors: 0 },
      knip: { issues: 0 },
    },
    scopes: Object.fromEntries(
      ["production", "tests", "other"].map((name) => [
        name,
        { eslint: 0, knip: 0 },
      ]),
    ),
    byRule: {},
    byFile: {},
    findings,
  };
  for (const f of findings) {
    result.scopes[f.scope][f.tool]++;
    const key = `${f.tool}:${f.rule}`;
    Object.defineProperty(result.byRule, key, {
      value: (result.byRule[key] ?? 0) + 1,
      enumerable: true,
      configurable: true,
    });
    Object.defineProperty(result.byFile, f.file, {
      value:
        (Object.hasOwn(result.byFile, f.file) ? result.byFile[f.file] : 0) + 1,
      enumerable: true,
      configurable: true,
    });
    if (f.tool === "knip") result.totals.knip.issues++;
    else {
      result.totals.eslint[f.severity === 2 ? "errors" : "warnings"]++;
      if (f.fatal) result.totals.eslint.fatalErrors++;
    }
  }
  return result;
}
export function summarize(eslint, knip, root) {
  const findings = [
    ...eslintFindings(eslint, root),
    ...knipFindings(knip, root),
  ];
  findings.sort((a, b) =>
    order(
      JSON.stringify([a.file, a.tool, a.rule, a.message, a.line, a.column]),
      JSON.stringify([b.file, b.tool, b.rule, b.message, b.line, b.column]),
    ),
  );
  return aggregate(findings);
}
const identity = (f) =>
  JSON.stringify([f.tool, f.file, f.rule, f.severity, f.message]);
export function compare(current, baseline) {
  const remaining = new Map();
  for (const f of baseline.findings) {
    const key = identity(f);
    remaining.set(key, [...(remaining.get(key) ?? []), f]);
  }
  const result = { new: [], resolved: [], unchanged: [] };
  for (const f of current.findings) {
    const matches = remaining.get(identity(f));
    if (matches?.length) {
      matches.pop();
      result.unchanged.push(f);
    } else result.new.push(f);
  }
  result.resolved = [...remaining.values()].flat();
  return result;
}
const excluded = new Set([
  "node_modules",
  ".git",
  "dist",
  "reports",
  "test-results",
  "playwright-report",
]);
export async function fingerprint(root) {
  const hash = createHash("sha256");
  async function visit(directory, prefix = "") {
    for (const name of (await readdir(directory)).sort(order)) {
      if (excluded.has(name)) continue;
      const path = `${directory}/${name}`;
      const relative = prefix + name;
      const info = await lstat(path);
      if (info.isSymbolicLink())
        throw new Error(`Source symlink is unsupported: ${relative}`);
      if (info.isDirectory()) await visit(path, `${relative}/`);
      else if (/\.(?:[cm]?[jt]sx?|json|html|css)$/.test(name)) {
        hash.update(relative + "\0");
        hash.update(digest(await readFile(path)));
      }
    }
  }
  await visit(root);
  return hash.digest("hex");
}
export class IncompleteAnalysis extends Error {}
async function json(path) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch (cause) {
    throw new Error(`Cannot read JSON report: ${path}`, { cause });
  }
}
function validateProvenance(value) {
  requireValue(
    object(value) &&
      /^[a-f0-9]{64}$/.test(value.sourceFingerprint) &&
      object(value.reportHashes) &&
      [value.reportHashes.eslint, value.reportHashes.knip].every(
        (hash) => typeof hash === "string" && /^[a-f0-9]{64}$/.test(hash),
      ) &&
      typeof value.generatedAt === "string" &&
      Number.isFinite(Date.parse(value.generatedAt)) &&
      typeof value.nodeVersion === "string",
    "Invalid report provenance",
  );
}
function validateBaseline(value) {
  requireValue(
    object(value) &&
      value.kind === "frontend-analysis-review" &&
      value.schemaVersion === 1 &&
      value.status === "completed" &&
      Array.isArray(value.findings),
    "Invalid baseline review artifact",
  );
  for (const f of value.findings) {
    requireValue(
      object(f) &&
        ["eslint", "knip"].includes(f.tool) &&
        typeof f.rule === "string" &&
        typeof f.message === "string" &&
        [1, 2].includes(f.severity) &&
        typeof f.fatal === "boolean",
      "Invalid baseline finding",
    );
    requireValue(
      normalizePath(f.file, "/baseline") === f.file &&
        f.scope === scope(f.file),
      "Invalid baseline path/scope",
    );
  }
  requireValue(
    isDeepStrictEqual(aggregate(value.findings).totals, value.totals),
    "Baseline count disagreement",
  );
  if (value.provenance) validateProvenance(value.provenance);
  return value;
}
export async function loadReview({ reports, root, baseline }) {
  if (
    await lstat(`${reports}/failure.txt`).then(
      () => true,
      (error) => {
        if (error.code === "ENOENT") return false;
        throw error;
      },
    )
  )
    throw new IncompleteAnalysis(
      "Frontend analysis is incomplete; inspect failure.txt",
    );
  const marker = await readFile(`${reports}/summary.json`, "utf8").catch(
    (error) => {
      if (error.code === "ENOENT")
        throw new IncompleteAnalysis(
          "Frontend analysis is incomplete: missing completion marker",
        );
      throw error;
    },
  );
  let summary;
  try {
    summary = JSON.parse(marker);
  } catch {
    throw new Error("Invalid completion summary JSON");
  }
  if (summary.status !== "completed")
    throw new IncompleteAnalysis("Frontend analysis is incomplete");
  requireValue(
    summary.schemaVersion === 1,
    "Unsupported native summary version",
  );
  const readNative = (name) =>
    readFile(`${reports}/${name}.json`).catch((error) => {
      if (error.code === "ENOENT")
        throw new IncompleteAnalysis(
          `Frontend analysis is incomplete: missing ${name} report`,
        );
      throw error;
    });
  const eslint = await readNative("eslint");
  const knip = await readNative("knip");
  const review = summarize(JSON.parse(eslint), JSON.parse(knip), root);
  requireValue(
    isDeepStrictEqual(summary.eslint, review.totals.eslint) &&
      isDeepStrictEqual(summary.knip, review.totals.knip),
    "Native report total/count disagreement",
  );
  if (review.totals.eslint.errors)
    throw new IncompleteAnalysis(
      "Frontend analysis is incomplete: enforced ESLint errors",
    );
  let freshness = "unverified";
  if (summary.provenance) {
    validateProvenance(summary.provenance);
    requireValue(
      summary.provenance.reportHashes.eslint === digest(eslint) &&
        summary.provenance.reportHashes.knip === digest(knip),
      "Report hash/provenance disagreement",
    );
    freshness =
      summary.provenance.sourceFingerprint === (await fingerprint(root))
        ? "current"
        : "stale";
  }
  return {
    kind: "frontend-analysis-review",
    schemaVersion: 1,
    status: "completed",
    ...review,
    provenance: summary.provenance ?? null,
    freshness,
    comparison: baseline
      ? compare(review, validateBaseline(await json(baseline)))
      : null,
  };
}
export function markdown(review) {
  const lines = [
    "# Frontend analysis review",
    "",
    `Source freshness: **${review.freshness}**.`,
    "",
    `ESLint: ${review.totals.eslint.errors} errors, ${review.totals.eslint.warnings} warnings; Knip: ${review.totals.knip.issues} issues.`,
    "",
    "| Scope | ESLint | Knip |",
    "| --- | ---: | ---: |",
  ];
  for (const [name, counts] of Object.entries(review.scopes))
    lines.push(`| ${name} | ${counts.eslint} | ${counts.knip} |`);
  lines.push("", "## Rules", "");
  for (const [rule, count] of Object.entries(review.byRule))
    lines.push(`- ${rule}: ${count}`);
  lines.push("", "## Files", "");
  for (const [file, count] of Object.entries(review.byFile))
    lines.push(`- ${file}: ${count}`);
  const metrics = review.findings.filter((f) => f.metric);
  if (metrics.length) {
    lines.push("", "## Native metrics", "");
    for (const f of metrics)
      lines.push(
        `- ${f.file}:${f.line ?? "?"} — ${f.rule}: ${f.metric.actual} (limit ${f.metric.limit})`,
      );
  }
  if (review.comparison) {
    const c = review.comparison;
    lines.push(
      "",
      `Comparison: ${c.new.length} new, ${c.resolved.length} resolved, ${c.unchanged.length} unchanged occurrences.`,
    );
  }
  lines.push(
    "",
    "Occurrence identities ignore line/column moves; renamed files or changed messages count as resolved/new. Compare tool/config/source coverage before interpreting deltas. Freshness fingerprints cover frontend JS/TS/JSON/HTML/CSS inputs, not backend state, external plugins, environment equivalence, or correctness. Legacy reports have unverified provenance. Baselines never suppress findings or change gates.",
    "",
  );
  return lines.join("\n");
}
