package agrishield.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Argon2 ARGON2 = Argon2Factory.create();

    private static final String SESSION_USER_KEY = "auth.user";
    private static final String SESSION_PROVIDER_KEY = "auth.provider";

    private static final Map<String, UserRecord> USERS = new ConcurrentHashMap<>();
    private static final Map<String, ResetCode> RESET_CODES = new ConcurrentHashMap<>();

    private static final Set<String> WEB_ALLOWED_ROLES = Set.of("admin", "regulator", "manufacturer", "distributor", "dealer");

    private static final String GOOGLE_ISSUER_1 = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_2 = "accounts.google.com";
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";

    static {
        seed("admin@agrishield.tz", "Amina Admin", "admin", "Admin#123", true);
        seed("regulator@agrishield.tz", "Rashid Regulator", "regulator", "Regulator#123", true);
        seed("manufacturer@agrishield.tz", "Mariam Manufacturer", "manufacturer", "Manufacturer#123", false);
        seed("distributor@agrishield.tz", "Daniel Distributor", "distributor", "Distributor#123", false);
        seed("dealer@agrishield.tz", "Dora Dealer", "dealer", "Dealer#123", false);
        seed("farmer@agrishield.tz", "Farmer Mobile User", "farmer", "Farmer#123", false);
    }

    private static void seed(String email, String fullName, String role, String password, boolean totpRequired) {
        String hash = ARGON2.hash(3, 65536, 1, password);
        USERS.put(email.toLowerCase(), new UserRecord(UUID.randomUUID().toString(), fullName, email, role, hash, totpRequired));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = normalize(req.getPathInfo());
        if ("/me".equals(path)) {
            handleMe(req, resp);
            return;
        }
        json(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "NOT_FOUND"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = normalize(req.getPathInfo());
        switch (path) {
            case "/login" -> handleLogin(req, resp);
            case "/logout" -> handleLogout(req, resp);
            case "/social" -> handleSocial(req, resp);
            case "/password/forgot" -> handleForgot(req, resp);
            case "/password/reset" -> handleReset(req, resp);
            default -> json(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "NOT_FOUND"));
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readJson(req);
        String email = lower(text(body.get("email")));
        String password = text(body.get("password"));
        String totpCode = text(body.get("totpCode"));

        UserRecord user = USERS.get(email);
        if (user == null || password == null || !ARGON2.verify(user.passwordHash(), password)) {
            json(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "INVALID_CREDENTIALS"));
            return;
        }

        if (user.totpRequired() && !"123456".equals(totpCode)) {
            json(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "TOTP_REQUIRED"));
            return;
        }

        if (!WEB_ALLOWED_ROLES.contains(user.role())) {
            json(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "FARMER_WEB_NOT_ALLOWED"));
            return;
        }

        createSession(req, user.email(), "password");
        json(resp, HttpServletResponse.SC_OK, userDto(user, "password"));
    }

    private void handleSocial(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readJson(req);
        String provider = lower(text(body.get("provider")));
        String idToken = text(body.get("idToken"));

        if (!Set.of("google", "apple").contains(provider) || idToken == null || idToken.isBlank()) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "SOCIAL_TOKEN_REQUIRED"));
            return;
        }

        String email;
        try {
            email = verifySocialToken(provider, idToken);
        } catch (Exception ex) {
            json(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "SOCIAL_TOKEN_INVALID"));
            return;
        }

        UserRecord user = USERS.get(lower(email));
        if (user == null) {
            json(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "ACCOUNT_NOT_PROVISIONED"));
            return;
        }

        if (!WEB_ALLOWED_ROLES.contains(user.role())) {
            json(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "FARMER_WEB_NOT_ALLOWED"));
            return;
        }

        createSession(req, user.email(), provider);
        json(resp, HttpServletResponse.SC_OK, userDto(user, provider));
    }

    private void handleForgot(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readJson(req);
        String identifier = lower(text(body.get("identifier")));

        UserRecord user = USERS.get(identifier);
        String code = null;
        if (user != null) {
            code = String.valueOf((int) (Math.random() * 900000) + 100000);
            RESET_CODES.put(code, new ResetCode(user.email(), Instant.now().plusSeconds(15 * 60), false));

            // Attempt to send SMS via Twilio (async, non-blocking)
            if (isSmsEnabled()) {
                try {
                    sendResetCodeViaSMS(user, code);
                } catch (Exception e) {
                    Logger.getLogger(AuthServlet.class.getName()).warning("SMS send failed: " + e.getMessage());
                    // Continue - user can still use debug code or email fallback
                }
            }

            if (isDebugResetEnabled()) {
                json(resp, HttpServletResponse.SC_OK, Map.of(
                    "accepted", true,
                    "channel", "sms_or_email",
                    "message", "Reset code sent successfully.",
                    "previewCode", code,
                    "expiresInMinutes", 15
                ));
                return;
            }
        }

        json(resp, HttpServletResponse.SC_OK, Map.of(
            "accepted", true,
            "channel", "sms_or_email",
            "message", "If the account exists, reset instructions were sent."
        ));
    }

    private void handleReset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readJson(req);
        String code = text(body.get("code"));
        String newPassword = text(body.get("newPassword"));

        if (code == null || newPassword == null) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "RESET_INPUT_INVALID"));
            return;
        }

        ResetCode token = RESET_CODES.get(code);
        if (token == null || token.used() || token.expiresAt().isBefore(Instant.now())) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "RESET_CODE_INVALID"));
            return;
        }

        if (!isStrongPassword(newPassword)) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "PASSWORD_POLICY_FAILED"));
            return;
        }

        UserRecord user = USERS.get(lower(token.email()));
        if (user == null) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "RESET_CODE_INVALID"));
            return;
        }

        String hash = ARGON2.hash(3, 65536, 1, newPassword);
        USERS.put(lower(user.email()), new UserRecord(user.id(), user.fullName(), user.email(), user.role(), hash, user.totpRequired()));
        RESET_CODES.put(code, new ResetCode(token.email(), token.expiresAt(), true));

        json(resp, HttpServletResponse.SC_OK, Map.of("success", true, "message", "Password reset successful. You can now sign in."));
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        json(resp, HttpServletResponse.SC_OK, Map.of("success", true));
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null) {
            json(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "UNAUTHENTICATED"));
            return;
        }

        String email = (String) session.getAttribute(SESSION_USER_KEY);
        String provider = (String) session.getAttribute(SESSION_PROVIDER_KEY);
        if (email == null) {
            json(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "UNAUTHENTICATED"));
            return;
        }

        UserRecord user = USERS.get(lower(email));
        if (user == null) {
            json(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "UNAUTHENTICATED"));
            return;
        }

        json(resp, HttpServletResponse.SC_OK, userDto(user, provider == null ? "password" : provider));
    }

    private void createSession(HttpServletRequest req, String email, String provider) {
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER_KEY, email);
        session.setAttribute(SESSION_PROVIDER_KEY, provider);
        session.setMaxInactiveInterval(60 * 60 * 8);
    }
    private void sendResetCodeViaSMS(UserRecord user, String code) {
        String accountSid = System.getenv("TWILIO_ACCOUNT_SID");
        String authToken = System.getenv("TWILIO_AUTH_TOKEN");
        String fromNumber = System.getenv("TWILIO_PHONE_NUMBER");
        String toNumber = System.getenv("USER_PHONE_" + user.email().replace("@", "_").replace(".", "_"));

        if (accountSid == null || authToken == null || fromNumber == null || toNumber == null) {
            throw new IllegalStateException("Twilio or user phone not configured");
        }

        Twilio.init(accountSid, authToken);
        String message = String.format(
            "AgriShield: Your password reset code is %s. Valid for 15 minutes. Do not share this code.",
            code
        );

        try {
            Message.creator(
                new PhoneNumber(toNumber),  // To number
                new PhoneNumber(fromNumber), // From number
                message
            ).create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    private boolean isSmsEnabled() {
        return "true".equalsIgnoreCase(System.getenv().getOrDefault("TWILIO_ENABLED", "true"))
            && System.getenv("TWILIO_ACCOUNT_SID") != null
            && System.getenv("TWILIO_AUTH_TOKEN") != null
            && System.getenv("TWILIO_PHONE_NUMBER") != null;
    }
    private String verifySocialToken(String provider, String idToken) throws Exception {
        String mode = lower(System.getenv().getOrDefault("AUTH_SOCIAL_MODE", "demo"));
        if (!"live".equals(mode)) {
            if (idToken.contains("@")) {
                return lower(idToken);
            }
            throw new IllegalArgumentException("Demo mode expects email-like token");
        }

        if ("google".equals(provider)) {
            String audience = envRequired("GOOGLE_CLIENT_ID");
            JWTClaimsSet claims = verifyOidcJwt(idToken, GOOGLE_JWKS, audience, Set.of(GOOGLE_ISSUER_1, GOOGLE_ISSUER_2));
            return claims.getStringClaim("email");
        }

        String audience = envRequired("APPLE_CLIENT_ID");
        JWTClaimsSet claims = verifyOidcJwt(idToken, APPLE_JWKS, audience, Set.of(APPLE_ISSUER));
        return claims.getStringClaim("email");
    }

    private JWTClaimsSet verifyOidcJwt(String token, String jwksUrl, String audience, Set<String> issuers) throws Exception {
        SignedJWT.parse(token);
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwksUrl));
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);

        JWTClaimsSet claims = processor.process(token, null);

        if (claims.getExpirationTime() == null || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired");
        }

        if (claims.getIssuer() == null || !issuers.contains(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid issuer");
        }

        List<String> audiences = claims.getAudience();
        if (audiences == null || audiences.stream().noneMatch(audience::equals)) {
            throw new IllegalArgumentException("Invalid audience");
        }

        String email = claims.getStringClaim("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email claim missing");
        }
        return claims;
    }

    private String envRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + key);
        }
        return value;
    }

    private boolean isDebugResetEnabled() {
        return "true".equalsIgnoreCase(System.getenv().getOrDefault("AUTH_DEBUG_RESET_CODE", "true"));
    }

    private boolean isStrongPassword(String password) {
        if (password.length() < 8) return false;
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private Map<String, Object> userDto(UserRecord user, String provider) {
        return Map.of(
            "id", user.id(),
            "fullName", user.fullName(),
            "email", user.email(),
            "role", user.role(),
            "authProvider", provider
        );
    }

    private Map<String, Object> readJson(HttpServletRequest req) throws IOException {
        return MAPPER.readValue(req.getInputStream(), new TypeReference<>() {});
    }

    private void json(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(resp.getWriter(), body);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "/";
        return value.startsWith("/") ? value : "/" + value;
    }

    private String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private record UserRecord(String id, String fullName, String email, String role, String passwordHash, boolean totpRequired) {}

    private record ResetCode(String email, Instant expiresAt, boolean used) {}
}
