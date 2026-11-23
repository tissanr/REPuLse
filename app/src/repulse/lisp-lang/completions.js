import { completeFromList } from "@codemirror/autocomplete";
import { BUILTINS } from "./builtins.generated.js";

// The name list is generated from app/src/repulse/content/builtin_meta.edn
// (npm run gen:ai-docs). Do not add names here.

export { BUILTINS };

export const builtinCompletions = completeFromList(BUILTINS);
