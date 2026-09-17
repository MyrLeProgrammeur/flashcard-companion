/* Generate Strings.kt from the web UI's i18n.js so the two cannot drift. */
const fs = require("fs"), vm = require("vm");
const ROOT = require("path").resolve(__dirname, "..");
const STATIC = ROOT + "/backend/static";
const OUT = ROOT + "/android/app/src/main/java/com/matheo/flashcardcompanion/ui/Strings.kt";

let src = fs.readFileSync(STATIC + "/i18n.js", "utf8");
src += "\n;globalThis.__I18N = I18N;";
const ctx = {
  document: { documentElement: {}, querySelectorAll: () => [] },
  localStorage: { getItem: () => null, setItem: () => {} },
  navigator: { language: "fr" },
};
ctx.window = ctx; ctx.globalThis = ctx;
vm.createContext(ctx);
vm.runInContext(src, ctx);

const I = ctx.__I18N;
const fr = I.fr, en = I.en;
const frKeys = Object.keys(fr), enKeys = Object.keys(en);
const missing = frKeys.filter(k => !(k in en));
const extra = enKeys.filter(k => !(k in fr));
console.error(`fr=${frKeys.length} en=${enKeys.length} missingInEn=${missing.length} extraInEn=${extra.length}`);
if (missing.length) console.error("MISSING IN EN: " + missing.join(", "));
if (extra.length) console.error("EXTRA IN EN: " + extra.join(", "));

// Kotlin string literal escaping: backslash, quote, and $ (string templates).
function kt(s) {
  return String(s)
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/\$/g, "\\$")
    .replace(/\r/g, "\\r")
    .replace(/\n/g, "\\n")
    .replace(/\t/g, "\\t");
}

const header = [
  "package com.matheo.flashcardcompanion.ui",
  "",
  "/**",
  " * FR/EN string table, generated from the web UI's i18n.js so the two cannot",
  " * drift apart. French is canonical.",
  " *",
  " * A {name} placeholder is substituted by [t]. A value written",
  " * \"singular|plural\" is a plural pair, selected by [tn].",
  " *",
  " * Regenerate with tools/gen_strings.js after editing i18n.js.",
  " */",
  "object Strings {",
  "    val fr: Map<String, String> = mapOf(",
];
const lines = [...header];
for (const k of frKeys) lines.push(`        "${kt(k)}" to "${kt(fr[k])}",`);
lines.push("    )", "", "    val en: Map<String, String> = mapOf(");
for (const k of enKeys) lines.push(`        "${kt(k)}" to "${kt(en[k])}",`);
lines.push("    )", "}", "");

fs.writeFileSync(OUT, lines.join("\n"));
console.error(`wrote ${OUT} (${lines.length} lines)`);
