package enterprises.orbital.evekit.dataplatform;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.oauth.*;
import org.apache.http.client.utils.URIBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

/**
 * API for authentication and logging out.
 */
@Path("/ws/v1/auth")
@Produces({
    "application/json"
})
@io.swagger.annotations.Api(
    tags = {
        "Authentication"
    },
    produces = "application/json")
public class AuthenticationWS {
  private static final Logger log = Logger.getLogger(AuthenticationWS.class.getName());

  protected static URIBuilder makeStandardBuilder(
                                                  HttpServletRequest req)
    throws MalformedURLException, URISyntaxException {
    URIBuilder builder = new URIBuilder(OrbitalProperties.getGlobalProperty(DataPlatformApplication.PROP_APP_PATH, DataPlatformApplication.DEF_APP_PATH) + "/");
    return builder;
  }

  protected static String makeErrorCallback(
                                            HttpServletRequest req,
                                            String source)
    throws MalformedURLException, URISyntaxException {
    URIBuilder builder = makeStandardBuilder(req);
    builder.addParameter("auth_error",
                         "Error while authenticating with " + source + ".  Please retry.  If the problem persists, please contact the site admin.");
    return builder.toString();
  }

  protected static void loginDebugUser(
                                       String source,
                                       String screenName,
                                       HttpServletRequest req)
    throws IOException {
    UserAccount existing = AuthUtil.getCurrentUser(req);
    UserAuthSource authSource = AuthUtil.getBySourceScreenname(source, screenName);
    if (authSource != null) {
      // Already exists
      if (existing != null) {
        // User already signed in so change the associated to the current user. There may also be a redirect we should prefer.
        authSource.updateAccount(existing);
      } else {
        // Otherwise, sign in as usual.
        AuthUtil.signOn(req, authSource.getOwner(), authSource);
      }
    } else {
      // New user unless already signed in, in which case it's a new association.
      UserAccount newUser = existing == null ? AuthUtil.createNewUserAccount(false) : existing;
      authSource = AuthUtil.createSource(newUser, source, screenName, "debug user");
      if (existing == null) {
        // New user needs to sign in.
        AuthUtil.signOn(req, newUser, authSource);
      }
    }
  }

  @Path("/login/{source}")
  @GET
  @io.swagger.annotations.ApiOperation(
      value = "Authenticate using a specified source.",
      notes = "Initiate authentication using the specified source.  This will most often trigger a redirection to OAuth.")
  @io.swagger.annotations.ApiResponses(
      value = {
          @io.swagger.annotations.ApiResponse(
              code = 307,
              message = "Temporary redirect to an OAuth endpoint to initiate authentication.")
      })
  public Response login(
                        @Context HttpServletRequest req,
                        @PathParam("source") @io.swagger.annotations.ApiParam(
                            name = "source",
                            required = true,
                            value = "The source with which to authenticate.") String source)
    throws IOException, URISyntaxException {
    String redirect;
    URIBuilder builder = makeStandardBuilder(req);

    switch (source) {
    case "eve":
      if (OrbitalProperties.getBooleanGlobalProperty("enterprises.orbital.auth.eve_debug_mode", false)) {
        // In this case, skip the usual login scheme and immediately log in the user with a debug user.
        // This mode is normally only enabled for local test since EVE OAuth login doesn't work in that case.
        String eveDebugUser = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_debug_user", "eveuser");
        loginDebugUser("eve", eveDebugUser, req);
        return Response.temporaryRedirect(new URI(builder.toString())).build();
      } else {
        String eveClientID = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_client_id");
        String eveSecretKey = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_secret_key");
        builder.setPath(builder.getPath() + "api/ws/v1/auth/callback/eve");
        redirect = EVEAuthHandler.doGet(eveClientID, eveSecretKey, builder.toString(), null, null, req);
        if (redirect == null) redirect = makeErrorCallback(req, "EVE");
        log.fine("Redirecting to: " + redirect);
        return Response.temporaryRedirect(new URI(redirect)).build();
      }

    default:
      // Log but otherwise ignore.
      log.severe("Unrecognized login source: " + source);
    }

    return null;
  }

  @Path("/callback/{source}")
  @GET
  @io.swagger.annotations.ApiOperation(
      value = "Handle OAuth callback for specified source.",
      notes = "Handle OAuth callback after initial redirection to an OAuth source.")
  @io.swagger.annotations.ApiResponses(
      value = {
          @io.swagger.annotations.ApiResponse(
              code = 307,
              message = "Temporary redirect back to Jeeves site."),
          @io.swagger.annotations.ApiResponse(
              code = 400,
              message = "Unable to complete authentication.")
      })
  public Response callback(
                           @Context HttpServletRequest req,
                           @PathParam("source") @io.swagger.annotations.ApiParam(
                               name = "source",
                               required = true,
                               value = "The source with which authentication just completed.") String source)
    throws IOException, URISyntaxException {
    String redirect;
    URIBuilder builder = makeStandardBuilder(req);

    switch (source) {
    case "eve":
      String eveClientID = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_client_id");
      String eveSecretKey = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_secret_key");
      String eveVerifyURL = OrbitalProperties.getGlobalProperty("enterprises.orbital.auth.eve_verify_url");
      redirect = EVECallbackHandler.doGet(eveClientID, eveSecretKey, eveVerifyURL, builder.toString(), req);
      if (redirect == null) redirect = makeErrorCallback(req, "EVE");
      log.fine("Redirecting to: " + redirect);
      return Response.temporaryRedirect(new URI(redirect)).build();

    default:
      // Log but otherwise ignore.
      log.severe("Unrecognized callback source: " + source);
    }

    return null;
  }

  @Path("/logout")
  @GET
  @io.swagger.annotations.ApiOperation(
      value = "Logout the current logged in user.",
      notes = "Logout the current logged in user.")
  @io.swagger.annotations.ApiResponses(
      value = {
          @io.swagger.annotations.ApiResponse(
              code = 307,
              message = "Temporary redirect back to Jeeves site.")
      })
  public Response logout(
                         @Context HttpServletRequest req)
    throws IOException, URISyntaxException {
    URIBuilder builder = makeStandardBuilder(req);
    String redirect = LogoutHandler.doGet(null, builder.toString(), req);
    // This should never happen for the normal logout case.
    assert redirect != null;
    log.fine("Redirecting to: " + redirect);
    return Response.temporaryRedirect(new URI(redirect)).build();
  }

}
