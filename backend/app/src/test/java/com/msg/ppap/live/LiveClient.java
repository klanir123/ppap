package com.msg.ppap.live;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP helper of the live tests (phase 6): tokens of the technical client {@code ppap-integration} (client
 * credentials) and of demo users (authorization code + PKCE through the Keycloak login form, e.g. {@code
 * tier1.admin} with {@code CONNECTOR_ADMIN}), JSON calls, multipart upload and polling with timings.
 */
final class LiveClient {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String KEYCLOAK = System.getProperty("live.keycloak", "http://localhost:8180");
    static final Map<String, String> APPS = Map.of(
            "oem", System.getProperty("live.oem", "http://localhost:8081"),
            "tier1", System.getProperty("live.tier1", "http://localhost:8082"),
            "tier2", System.getProperty("live.tier2", "http://localhost:8083"));

    final HttpClient http = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    final Map<String, Long> timings = new LinkedHashMap<>();

    static String app(String realm) {
        return APPS.get(realm);
    }

    /** Token of the technical client {@code ppap-integration} of the realm. */
    String clientToken(String realm) throws Exception {
        String form = "grant_type=client_credentials&client_id=ppap-integration&client_secret=" + realm
                + "-integration-local-secret";
        return json(http.send(
                        HttpRequest.newBuilder(URI.create(tokenUrl(realm)))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()))
                .path("access_token")
                .asString();
    }

    /** Token of a demo user (password {@code demo}) via the login form of the SPA client (code flow + PKCE). */
    String userToken(String realm, String user) throws Exception {
        var cookies = new CookieManager();
        HttpClient browser = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirect = app(realm) + "/callback";
        String auth = KEYCLOAK + "/realms/" + realm + "/protocol/openid-connect/auth?client_id=ppap-ui"
                + "&response_type=code&scope=openid&state=live&code_challenge_method=S256&code_challenge=" + challenge
                + "&redirect_uri=" + enc(redirect);
        String page = browser.send(
                        HttpRequest.newBuilder(URI.create(auth)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .body();
        Matcher action = Pattern.compile("action=\"([^\"]+)\"").matcher(page);
        if (!action.find()) {
            throw new AssertionError("no login form for " + realm);
        }
        // Keycloak marks its cookies Secure; the JDK cookie handler would not send them over http://localhost
        String cookieHeader = cookies.getCookieStore().getCookies().stream()
                .map(HttpCookie::toString)
                .collect(Collectors.joining("; "));
        HttpResponse<String> login = browser.send(
                HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookieHeader)
                        .POST(HttpRequest.BodyPublishers.ofString("username=" + enc(user) + "&password=demo"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String location = login.headers()
                .firstValue("Location")
                .orElseThrow(() -> new AssertionError("login of " + user + " failed: " + login.statusCode()));
        Matcher code = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        if (!code.find()) {
            throw new AssertionError("no code in " + location);
        }
        String form = "grant_type=authorization_code&client_id=ppap-ui&code=" + code.group(1) + "&redirect_uri="
                + enc(redirect) + "&code_verifier=" + verifier;
        return json(http.send(
                        HttpRequest.newBuilder(URI.create(tokenUrl(realm)))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()))
                .path("access_token")
                .asString();
    }

    JsonNode get(String url, String token) throws Exception {
        return json(raw("GET", url, token, null, Map.of()));
    }

    JsonNode send(String method, String url, String token, Object body) throws Exception {
        return send(method, url, token, body, Map.of());
    }

    JsonNode send(String method, String url, String token, Object body, Map<String, String> headers) throws Exception {
        return json(raw(method, url, token, body, headers));
    }

    HttpResponse<String> raw(String method, String url, String token, Object body, Map<String, String> headers)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        headers.forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Uploads a PDF and returns the document. */
    JsonNode upload(String app, String token, Path file) throws Exception {
        byte[] content = Files.readAllBytes(file);
        String boundary = UUID.randomUUID().toString();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                        + file.getFileName() + "\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        return json(http.send(
                HttpRequest.newBuilder(URI.create(app + "/api/v1/documents"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()));
    }

    JsonNode await(String name, long start, Duration timeout, Probe probe) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode result = probe.get();
            if (result != null) {
                timings.put(name, (System.nanoTime() - start) / 1_000_000);
                return result;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timeout: " + name);
    }

    @FunctionalInterface
    interface Probe {
        JsonNode get() throws Exception;
    }

    static JsonNode json(HttpResponse<String> response) {
        if (response.statusCode() >= 300) {
            throw new AssertionError(response.request().method() + " " + response.uri() + " → " + response.statusCode()
                    + " " + response.body());
        }
        return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    static Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    static List<String> texts(JsonNode array, String field) {
        return stream(array).map(n -> n.path(field).asString()).toList();
    }

    private static String tokenUrl(String realm) {
        return KEYCLOAK + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
