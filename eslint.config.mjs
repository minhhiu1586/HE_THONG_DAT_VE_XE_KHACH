import { fileURLToPath } from "node:url";
import frontendConfig from "./apps/frontend/eslint.config.mjs";

export default [
  ...frontendConfig,
  { settings: { next: { rootDir: fileURLToPath(new URL("./apps/frontend/", import.meta.url)) } } },
];
