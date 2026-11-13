const { chromium } = require(process.env.CI_PLAYWRIGHT_MODULE || "playwright");
const fs = require("node:fs");
const path = require("node:path");

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.CI_CHROMIUM_PATH ||
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1050 },
    deviceScaleFactor: 1,
  });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const output = path.resolve("commerce/docs/screenshots");
  fs.mkdirSync(output, { recursive: true });
  await page.goto("http://localhost:3000/login");
  await page.getByRole("heading", { name: "登录分析工作台" }).waitFor();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "login.png"),
    fullPage: true,
  });
  await page.getByLabel("用户名", { exact: true }).fill("admin");
  await page
    .getByLabel("密码", { exact: true })
    .fill(process.env.CI_DEMO_PASSWORD || "CommerceDemo!2026");
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.getByRole("heading", { name: "看见数据，理解经营。" }).waitFor();
  await page.locator("canvas").first().waitFor();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "overview.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "发起分析" }).click();
  await page
    .getByLabel("经营问题", { exact: true })
    .fill("比较上个月各品类的销售额和订单退款率");
  await page.getByRole("button", { name: "开始分析" }).click();
  await page
    .getByRole("heading", { name: "观察结果", exact: true })
    .waitFor({ timeout: 190000 });
  await page.locator(".evidence").first().waitFor();
  await page.waitForTimeout(700);
  const reportCharts = page.locator(".report .commerce-chart");
  for (const chart of await reportCharts.all()) {
    await chart.locator("canvas").waitFor({ timeout: 10000 });
  }
  await page.screenshot({
    path: path.join(output, "analysis.png"),
    fullPage: true,
  });
  await page.reload();
  await page.getByRole("heading", { name: "观察结果", exact: true }).waitFor();
  await page.getByRole("button", { name: "任务历史", exact: false }).click();
  await page.getByRole("heading", { name: "每次分析，都有记录。" }).waitFor();
  await page.locator(".history .status.succeeded").first().waitFor();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "history.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "指标词典", exact: false }).click();
  await page.getByRole("heading", { name: "统一口径，才能比较。" }).waitFor();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "metrics.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "管理配置", exact: false }).click();
  await page.getByRole("heading", { name: "模型、预算与数据授权。" }).waitFor();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "admin.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "经营概览", exact: false }).click();
  await page.waitForTimeout(600);
  await page.screenshot({
    path: path.join(output, "mobile.png"),
    fullPage: true,
  });
  if (
    await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth + 1,
    )
  )
    errors.push("Mobile horizontal overflow");
  const result = {
    browser: "Chromium",
    screenshots: fs.readdirSync(output),
    errors,
  };
  fs.writeFileSync(
    "commerce/evaluation/browser-results.json",
    JSON.stringify(result, null, 2) + "\n",
  );
  console.log(JSON.stringify(result));
  await browser.close();
  if (errors.length) process.exitCode = 1;
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
