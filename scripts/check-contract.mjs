import { readFileSync } from "node:fs";
import process from "node:process";

const generatedPath = "backend/build/generated/openapi.json";
const committedPath = "docs/api/openapi.json";

function canonical(path) {
  return JSON.stringify(JSON.parse(readFileSync(path, "utf8")));
}

try {
  if (canonical(generatedPath) !== canonical(committedPath)) {
    console.error("OpenAPI contract drift detected. Regenerate docs/api/openapi.json and the frontend schema.");
    process.exit(1);
  }
} catch (error) {
  console.error(`OpenAPI contract check failed: ${error.message}`);
  process.exit(1);
}
