import { createClient } from "@supabase/supabase-js";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import twilio from "twilio";

const WEB_ALLOWED_ROLES = new Set(["admin", "regulator", "manufacturer", "distributor", "dealer"]);

export function json(res, status, body) {
  res.status(status).json(body);
}

export function readBody(req) {
  if (!req.body) return {};
  if (typeof req.body === "string") {
    try {
      return JSON.parse(req.body);
    } catch {
      return {};
    }
  }
  return req.body;
}

export function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

export function isStrongPassword(password) {
  const value = String(password || "");
  if (value.length < 8) return false;
  const hasUpper = /[A-Z]/.test(value);
  const hasLower = /[a-z]/.test(value);
  const hasDigit = /\d/.test(value);
  const hasSpecial = /[^A-Za-z0-9]/.test(value);
  return hasUpper && hasLower && hasDigit && hasSpecial;
}

export function supabaseAdmin() {
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) {
    throw new Error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(url, key, { auth: { persistSession: false } });
}

export function hashPassword(password) {
  return bcrypt.hashSync(password, 10);
}

export function verifyPassword(password, hash) {
  return bcrypt.compareSync(String(password || ""), String(hash || ""));
}

export function randomCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

export function nowIso() {
  return new Date().toISOString();
}

export function plusMinutesIso(minutes) {
  return new Date(Date.now() + minutes * 60 * 1000).toISOString();
}

export function isExpired(iso) {
  return new Date(iso).getTime() < Date.now();
}

export function isDebugEnabled() {
  return String(process.env.AUTH_DEBUG_RESET_CODE || "true").trim().toLowerCase() === "true";
}

export function signSession(user) {
  const secret = process.env.SESSION_SECRET || "dev-only-change-this";
  return jwt.sign(
    {
      id: user.id,
      email: user.email,
      fullName: user.full_name,
      role: user.role,
      verified: !!user.verified,
      authProvider: user.auth_provider || "password",
    },
    secret,
    { expiresIn: "8h" }
  );
}

export function parseSession(req) {
  const cookies = req.headers.cookie || "";
  const token = cookies
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith("agri_session="))
    ?.split("=")[1];

  if (!token) return null;
  const secret = process.env.SESSION_SECRET || "dev-only-change-this";
  try {
    return jwt.verify(token, secret);
  } catch {
    return null;
  }
}

export function setSessionCookie(res, token) {
  const secure = process.env.NODE_ENV === "production" ? "; Secure" : "";
  res.setHeader(
    "Set-Cookie",
    `agri_session=${token}; HttpOnly; Path=/; Max-Age=28800; SameSite=Lax${secure}`
  );
}

export function clearSessionCookie(res) {
  const secure = process.env.NODE_ENV === "production" ? "; Secure" : "";
  res.setHeader("Set-Cookie", `agri_session=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax${secure}`);
}

export function ensureRoleAllowed(role) {
  return WEB_ALLOWED_ROLES.has(String(role || "").toLowerCase());
}

export async function sendSms(phone, message) {
  const enabled = String(process.env.TWILIO_ENABLED || "false").trim().toLowerCase() === "true";
  const sid = String(process.env.TWILIO_ACCOUNT_SID || "").trim();
  const token = String(process.env.TWILIO_AUTH_TOKEN || "").trim();
  const from = String(process.env.TWILIO_PHONE_NUMBER || "").trim();

  if (!enabled || !sid || !token || !from || !phone) {
    return false;
  }

  const client = twilio(sid, token);
  await client.messages.create({ to: phone, from, body: message });
  return true;
}
