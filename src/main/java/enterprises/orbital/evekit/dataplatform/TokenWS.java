package enterprises.orbital.evekit.dataplatform;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.*;
import org.apache.http.client.utils.URIBuilder;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/ws/v1/token")
@Consumes({
    "application/json"
})
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Token"
},
    produces = "application/json",
    consumes = "application/json")
public class TokenWS {
  private static final Logger log = Logger.getLogger(TokenWS.class.getName());

  // EVE SSO Client ID and Secret to be used for token handling.  These are normally different than the client ID
  // and secret we use for EveKit authentication because the callback path is different and EVE SSO is
  // strict about callback path.
  public static final String PROP_TOKEN_EVE_CLIENT_ID = "enterprises.orbital.token.eve_client_id";
  public static final String PROP_TOKEN_EVE_SECRET_KEY = "enterprises.orbital.token.eve_secret_key";
  // The EVE SSO verify URL is generic so we can use whatever authentication is using.
  public static final String PROP_EVE_VERIFY_URL = "enterprises.orbital.auth.eve_verify_url";
  // Location of ESI server
  public static final String PROP_ESI_SERVER_PATH = "enterprises.orbital.esiServerPath";
  public static final String DEF_ESI_SERVER_PATH = "https://esi.tech.ccp.is/latest";

  @Path("/token_list")
  @GET
  @ApiOperation(
      value = "Get list of ESI tokens for the currently authenticated user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of ESI tokens",
              response = ESIToken.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "user not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response getTokenList(
      @Context HttpServletRequest request) {
    // Retrieve user and verify as needed
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve all tokens
    List<ESIToken> result = new ArrayList<ESIToken>();
    for (ESIToken next : ESIToken.getAllKeys(user)) {
      next.updateValid();
      result.add(next);
    }
    // Finish
    return Response.ok().entity(result).build();
  }

  @Path("/delete_token/{kid}")
  @DELETE
  @ApiOperation(
      value = "Delete an ESI token.",
      notes = "Delete the specified token if owned by the authenticated user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "ESI token deleted successfully"),
          @ApiResponse(
              code = 401,
              message = "requestor not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "specified ESI token not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response deleteToken(
      @Context HttpServletRequest request,
      @PathParam("kid") @ApiParam(
          name = "kid",
          required = true,
          value = "ID of ESI token to be deleted") long kid) {
    // Retrieve user and verify as needed
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Find target token, error if not found
    ESIToken toDelete = ESIToken.getKeyByID(kid);
    if (toDelete == null || !toDelete.getUserAccount().equals(user)) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target ESI token not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Delete and return
    if (!ESIToken.deleteKey(user, kid)) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error deleting ESI token, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  protected Response startTokenFlow(HttpServletRequest request, DataPlatformUserAccount user, String scope, ESIToken existing)
      throws IOException, URISyntaxException {
    // Start new or re-authenticated token flow
    URIBuilder builder = AuthenticationWS.makeStandardBuilder(request);
    builder.setPath(builder.getPath() + "api/ws/v1/token/token_callback");
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_SECRET_KEY);
    String eScope = existing == null ? scope : existing.getScopes();
    long eKid = existing == null ? -1 : existing.getKid();
    String redirect = ESITokenManager.createToken(request, user, eScope, builder.toString(), eKid,
                                                  eveClientID, eveSecretKey);
    if (redirect == null) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error creating ESI token, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    // Success, return redirect allowing client to complete authentication
    final String redirectResult = redirect;
    return Response.ok().entity(new Object() {
      @SuppressWarnings("unused")
      public final String newLocation = redirectResult;
    }).build();
  }


  @Path("/create_token")
  @POST
  @ApiOperation(
      value = "Create an ESI token.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "token creation process started.  Result will be an object with a 'newLocation' field containing a redirect"),
          @ApiResponse(
              code = 401,
              message = "requestor not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "illegal scope value.",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response createToken(
      @Context HttpServletRequest request,
      @ApiParam(
          name = "scope",
          required = true,
          value = "Space separated list of scopes for new token") String scope) throws IOException, URISyntaxException {
    // Verify post argument
    if (scope == null || scope.length() == 0) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "illegal scope argument");
      return Response.status(Status.FORBIDDEN).entity(errMsg).build();
    }
    // Retrieve user and verify as needed
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Enforce total token limit
    int currentKeyCount = ESIToken.getAllKeys(user).size();
    int tokenLimit = (int) OrbitalProperties.getLongGlobalProperty(ESITokenManager.PROP_TOTAL_TOKEN_LIMIT,
                                                                   ESITokenManager.DEF_TOTAL_TOKEN_LIMIT);
    if (currentKeyCount >= tokenLimit) {
      ServiceError errMsg = new ServiceError(
          Status.UNAUTHORIZED.getStatusCode(),
          "you already have the maximum number of tokens which is " + tokenLimit);
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Start new token creation flow
    return startTokenFlow(request, user, scope, null);
  }

  @Path("/reauth_token/{kid}")
  @GET
  @ApiOperation(
      value = "Re-authenticate an existing ESI token.",
      notes = "Re-authenticate the specified token if owned by the authenticated user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "token re-authentication process started.  Result will be an object with a 'newLocation' field containing a redirect"),
          @ApiResponse(
              code = 401,
              message = "requestor not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "illegal scope value.",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response reauthToken(
      @Context HttpServletRequest request,
      @PathParam("kid") @ApiParam(
          name = "kid",
          required = true,
          value = "ID of ESI token to be re-authenticated") long kid) throws IOException, URISyntaxException {
    // Retrieve user and verify as needed
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Find target token, error if not found
    ESIToken toAuth = ESIToken.getKeyByID(kid);
    if (toAuth == null || !toAuth.getUserAccount().equals(user)) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target ESI token not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Start token re-authentication creation flow
    return startTokenFlow(request, user, null, toAuth);
  }

  @Path("/token_callback")
  @GET
  @ApiOperation(
      value = "Handle OAuth callback for ESI token creation.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect back to main site."),
          @ApiResponse(
              code = 400,
              message = "Unable to complete authentication.")
      })
  public Response tokenCallback(
      @Context HttpServletRequest req)
      throws IOException, URISyntaxException {
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_SECRET_KEY);
    String eveVerifyURL = OrbitalProperties.getGlobalProperty(PROP_EVE_VERIFY_URL);
    URIBuilder builder = AuthenticationWS.makeStandardBuilder(req);
    builder.setFragment("account/token");
    if (ESITokenManager.processTokenCallback(req, eveVerifyURL, eveClientID, eveSecretKey)) {
      // Token creation or re-authentication completed properly, redirect
      return Response.temporaryRedirect(new URI(builder.toString())).build();
    }
    // Otherwise, failed to complete, redirect with an error message
    String err = "Error while creating or re-authenticating ESI token.  Please retry.  If the problem perists, please contact the site admin.";
    builder.addParameter("auth_error", err);
    return Response.temporaryRedirect(new URI(builder.toString())).build();
  }

  @Path("/get_esi_scopes")
  @GET
  @ApiOperation(
      value = "Get available scopes from the ESI servers.",
      notes = "Retrieve map of available scopes from the ESI servers.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "map retrieved successfully"),
          @ApiResponse(
              code = 500,
              message = "internal service error",
              response = ServiceError.class),
      })
  public Response getESIScopes(
      @Context HttpServletRequest request) {
    // Retrieve swagger.json from server
    JsonObject data = null;
    try {
      String serverPath = OrbitalProperties.getGlobalProperty(PROP_ESI_SERVER_PATH, DEF_ESI_SERVER_PATH) + "/swagger.json";
      URL               target = new URL(serverPath);
      HttpURLConnection conn;
      conn = (HttpURLConnection) target.openConnection();
      conn.setUseCaches(true);
      javax.json.JsonReader reader = javax.json.Json.createReader(new InputStreamReader(target.openStream()));
      data = reader.readObject();
      reader.close();
    } catch (IOException e) {
      log.log(Level.WARNING, "Failed to retrieve scope list", e);
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "failed to retrieve and parse swagger.json");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    // Extract security object and scopes
    Map<String, String> scopeMap = new HashMap<String, String>();
    JsonObject          rawMap   = data.getJsonObject("securityDefinitions").getJsonObject("evesso").getJsonObject("scopes");
    for (Map.Entry<String, JsonValue> next : rawMap.entrySet()) {
      scopeMap.put(next.getKey(), next.getValue().toString());
    }
    // Return result
    return Response.ok().entity(scopeMap).build();
  }


}
