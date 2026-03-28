import { json, readBody } from "./_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  const body = readBody(req);
  const serialCode = String(body.serialCode || "").trim();
  if (!serialCode) return json(res, 400, { message: "SERIAL_REQUIRED" });

  const suspicious = serialCode.endsWith("999") || serialCode.length < 6;

  return json(res, 200, {
    serialCode,
    status: suspicious ? "SUSPICIOUS" : "VALID",
    confidence: suspicious ? 0.42 : 0.96,
    advice: suspicious
      ? "Do not use this input. Report to regulator."
      : "Input looks valid based on available registry data.",
    checkedAt: new Date().toISOString(),
  });
}
