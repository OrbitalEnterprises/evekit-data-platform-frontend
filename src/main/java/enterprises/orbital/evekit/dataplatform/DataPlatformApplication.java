package enterprises.orbital.evekit.dataplatform;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.oauth.AuthUtil;

public class DataPlatformApplication extends Application {
  // Property which holds the name of the persistence unit
  public static final String PROP_APP_PATH = "enterprises.orbital.evekit.dataplatform.apppath";
  public static final String DEF_APP_PATH = "http://localhost/evekit-dp";

  public DataPlatformApplication() throws IOException {
    // Populate properties
    OrbitalProperties.addPropertyFile("EveKitDataPlatform.properties");
    // Sent persistence unit
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(DataPlatformProvider.DATA_PLATFORM_PU_PROP)));
    // Set UserAccountProvider provider
    AuthUtil.setUserAccountProvider(new DataPlatformUserAccountProvider());
  }

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> resources = new HashSet<Class<?>>();
    // Local resources
    resources.add(AccountWS.class);
    resources.add(AuthenticationWS.class);
    resources.add(ReleaseWS.class);
    resources.add(TokenWS.class);
    // Swagger additions
    resources.add(io.swagger.jaxrs.listing.ApiListingResource.class);
    resources.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);
    // Return resource set
    return resources;
  }

}
