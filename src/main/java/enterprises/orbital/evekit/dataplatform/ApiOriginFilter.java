package enterprises.orbital.evekit.dataplatform;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * Add standard CORS friendly headers so proxy can be called from various places.
 */
public class ApiOriginFilter implements javax.servlet.Filter {
  @Override
  public void doFilter(
                       ServletRequest request,
                       ServletResponse response,
                       FilterChain chain)
    throws IOException, ServletException {
    HttpServletResponse res = (HttpServletResponse) response;
    res.addHeader("Access-Control-Allow-Origin", "*");
    res.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
    res.addHeader("Access-Control-Allow-Headers", "Content-Type");
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}

  @Override
  public void init(
                   FilterConfig filterConfig)
    throws ServletException {}
}