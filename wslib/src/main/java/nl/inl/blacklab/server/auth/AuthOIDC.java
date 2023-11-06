package nl.inl.blacklab.server.auth;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.WebContextFactory;
import org.pac4j.core.context.WebContextHelper;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.matching.matcher.Matcher;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.OidcCredentials;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.AccessTokenType;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.BearerTokenError;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.UserRequest;

public class AuthOIDC implements AuthMethod {

    final OidcConfiguration oidcConfiguration;
    final OidcClient client;

    public AuthOIDC(Map<String, String> params) {
        oidcConfiguration = new OidcConfiguration();
        oidcConfiguration.setDiscoveryURI(params.get("discoveryURI"));
        oidcConfiguration.setClientId(params.get("clientId"));
        oidcConfiguration.setSecret(params.get("secret"));
        oidcConfiguration.setUseNonce(true);
        //oidcClient.setPreferredJwsAlgorithm(JWSAlgorithm.RS256);
//        oidcConfiguration.addCustomParam("prompt", "consent");

        // hmmm.
//        KeycloakOidcClient oidcClient = new KeycloakOidcClient(oidcConfiguration);
        client = new OidcClient(oidcConfiguration);




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

    private static class BlackLabUserRequestWebContext implements WebContext {
        private final UserRequest req;
        public BlackLabUserRequestWebContext(UserRequest req) {
            this.req = req;
        }

        @Override
        public Optional<String> getRequestParameter(String name) {
            return Optional.ofNullable(req.getParameter(name));
        }

        @Override
        public Map<String, String[]> getRequestParameters() {
            throw new NotImplementedException("getRequestParameters");
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
        // (the javaee one, and in the future, the jakarta-ee one)
        try {
            String header = request.getHeader("Authorization");
            if (header == null || header.isEmpty())
                return User.anonymous(request.getSessionId());

            OidcCredentials creds = new OidcCredentials();
            creds.setAccessToken(BearerAccessToken.parse(header));
            Optional<UserProfile> profile = client.getUserProfile(creds, new BlackLabUserRequestWebContext(request), JEESessionStore.INSTANCE);
            return profile.map(p -> {
                if (p.getRoles().contains("admin")) return User.superuser(request.getSessionId());
                return User.loggedIn(p.getId(), request.getSessionId());
            })
            .orElse(User.anonymous(request.getSessionId()));
        } catch (ParseException e) {
            throw new RuntimeException("Invalid Authorization header");
        }
    }
}
