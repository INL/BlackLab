package nl.inl.blacklab.server.auth;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.http.client.direct.HeaderClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.oidc.profile.creator.TokenValidator;

import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;

import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

public class AuthOIDC implements AuthMethod {

    final HeaderClient client;

    final String adminRole;

    public AuthOIDC(Map<String, String> params) {
        adminRole = Optional.ofNullable(params.get("adminRole")).orElse("admin");

        OidcConfiguration config = new OidcConfiguration();
        config.setDiscoveryURI(params.get("discoveryURI"));
        config.setClientId("unused");//params.get("clientId"));
        config.setSecret("unused");//params.get("secret"));

        // Disable CSRF
        // Since we only consume the bearer token (not generate it), there is no way to supply a CSRF token to the client.
        config.setWithState(false);
        // unused, but required to pass initialization step of oidcclient.
        config.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);



        OidcClient oidcClient = new OidcClient(config);
        oidcClient.setCallbackUrl("notused");
        oidcClient.init();
        client = new HeaderClient("Authorization", "Bearer ", oidcClient.getAuthenticator(), oidcClient.getProfileCreator());

        //        client.setName(params.get("clientId"));

//
//        final GoogleOidcClient oidcClient = new GoogleOidcClient(oidcConfiguration);
//        oidcClient.setAuthorizationGenerator((ctx, profile) -> { profile.addRole("ROLE_ADMIN"); return Optional.of(profile); });

//        final SAML2Configuration cfg = new SAML2Configuration("resource:samlKeystore.jks",
//                "pac4j-demo-passwd",
//                "pac4j-demo-passwd",
//                "resource:samltest-providers.xml");
//        cfg.setMaximumAuthenticationLifetime(3600);
//        cfg.setServiceProviderEntityId("http://localhost:8080/callback?client_name=SAML2Client");
//        cfg.setServiceProviderMetadataPath(new File("sp-metadata.xml").getAbsolutePath());
//        final SAML2Client saml2Client = new SAML2Client(cfg);
//
//        final FacebookClient facebookClient = new FacebookClient("145278422258960", "be21409ba8f39b5dae2a7de525484da8");
//        facebookClient.setCallbackUrl("https://localhost/callback");
//        final TwitterClient twitterClient = new TwitterClient("CoxUiYwQOSFDReZYdjigBA", "2kAzunH5Btc4gRSaMr7D7MkyoJ5u1VzbOOzE8rBofs");
//        // HTTP
//        final FormClient formClient = new FormClient("http://localhost:8080/loginForm.jsp", new SimpleTestUsernamePasswordAuthenticator());
//        final IndirectBasicAuthClient indirectBasicAuthClient = new IndirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator());

        // CAS
//        final CasConfiguration configuration = new CasConfiguration("https://casserverpac4j.herokuapp.com/login");
//        //final CasConfiguration configuration = new CasConfiguration("http://localhost:8888/cas/login");
//        final DefaultLogoutHandler defaultCasLogoutHandler = new DefaultLogoutHandler();
//        defaultCasLogoutHandler.setDestroySession(true);
//        configuration.setLogoutHandler(defaultCasLogoutHandler);
//        final CasProxyReceptor casProxy = new CasProxyReceptor();
//        //configuration.setProxyReceptor(casProxy);
//        final CasClient casClient = new CasClient(configuration);

        /*final DirectCasClient casClient = new DirectCasClient(configuration);
        casClient.setName("CasClient");*/

        // Strava
//        final StravaClient stravaClient = new StravaClient();
//        stravaClient.setApprovalPrompt("auto");
//        // client_id
//        stravaClient.setKey("3945");
//        // client_secret
//        stravaClient.setSecret("f03df80582396cddfbe0b895a726bac27c8cf739");
//        stravaClient.setScope("view_private");

        // REST authent with JWT for a token passed in the url as the token parameter
//        final List<SignatureConfiguration> signatures = new ArrayList<>();
//        signatures.add(new SecretSignatureConfiguration(Constants.JWT_SALT));
//        ParameterClient parameterClient = new ParameterClient("token", new JwtAuthenticator(signatures));
//        parameterClient.setSupportGetRequest(true);
//        parameterClient.setSupportPostRequest(false);

        // basic auth
//        final DirectBasicAuthClient directBasicAuthClient = new DirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator());
        /*final DirectDigestAuthClient directBasicAuthClient = new DirectDigestAuthClient((credentials, context) -> {
            final CommonProfile profile = new CommonProfile();
            final DigestCredentials digestCredentials = (DigestCredentials) credentials;
            profile.setId(digestCredentials.getToken());
            profile.addAttribute(Pac4jConstants.USERNAME, digestCredentials.getUsername());
            credentials.setUserProfile(profile);
        });
        directBasicAuthClient.setName("DirectBasicAuthClient");*/

//        final Clients clients = new Clients("http://localhost:8080/callback", oidcClient, saml2Client, facebookClient,
//                twitterClient, formClient, indirectBasicAuthClient, casClient, stravaClient, parameterClient,
//                directBasicAuthClient, new AnonymousClient(), casProxy);

//        final Config config = new Config(client);
//        final Config config = new Config(clients);
//        config.addAuthorizer("admin", new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
//        config.addAuthorizer("custom", new CustomAuthorizer());
//        config.addAuthorizer("mustBeAnon", new IsAnonymousAuthorizer<>("/?mustBeAnon"));
//        config.addAuthorizer("mustBeAuth", new IsAuthenticatedAuthorizer<>("/?mustBeAuth"));
//        config.addMatcher("excludedPath", new PathMatcher().excludeRegex("^/facebook/notprotected\\.jsp$"));

//        return config;
    }

    private static class BlackLabPac4jSessionStore implements SessionStore {
        private Map<WebContext, Map<String, Object>> store = new HashMap<>(); // eh?

        private static final BlackLabPac4jSessionStore INSTANCE = new BlackLabPac4jSessionStore();
        private BlackLabPac4jSessionStore() {}

        @Override
        public Optional<String> getSessionId(WebContext context, boolean createSession) {
            return Optional.of(((BlackLabUserRequestWebContext) context).getNativeRequest().getSessionId());
        }

        @Override
        public Optional<Object> get(WebContext context, String key) {
            return Optional.ofNullable(store.get(context)).map(m -> m.get(key));
        }

        @Override
        public void set(WebContext context, String key, Object value) {
            store.computeIfAbsent(context, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public boolean destroySession(WebContext context) {
            return store.remove(context) != null;
        }

        @Override
        public Optional<Object> getTrackableSession(WebContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionStore> buildFromTrackableSession(WebContext context, Object trackableSession) {
            return Optional.empty();
        }

        @Override
        public boolean renewSession(WebContext context) {
            return false;
        }
    }

    private static class BlackLabUserRequestWebContext implements WebContext {
        private final UserRequest req;
        public BlackLabUserRequestWebContext(UserRequest req) {
            this.req = req;
        }

        /** Tunnel from pac4j context to our own request object. */
        public UserRequest getNativeRequest() {
            return req;
        }

        @Override
        public Optional<String> getRequestParameter(String name) {
            return Optional.ofNullable(req.getParameter(name));
        }

        @Override
        public Map<String, String[]> getRequestParameters() {
            return req.getParameters();
        }

        @Override
        public Optional<Object> getRequestAttribute(String name) {
            return Optional.of(req.getAttribute(name));
        }

        @Override
        public void setRequestAttribute(String name, Object value) {
            throw new NotImplementedException("setRequestAttribute");
        }

        @Override
        public Optional<String> getRequestHeader(String name) {
            return Optional.ofNullable(req.getHeader(name));
        }

        @Override
        public String getRequestMethod() {
            throw new NotImplementedException("getRequestMethod");
        }

        @Override
        public String getRemoteAddr() {
            return req.getRemoteAddr();
        }

        @Override
        public void setResponseHeader(String name, String value) {
            throw new NotImplementedException("setResponseHeader");
        }

        @Override
        public Optional<String> getResponseHeader(String name) {
            return Optional.ofNullable(req.getHeader(name));
        }

        @Override
        public void setResponseContentType(String content) {
            throw new NotImplementedException("setResponseContentType");
        }

        @Override
        public String getServerName() {
            return URI.create(req.getHeader("Host")).getHost();
        }

        @Override
        public int getServerPort() {
            return URI.create(req.getHeader("Host")).getPort();
        }

        @Override
        public String getScheme() {
            return URI.create(req.getHeader("Host")).getScheme();
        }

        @Override
        public boolean isSecure() {
            return getScheme().equals("https");
        }

        @Override
        public String getFullRequestURL() {
            throw new NotImplementedException("getFullRequestURL");
        }

        @Override
        public Collection<Cookie> getRequestCookies() {
            throw new NotImplementedException("getRequestCookies");
        }

        @Override
        public void addResponseCookie(Cookie cookie) {
            throw new NotImplementedException("addResponseCookie");
        }

        @Override
        public String getPath() {
            throw new NotImplementedException("getPath");
        }
    }

    @Override
    public User determineCurrentUser(UserRequest request) {
        // For some info on how Pac4j works internally, see:
        // https://www.pac4j.org/blog/jee_pac4j_vs_pac4j_jee.html
        // Pac4j is an abstracted library, so we need to include a specific implementation.
        // in our case the wslib is meant to be abstracted away from a specific web framework, so we have to write some of our own code.

        String header = request.getHeader("Authorization");
        if (header == null || header.isEmpty())
            return User.anonymous(request.getSessionId());

        Credentials credentials = new TokenCredentials(header.substring("Bearer ".length()));
        // for profile creation code see OidcProfileCreator.java
        Optional<UserProfile> profile = client.getUserProfile(credentials, new BlackLabUserRequestWebContext(request),
            BlackLabPac4jSessionStore.INSTANCE);

        // credentials are not checked.
        // The access token is considered valid if it is present and not expired.
        return profile.map(p -> (OidcProfile) p).map(p -> {
            // We require the email scope to be present. This can be done by including the "email" scope when requesting the access token.
            // (see https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims)
            if (p.getEmail() == null || p.getEmail().isBlank())
                throw new RuntimeException("No email address in OIDC profile");

            boolean isAdmin = p.getRoles().contains(adminRole);
            return isAdmin ? User.superuser(request.getSessionId()) : User.loggedIn(p.getEmail(), request.getSessionId());
        })
        .orElse(User.anonymous(request.getSessionId()));

    }
}
