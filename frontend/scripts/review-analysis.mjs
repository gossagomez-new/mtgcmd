import { open, realpath, unlink } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  IncompleteAnalysis,
  loadReview,
  markdown,
} from "./analysis-review.mjs";

async function outputs(entries) {
  const created = [];
  try {
    const paths = entries.map(([path]) => resolve(path));
    if (new Set(paths).size !== paths.length)
      throw new Error("Output paths must be distinct");
    for (const path of paths) {
      if ((await realpath(dirname(path))) !== dirname(path))
        throw new Error("Symlinked output parents are not supported");
      const file = await open(path, "wx");
      created.push({ path, file });
    }
    for (let i = 0; i < created.length; i++)
      await created[i].file.writeFile(entries[i][1]);
    await Promise.all(created.map(({ file }) => file.close()));
  } catch (error) {
    for (const { path, file } of created) {
      await file.close().catch(() => {});
      await unlink(path).catch(() => {});
    }
    throw error;
  }
}
try {
  const args = process.argv.slice(2);
  const options = {};
  if (args.length === 1 && args[0] === "--help") {
    console.log(
      "Usage: node frontend/scripts/review-analysis.mjs [--root FRONTEND] [--reports DIR] [--baseline SAVED_JSON] [--json NEW_FILE] [--markdown NEW_FILE]",
    );
  } else {
    for (let i = 0; i < args.length; i += 2) {
      const name = args[i].replace(/^--/, "");
      if (
        !args[i].startsWith("--") ||
        !["root", "reports", "baseline", "json", "markdown"].includes(name) ||
        options[name] ||
        !args[i + 1] ||
        args[i + 1].startsWith("--")
      )
        throw new Error(
          "Invalid, duplicate, or missing CLI argument; use --help",
        );
      options[name] = resolve(args[i + 1]);
    }
    const root = options.root ?? fileURLToPath(new URL("..", import.meta.url));
    const reports = options.reports ?? resolve(root, "reports/static-analysis");
    const review = await loadReview({
      root,
      reports,
      baseline: options.baseline,
    });
    const text = markdown(review);
    const entries = [];
    if (options.json)
      entries.push([options.json, JSON.stringify(review, null, 2) + "\n"]);
    if (options.markdown) entries.push([options.markdown, text]);
    await outputs(entries);
    console.log(text);
  }
} catch (error) {
  console.error(`Frontend review failed: ${error.message}`);
  process.exitCode = error instanceof IncompleteAnalysis ? 2 : 1;
}
