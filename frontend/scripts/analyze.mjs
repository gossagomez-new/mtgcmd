import { lstat, mkdir, unlink, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const root = fileURLToPath(new URL("..", import.meta.url));
const reports = join(root, "reports/static-analysis");
const ownedFiles = [
  "summary.json",
  "summary.txt",
  "failure.txt",
  "eslint.json",
  "eslint.txt",
  "knip.json",
  "knip.txt",
];
const require = createRequire(import.meta.url);
let prepared = false;

async function prepareReports() {
  for (const path of [join(root, "reports"), reports]) {
    const info = await lstat(path).catch((error) => {
      if (error.code !== "ENOENT") throw error;
    });
    if (info?.isSymbolicLink())
      throw new Error("Refusing a symlinked report directory.");
    if (info && !info.isDirectory())
      throw new Error("Report path is not a directory.");
    await mkdir(path, { recursive: true });
  }
  prepared = true;
  for (const name of ownedFiles) {
    await unlink(join(reports, name)).catch((error) => {
      if (error.code !== "ENOENT") throw error;
    });
  }
}

async function write(name, content) {
  // CREATE_NEW avoids following a pre-existing report-file symlink.
  await writeFile(join(reports, name), content, { flag: "wx" });
}

async function lint() {
  const { ESLint } = await import("eslint");
  const eslint = new ESLint({
    cwd: root,
    overrideConfigFile: join(root, "eslint.analysis.config.js"),
  });
  const results = await eslint.lintFiles(["."]);
  const formatter = await eslint.loadFormatter("stylish");
  await write(
    "eslint.txt",
    formatter.format(results) || "No ESLint findings.\n",
  );
  await write(
    "eslint.json",
    JSON.stringify(
      results.map((result) => ({
        ...result,
        filePath: relative(root, result.filePath),
      })),
      null,
      2,
    ) + "\n",
  );
  const counts = results.reduce(
    (sum, result) => ({
      errors: sum.errors + result.errorCount,
      warnings: sum.warnings + result.warningCount,
      fatalErrors: sum.fatalErrors + result.fatalErrorCount,
    }),
    { errors: 0, warnings: 0, fatalErrors: 0 },
  );
  if (counts.errors)
    throw new Error(
      `ESLint reported ${counts.errors} enforced/parse errors; see eslint.txt.`,
    );
  return counts;
}

async function unusedCode() {
  const packageEntry = require.resolve("knip");
  const result = spawnSync(
    process.execPath,
    [
      join(dirname(packageEntry), "../bin/knip.js"),
      "--directory",
      root,
      "--config",
      join(root, "knip.json"),
      "--reporter",
      "json",
      "--no-progress",
      "--no-exit-code",
    ],
    {
      cwd: root,
      encoding: "utf8",
      timeout: 120000,
      maxBuffer: 16 * 1024 * 1024,
      env: { ...process.env, NO_COLOR: "1" },
    },
  );
  if (result.error || result.status !== 0) {
    throw new Error(
      `Knip execution failed (${result.error?.message ?? result.status}): ${result.stderr ?? ""}`,
    );
  }
  let data;
  try {
    data = JSON.parse(result.stdout);
    if (!Array.isArray(data.issues)) throw new Error("missing issues array");
  } catch (error) {
    throw new Error(`Invalid Knip JSON: ${error.message}`, { cause: error });
  }
  const lines = [];
  for (const row of data.issues) {
    if (typeof row.file !== "string")
      throw new Error("Invalid Knip issue file.");
    for (const [category, findings] of Object.entries(row)) {
      if (category === "file" || category === "owners") continue;
      if (!Array.isArray(findings))
        throw new Error("Invalid Knip issue category.");
      for (const finding of findings) {
        lines.push(`${row.file}: ${category}: ${JSON.stringify(finding)}`);
      }
    }
  }
  await write("knip.json", JSON.stringify(data, null, 2) + "\n");
  await write(
    "knip.txt",
    (lines.join("\n") || "No Knip findings.") +
      (result.stderr ? `\nDiagnostics:\n${result.stderr}` : "\n"),
  );
  return { issues: lines.length };
}

try {
  await prepareReports();
  const eslint = await lint();
  const knip = await unusedCode();
  const summary = { schemaVersion: 1, status: "completed", eslint, knip };
  await write(
    "summary.txt",
    `Frontend analysis completed (new findings advisory).\nESLint: ${eslint.errors} errors, ${eslint.warnings} warnings\nKnip: ${knip.issues} issues\n`,
  );
  // Write the machine-readable completion marker last.
  await write("summary.json", JSON.stringify(summary, null, 2) + "\n");
  console.log(
    `Frontend analysis: ${eslint.warnings} ESLint warnings; ${knip.issues} Knip issues.`,
  );
  console.log(`Reports: ${reports}`);
} catch (error) {
  console.error(`Frontend analysis failed: ${error.message}`);
  if (prepared) {
    // Partial reports are diagnostic; a previous success must never survive failure.
    for (const name of ["summary.json", "summary.txt"]) {
      await unlink(join(reports, name)).catch(() => {});
    }
    await write("failure.txt", `${error.stack ?? error}\n`).catch((failure) => {
      console.error(`Could not write failure report: ${failure.message}`);
    });
  }
  process.exitCode = 1;
}
