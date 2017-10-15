package enterprises.orbital.evekit.dataplatform;

import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Path("/ws/v1/account")
@Consumes({
    "application/json"
})
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Account"
},
    produces = "application/json",
    consumes = "application/json")
public class AccountWS {

  @Path("/user_last_source/{uid}")
  @GET
  @ApiOperation(
      value = "Get the last user auth source used by the given user, or the currently logged in user",
      notes = "The last user auth source for the specified user, or null if the user is not logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "last user auth source, or null",
              response = DataPlatformUserAuthSource.class),
          @ApiResponse(
              code = 401,
              message = "requesting source for other than local user, but requestor not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting source for other than local user, but specified user not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response getUserLastSource(
                                    @Context HttpServletRequest request,
                                    @PathParam("uid") @ApiParam(
                                        name = "uid",
                                        required = true,
                                        value = "ID of user account for which the last source will be retrieved.  Set to -1 to retrieve for the current logged in user.") long uid) {
    // Retrieve current logged in user
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    DataPlatformUserAuthSource src = null;
    // If requesting for other than the logged in user, check admin
    if (user == null || (user.getID() != uid && uid != -1 && !user.isAdmin())) {
      ServiceError errMsg = new ServiceError(
          Status.UNAUTHORIZED.getStatusCode(), "Requesting source for other than local user, but requestor not logged in or not admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // If requesting for other than the logged in user, find user
    if (uid != -1) {
      user = DataPlatformUserAccount.getAccount(uid);
      if (user == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Requesting source for other than local user, but target user not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
    }
    // If we found an appropriate user, then look up the source
    if (user != null) {
      src = DataPlatformUserAuthSource.getLastUsedSource(user);
      if (src == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving auth source, please contact the administrator if this error persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
    }
    return Response.ok().entity(src).build();
  }

  @Path("/user")
  @GET
  @ApiOperation(
      value = "Get information about the current logged in user",
      notes = "User information about the current logged in user, or null if no user logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "logged in user, or null",
              response = DataPlatformUserAccount.class),
  })
  public Response getUser(
                          @Context HttpServletRequest request) {
    // Retrieve current logged in user
    DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    return Response.ok().entity(user).build();
  }

  @Path("/isadmin")
  @GET
  @ApiOperation(
      value = "Check whether the current user is an admin",
      notes = "Returns true if the current user is logged in and admin, false otherwise")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "admin status of logged in user",
              response = Boolean.class),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response checkAdmin(
                             @Context HttpServletRequest request) {
    // Retrieve current logged in user
    final DataPlatformUserAccount user = (DataPlatformUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    return Response.ok().entity(new Object() {
      @SuppressWarnings("unused")
      public final boolean isAdmin = user.isAdmin();
    }).build();
  }

}
