import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  workers: 1,
  timeout: 90000,
  use: {
    baseURL: "http://127.0.0.1:5173",
    viewport: { width: 1500, height: 1000 },
    launchOptions: { executablePath: process.env.CHROME_PATH },
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command:
        "mvn -f ../backend/pom.xml -q test-compile exec:java -Dexec.mainClass=dev.mtgcmd.testsupport.TestServer -Dexec.classpathScope=test",
      url: "http://127.0.0.1:8081/api/session",
      timeout: 180000,
    },
    {
      command: "npm run dev -- --port 5173 --strictPort",
      env: { API_PORT: "8081" },
      url: "http://127.0.0.1:5173",
      timeout: 60000,
    },
  ],
});
