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

const root = fileURLToPath(new URL("../", import.meta.url));
const pom = readFileSync(join(root, "backend/pom.xml"), "utf8");
const profiles = pom.match(/<profiles>[\s\S]*?<\/profiles>/)?.[0] ?? "";
const basePlugins =
  pom.split("<profiles>")[0].match(/<plugin>[\s\S]*?<\/plugin>/g) ?? [];
const corePlugins = basePlugins
  .filter((p) =>
    /<artifactId>(jacoco-maven-plugin|maven-surefire-plugin)<\/artifactId>/.test(
      p,
    ),
  )
  .join("\n");
const goal = "org.pitest:pitest-maven:1.30.1:mutationCoverage";

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), "mtgcmd mutation fixture "));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  writeFileSync(
    join(directory, "pom.xml"),
    `<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>${pom.match(/<parent>[\s\S]*?<\/parent>/)[0]}
    <groupId>fixture</groupId><artifactId>mutation-fixture</artifactId><version>1</version>
    ${pom.match(/<properties>[\s\S]*?<\/properties>/)[0]}
    <dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency></dependencies>
    <build><plugins>${corePlugins}</plugins></build>${profiles}
  </project>`,
  );
  function source(scope, name, body) {
    const path = join(directory, "src", scope, "java", "fixture");
    mkdirSync(path, { recursive: true });
    writeFileSync(join(path, `${name}.java`), `package fixture;\n${body}`);
  }
  source(
    "main",
    "MutationFixture",
    `public class MutationFixture {
    public static int classify(int value) { return value > 0 ? 1 : -1; }
    public static int weak(int value) { return value + 1; }
    public static int uncovered(int value) { return value * 2; }
    public static class Nested { public int value() { return 7; } }
  }`,
  );
  const tests = (strong = false) =>
    source(
      "test",
      "MutationFixtureTest",
      `
    import static org.junit.jupiter.api.Assertions.*;
    public class MutationFixtureTest {
      @org.junit.jupiter.params.ParameterizedTest
      @org.junit.jupiter.params.provider.CsvSource({"1,1", "0,-1", "-1,-1"})
      void classified(int value, int expected) { assertEquals(expected, MutationFixture.classify(value)); }
      @org.junit.jupiter.api.Test void weak() { ${strong ? "assertEquals(3, MutationFixture.weak(2));" : "MutationFixture.weak(2);"} }
      @org.junit.jupiter.api.Test void nested() { assertEquals(7, new MutationFixture.Nested().value()); }
      @org.junit.jupiter.api.Test void arguments() {
        java.util.List<String> args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        assertTrue(args.contains("--enable-native-access=ALL-UNNAMED"));
        assertEquals("preserved", System.getProperty("mutation.fixture.marker"));
        if (System.getProperty("mutation.fixture.pit") != null)
          assertTrue(args.stream().noneMatch(arg -> arg.contains("jacoco")), args.toString());
      }
    }`,
    );
  tests();
  function run(...args) {
    return new Promise((resolve, reject) => {
      const child = spawn(
        "mvn",
        ["-B", "-q", "-f", join(directory, "pom.xml"), ...args],
        { timeout: 180000 },
      );
      let output = "";
      child.stdout.on("data", (value) => {
        output += value;
      });
      child.stderr.on("data", (value) => {
        output += value;
      });
      child.on("error", reject);
      child.on("close", (status) => resolve({ status, output }));
    });
  }
  const pit = (...args) =>
    run(
      "-Pmutation",
      "-Dpit.targetClasses=fixture.MutationFixture*",
      "-Dpit.targetTests=fixture.*Test",
      "-Dpit.jvmArgs=--enable-native-access=ALL-UNNAMED,-Dmutation.fixture.marker=preserved,-Dmutation.fixture.pit=true",
      ...args,
      "test-compile",
      goal,
    );
  const target = (name) => join(directory, "target", name);
  return {
    run,
    pit,
    tests,
    source,
    target,
    xml: () => readFileSync(target("pit-reports/mutations.xml"), "utf8"),
  };
}

function mutations(xml, method) {
  return (xml.match(/<mutation\b[\s\S]*?<\/mutation>/g) ?? []).filter((m) =>
    m.includes(`<mutatedMethod>${method}</mutatedMethod>`),
  );
}

test("Java 25 JUnit 5 workers produce killed, surviving and uncovered mutants with independent JVM arguments", async (t) => {
  const f = fixture(t);
  const result = await f.pit("clean");
  assert.equal(result.status, 0, result.output);
  const xml = f.xml();
  assert.match(xml, /status=['"]KILLED['"]/);
  assert.match(xml, /status=['"]SURVIVED['"]/);
  assert.match(xml, /status=['"]NO_COVERAGE['"]/);
  assert.match(xml, /MutationFixture\$Nested/);
  assert.ok(existsSync(f.target("pit-reports/index.html")));
  assert.ok(
    !existsSync(f.target("jacoco.exec")),
    "PIT must not collect JaCoCo data",
  );
});

test("clean reruns replace weak-test survivors instead of reusing previous reports", async (t) => {
  const f = fixture(t);
  const weak = await f.pit("clean");
  assert.equal(weak.status, 0, weak.output);
  assert.ok(
    mutations(f.xml(), "weak").some((m) => /status=['"]SURVIVED['"]/.test(m)),
  );
  writeFileSync(f.target("pit-reports/stale-marker"), "previous run");
  f.tests(true);
  const strong = await f.pit("clean");
  assert.equal(strong.status, 0, strong.output);
  assert.ok(!existsSync(f.target("pit-reports/stale-marker")));
  assert.ok(mutations(f.xml(), "weak").length > 0);
  assert.ok(
    mutations(f.xml(), "weak").every((m) => /status=['"]KILLED['"]/.test(m)),
  );
});

test("failing unmutated tests and empty mutation targets are blocking", async (t) => {
  const red = fixture(t);
  red.source(
    "test",
    "MutationFixtureTest",
    `public class MutationFixtureTest {
    @org.junit.jupiter.api.Test void broken() { org.junit.jupiter.api.Assertions.fail("baseline failure"); }
  }`,
  );
  const failed = await red.pit("clean");
  assert.notEqual(failed.status, 0, failed.output);
  assert.match(
    failed.output,
    /without mutation|did not pass|failing|baseline/i,
  );
  const empty = fixture(t);
  const none = await empty.pit("clean", "-Dpit.targetClasses=missing.*");
  assert.notEqual(none.status, 0, none.output);
  assert.match(none.output, /no mutations|target classes|could not find/i);
});

test("normal verify retains JaCoCo and does not run or package PIT", async (t) => {
  const f = fixture(t);
  const result = await f.run(
    "clean",
    "verify",
    "-DargLine=-Dmutation.fixture.marker=preserved",
  );
  assert.equal(result.status, 0, result.output);
  assert.ok(existsSync(f.target("site/jacoco/index.html")));
  assert.ok(!existsSync(f.target("pit-reports")));
  const activated = await f.run(
    "-Pmutation",
    "clean",
    "verify",
    "-DargLine=-Dmutation.fixture.marker=preserved",
  );
  assert.equal(activated.status, 0, activated.output);
  assert.ok(
    !existsSync(f.target("pit-reports")),
    "profile activation alone does not invoke PIT",
  );
  assert.ok(
    !existsSync(f.target("jacoco.exec")),
    "mutation profile disables JaCoCo even without an explicit PIT goal",
  );
  const jar = spawnSync("jar", ["tf", f.target("mutation-fixture-1.jar")], {
    encoding: "utf8",
  });
  assert.equal(jar.status, 0, jar.stderr);
  assert.doesNotMatch(
    jar.stdout,
    /org\/pitest|pit-reports|MutationFixtureTest/,
  );
});

test("report output failures fail the mutation invocation", async (t) => {
  const f = fixture(t);
  mkdirSync(f.target("pit-reports/mutations.xml"), { recursive: true });
  const result = await f.pit();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /directory|report|mutations.xml/i);
});

test("PIT is pinned, opt-in, reporting-only, and project mutation CI is dispatch-only", () => {
  assert.match(profiles, /<id>mutation<\/id>/);
  assert.match(
    profiles,
    /<artifactId>pitest-maven<\/artifactId>\s*<version>1\.30\.1<\/version>/,
  );
  assert.match(
    profiles,
    /<artifactId>pitest-junit5-plugin<\/artifactId>\s*<version>1\.2\.3<\/version>/,
  );
  assert.doesNotMatch(
    profiles,
    /<executions>|<mutationThreshold>|<coverageThreshold>|<excludedClasses>/,
  );
  assert.doesNotMatch(corePlugins, /pitest-maven/);
  const workflow = readFileSync(
    join(root, ".github/workflows/mutation.yml"),
    "utf8",
  );
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /^\s+(pull_request|push|schedule):/m);
  assert.match(workflow, /contents: read/);
  assert.match(workflow, /if: always\(\)/);
  assert.match(workflow, /backend\/target\/pit-reports\//);
  assert.match(workflow, /"-Dpit.targetClasses=\$\{PIT_TARGET_CLASSES\}"/);
  assert.match(
    readFileSync(join(root, ".github/workflows/verify.yml"), "utf8"),
    /scripts\/pit\.test\.mjs/,
  );
  assert.match(
    readFileSync(join(root, "scripts/verification_profiles.py"), "utf8"),
    /scripts\/pit\.test\.mjs/,
  );
});
