import test from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  copyFileSync,
  rmSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const backend = fileURLToPath(new URL("../backend/", import.meta.url));

function fixture(t) {
  const pom = readFileSync(join(backend, "pom.xml"), "utf8");
  const plugin = pom
    .match(/<plugin>[\s\S]*?<\/plugin>/g)
    ?.find((block) =>
      block.includes("<artifactId>maven-pmd-plugin</artifactId>"),
    );
  assert.ok(plugin, "PMD must be configured in the backend build");
  const root = mkdtempSync(join(tmpdir(), "mtgcmd pmd test "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  copyFileSync(join(backend, "pmd-ruleset.xml"), join(root, "pmd-ruleset.xml"));
  writeFileSync(
    join(root, "pom.xml"),
    `
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.16</version><relativePath/></parent>
  <groupId>dev.mtgcmd</groupId><artifactId>pmd-fixture</artifactId><version>1</version>
  <properties><java.version>25</java.version></properties>
  <build><plugins>${plugin}</plugins></build>
</project>`,
  );
  function source(scope, name, body) {
    const directory = join(root, "src", scope, "java", "fixture");
    mkdirSync(directory, { recursive: true });
    writeFileSync(
      join(directory, `${name}.java`),
      `package fixture;\n${body}\n`,
    );
  }
  function run(...args) {
    return new Promise((resolve, reject) => {
      const child = spawn(
        "mvn",
        ["-B", "-q", "-f", join(root, "pom.xml"), ...args],
        { timeout: 180000 },
      );
      let output = "";
      child.stdout.on("data", (data) => {
        output += data;
      });
      child.stderr.on("data", (data) => {
        output += data;
      });
      child.on("error", reject);
      child.on("close", (status) => resolve({ status, output }));
    });
  }
  return {
    root,
    source,
    run,
    report: (name) => readFileSync(join(root, "target", name), "utf8"),
  };
}

test("advisory lifecycle analysis logs all main/test findings, supports Java 25, and replaces stale reports", async (t) => {
  const f = fixture(t);
  f.source(
    "main",
    "MainFixture",
    `public class MainFixture {
    private final String label;
    public MainFixture(String value) {
      if (value.isBlank()) throw new IllegalArgumentException();
      this.label = value;
    }
    public void print() { System.out.println(label); }
  }`,
  );
  f.source(
    "test",
    "TestFixture",
    'public class TestFixture { public void print() { System.out.println("test"); } }',
  );
  const build = await f.run("verify", "-DskipTests");
  assert.equal(build.status, 0, build.output);
  assert.ok(existsSync(join(f.root, "target/pmd-fixture-1.jar")));
  const text = f.report("pmd.txt");
  const xml = f.report("pmd.xml");
  assert.match(text, /MainFixture\.java:\d+:\s+SystemPrintln:/);
  assert.match(text, /TestFixture\.java:\d+:\s+SystemPrintln:/);
  assert.doesNotMatch(xml, /<error\b|<configerror\b/);
  assert.equal(
    text.trim().split("\n").length,
    (xml.match(/<violation\b/g) || []).length,
  );
  assert.ok(existsSync(join(f.root, "target/reports/pmd.html")));

  f.source(
    "main",
    "MainFixture",
    '@SuppressWarnings("PMD") public class MainFixture {}',
  );
  f.source(
    "test",
    "TestFixture",
    '@SuppressWarnings("PMD") public class TestFixture {}',
  );
  const standalone = await f.run("test-compile", "pmd:pmd");
  assert.equal(standalone.status, 0, standalone.output);
  assert.match(
    f.report("pmd.txt"),
    /rule violation suppressed by @SuppressWarnings/,
  );
  assert.doesNotMatch(f.report("pmd.txt"), /SystemPrintln:/);
  assert.doesNotMatch(f.report("pmd.xml"), /<violation\b/);

  // A focused fixture ruleset permits genuinely clean inputs without suppression.
  writeFileSync(
    join(f.root, "pmd-ruleset.xml"),
    `<ruleset name="clean fixture" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
    <description>Exercise empty report generation.</description>
    <rule ref="category/java/bestpractices.xml/SystemPrintln"/>
  </ruleset>`,
  );
  f.source("main", "MainFixture", "public class MainFixture {}");
  f.source("test", "TestFixture", "public class TestFixture {}");
  const empty = await f.run("test-compile", "pmd:pmd");
  assert.equal(empty.status, 0, empty.output);
  assert.equal(f.report("pmd.txt").trim(), "");
  assert.doesNotMatch(f.report("pmd.xml"), /<violation\b/);
  const clean = await f.run("clean", "package", "-DskipTests");
  assert.equal(clean.status, 0, clean.output);
  assert.equal(f.report("pmd.txt").trim(), "");
});

test("invalid PMD configuration fails analysis rather than pretending the report is complete", async (t) => {
  const f = fixture(t);
  f.source("main", "MainFixture", "public class MainFixture {}");
  writeFileSync(join(f.root, "pmd-ruleset.xml"), '<ruleset name="broken">');
  const result = await f.run("package", "-DskipTests");
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /ruleset|PMD/i);
  assert.ok(!existsSync(join(f.root, "target/pmd-fixture-1.jar")));
});

test("failure to write the findings log fails packaging", async (t) => {
  const f = fixture(t);
  f.source(
    "main",
    "MainFixture",
    "public class MainFixture { public void print() { System.out.println(1); } }",
  );
  mkdirSync(join(f.root, "target/pmd.txt"), { recursive: true });
  const result = await f.run("package", "-DskipTests");
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /pmd\.txt|directory|PMD/i);
  assert.ok(!existsSync(join(f.root, "target/pmd-fixture-1.jar")));
});
