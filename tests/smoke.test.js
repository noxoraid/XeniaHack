import { describe, expect, it } from "vitest";
import { TestDriver } from "testdriverai/vitest/hooks";

describe("Smoke Test", () => {
  it("loads the sample application", async (context) => {
    const testdriver = TestDriver(context);

    await testdriver.provision.chrome({ url: "https://example.com" });

    const assertResult = await testdriver.assert(
      "The Example Domain page is visible with the text 'This domain is for use in documentation examples'"
    );
    expect(assertResult).toBeTruthy();
  });
});
