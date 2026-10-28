#!/usr/bin/env node

// npm runs scripts through cmd.exe on Windows, where `./gradlew` is not an executable name, so
// every `cd backend && ./gradlew ...` shim failed before Gradle started. Resolve the wrapper for
// the host platform here and forward the arguments, keeping the package scripts shell-agnostic.
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const cwd = path.join(repoRoot, "backend");
const forwarded = process.argv.slice(2);

// cmd only understands its own quoting: double an embedded quote and wrap anything cmd would split.
const quote = (value) =>
  /[\s"&|<>^]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value;

const onWindows = process.platform === "win32";
const command = onWindows ? process.env.ComSpec ?? "cmd.exe" : "./gradlew";
const args = onWindows
  ? ["/d", "/s", "/c", `"${["gradlew.bat", ...forwarded].map(quote).join(" ")}"`]
  : forwarded;

const child = spawn(command, args, {
  cwd,
  stdio: "inherit",
  // cmd must receive the command line verbatim; re-quoting it here would double the quoting.
  windowsVerbatimArguments: onWindows,
});

child.on("error", (error) => {
  console.error(`Could not start the Gradle wrapper: ${error.message}`);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  process.exit(signal ? 1 : (code ?? 1));
});
