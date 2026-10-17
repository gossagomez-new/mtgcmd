import test from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  existsSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";

const repository = fileURLToPath(new URL("../", import.meta.url));
const pom = readFileSync(join(repository, "backend/pom.xml"), "utf8");
const plugins = pom.match(/<plugin>[\s\S]*?<\/plugin>/g) ?? [];
const plugin = (name) =>
  plugins.find((value) => value.includes(`<artifactId>${name}</artifactId>`)) ??
  "";

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "mtgcmd coverage fixture "));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  writeFileSync(
    join(root, "pom.xml"),
    `<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>${pom.match(/<parent>[\s\S]*?<\/parent>/)[0]}
    <groupId>dev.mtgcmd</groupId><artifactId>coverage-fixture</artifactId><version>1</version>
    ${pom.match(/<properties>[\s\S]*?<\/properties>/)[0]}
    <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency></dependencies>
    <build><plugins>${plugin("jacoco-maven-plugin")}${plugin("maven-surefire-plugin")}</plugins></build>
  </project>`,
  );
  function source(scope, name, text) {
    const directory = join(root, "src", scope, "java", "fixture");
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, `${name}.java`), `package fixture;\n${text}`);
  }
  source(
    "main",
    "CoverageFixture",
    `public record CoverageFixture(int value) {
    public int result(boolean doubled) { return doubled ? value * 2 : value; }
    public int unused() { return -1; }
  }`,
  );
  source(
    "test",
    "CoverageFixtureTest",
    `import org.junit.jupiter.api.Test;
    import static org.junit.jupiter.api.Assertions.*;
    public class CoverageFixtureTest {
      @Test void exercised() {
        assertEquals(6, new CoverageFixture(3).result(true));
        assertTrue(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
          .contains("--enable-native-access=ALL-UNNAMED"));
        assertEquals("preserved", System.getProperty("coverage.fixture.marker"));
      }
    }`,
  );
  function run(...args) {
    return new Promise((resolve, reject) => {
      const child = spawn(
        "mvn",
        [
          "-B",
          "-q",
          "-f",
          join(root, "pom.xml"),
          "-DargLine=-Dcoverage.fixture.marker=preserved",
          ...args,
        ],
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
  const path = (name) => join(root, "target", name);
  return {
    root,
    source,
    run,
    path,
    read: (name) => readFileSync(path(name), "utf8"),
  };
}

function reports(f) {
  assert.ok(
    existsSync(f.path("jacoco.exec")),
    "instrumented tests must produce execution data",
  );
  assert.match(f.read("site/jacoco/index.html"), /JaCoCo/);
  const xml = f.read("site/jacoco/jacoco.xml");
  assert.match(xml, /class name="fixture\/CoverageFixture"/);
  assert.doesNotMatch(xml, /class name="fixture\/CoverageFixtureTest"/);
  assert.match(
    xml,
    /<counter type="INSTRUCTION" missed="[1-9]\d*" covered="[1-9]\d*"/,
  );
  assert.match(xml, /method name="unused"/);
  assert.match(f.read("site/jacoco/jacoco.csv"), /CoverageFixture/);
}

test("Java 25 test and verify preserve JVM arguments and report uncovered production code without a gate", async (t) => {
  const f = fixture(t);
  f.source(
    "test",
    "CoverageFixtureTest",
    `public class CoverageFixtureTest {
    @org.junit.jupiter.api.Test void exercised() {
      org.junit.jupiter.api.Assertions.assertEquals(6, new CoverageFixture(3).result(true));
    }
  }`,
  );
  const tested = await f.run("clean", "test");
  assert.equal(tested.status, 0, tested.output);
  assert.ok(
    existsSync(f.path("jacoco.exec")),
    "test lifecycle must collect coverage",
  );
  assert.ok(
    !existsSync(f.path("site/jacoco/index.html")),
    "reports belong to verify",
  );
  const verified = await f.run("verify");
  assert.equal(verified.status, 0, verified.output);
  reports(f);
  const jar = spawnSync("jar", ["tf", f.path("coverage-fixture-1.jar")], {
    encoding: "utf8",
  });
  assert.equal(jar.status, 0, jar.stderr);
  assert.doesNotMatch(
    jar.stdout,
    /org\/jacoco|jacoco\.exec|site\/jacoco|CoverageFixtureTest/,
  );
});

test("non-clean skipped tests can reuse stale data while clean skipped builds have no coverage", async (t) => {
  const f = fixture(t);
  const initial = await f.run("clean", "verify");
  assert.equal(initial.status, 0, initial.output);
  const data = readFileSync(f.path("jacoco.exec"));
  const csv = f.read("site/jacoco/jacoco.csv");
  f.source(
    "test",
    "CoverageFixtureTest",
    `public class CoverageFixtureTest {
    @org.junit.jupiter.api.Test void failsIfExecuted() { org.junit.jupiter.api.Assertions.fail("must be skipped"); }
  }`,
  );
  const stale = await f.run("verify", "-DskipTests");
  assert.equal(stale.status, 0, stale.output);
  assert.deepEqual(readFileSync(f.path("jacoco.exec")), data);
  assert.equal(f.read("site/jacoco/jacoco.csv"), csv);
  for (const flag of ["-DskipTests", "-Dmaven.test.skip=true"]) {
    const skipped = await f.run("clean", "verify", flag);
    assert.equal(skipped.status, 0, skipped.output);
    assert.ok(!existsSync(f.path("jacoco.exec")));
    assert.ok(!existsSync(f.path("site/jacoco/index.html")));
    assert.ok(existsSync(f.path("coverage-fixture-1.jar")));
  }
});

test("disabled instrumentation still runs tests with native and caller arguments and no unresolved agent token", async (t) => {
  const f = fixture(t);
  const result = await f.run("clean", "verify", "-Djacoco.skip=true");
  assert.equal(result.status, 0, result.output);
  assert.ok(
    existsSync(f.path("surefire-reports/TEST-fixture.CoverageFixtureTest.xml")),
  );
  assert.ok(!existsSync(f.path("jacoco.exec")));
  assert.ok(!existsSync(f.path("site/jacoco/index.html")));
});

test("test failures and report write failures remain blocking", async (t) => {
  const failed = fixture(t);
  failed.source(
    "test",
    "CoverageFixtureTest",
    `public class CoverageFixtureTest {
    @org.junit.jupiter.api.Test void fails() { org.junit.jupiter.api.Assertions.fail("fixture failure"); }
  }`,
  );
  const result = await failed.run("clean", "verify");
  assert.notEqual(result.status, 0, result.output);
  assert.ok(!existsSync(failed.path("coverage-fixture-1.jar")));
  assert.ok(!existsSync(failed.path("site/jacoco/index.html")));
  const unwritable = fixture(t);
  mkdirSync(unwritable.path("site/jacoco/jacoco.xml"), { recursive: true });
  const reportFailure = await unwritable.run("verify");
  assert.notEqual(reportFailure.status, 0, reportFailure.output);
  assert.match(reportFailure.output, /jacoco|coverage|directory/i);
});

test("corrupt execution data fails standalone reporting", async (t) => {
  const f = fixture(t);
  const compiled = await f.run("compile");
  assert.equal(compiled.status, 0, compiled.output);
  writeFileSync(f.path("jacoco.exec"), "not execution data");
  const result = await f.run("jacoco:report");
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /execution|jacoco|header/i);
});

test("coverage is build-only, reporting-only, and retained with fixture checks in CI and local verification", () => {
  const jacoco = plugin("jacoco-maven-plugin");
  assert.match(jacoco, /<version>0\.8\.15<\/version>/);
  assert.doesNotMatch(jacoco, /<goal>check<\/goal>|<excludes>|<rules>/);
  assert.doesNotMatch(
    pom.match(/<dependencies>[\s\S]*?<\/dependencies>/)[0],
    /org\.jacoco/,
  );
  const workflow = readFileSync(
    join(repository, ".github/workflows/verify.yml"),
    "utf8",
  );
  assert.match(workflow, /backend\/target\/site\/jacoco\//);
  assert.match(workflow, /backend\/target\/jacoco\.exec/);
  assert.match(workflow, /scripts\/jacoco\.test\.mjs/);
  assert.match(
    readFileSync(join(repository, "scripts/verification_profiles.py"), "utf8"),
    /scripts\/jacoco\.test\.mjs/,
  );
});
