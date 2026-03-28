import { json, parseSession, supabaseAdmin } from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  const session = parseSession(req);
  if (!session) return json(res, 401, { message: "UNAUTHENTICATED" });

  try {
    const supabase = supabaseAdmin();
    const [{ count: totalUsers }, { count: verifiedUsers }] = await Promise.all([
      supabase.from("users").select("id", { count: "exact", head: true }),
      supabase.from("users").select("id", { count: "exact", head: true }).eq("verified", true),
    ]);

    return json(res, 200, {
      totals: {
        users: totalUsers || 0,
        verifiedUsers: verifiedUsers || 0,
        openAnomalies: 0,
        pendingBatches: 0,
      },
      recentAnomalies: [],
    });
  } catch {
    return json(res, 200, {
      totals: { users: 0, verifiedUsers: 0, openAnomalies: 0, pendingBatches: 0 },
      recentAnomalies: [],
    });
  }
}
