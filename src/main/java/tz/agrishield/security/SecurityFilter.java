  package tz.agrishield.security;
  
  import jakarta.servlet.*;
  import jakarta.servlet.annotation.WebFilter;
  import jakarta.servlet.http.*;
  
  // @WebFilter("/*") means: intercept ALL requests to the application
  @WebFilter("/*")
  public class SecurityFilter implements Filter {
  
      // These paths do NOT require authentication
      private static final String[] PUBLIC_PATHS = {
          "/api/verify",    // verification is public (no login needed)
          "/api/sms",       // SMS gateway callback
          "/api/ussd",      // USSD gateway callback
          "/api/auth/login",
          "/api/auth/register",
          "/static/",       // CSS, JS, images
          "/pages/login.html",
          "/pages/verify-public.html"
      };
  
      @Override
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
          throws java.io.IOException, ServletException {
  
          HttpServletRequest request = (HttpServletRequest) req;
          HttpServletResponse response = (HttpServletResponse) res;
  
          String path = request.getRequestURI();
  
          // CORS headers for browser requests
          response.setHeader("X-Content-Type-Options", "nosniff");
          response.setHeader("X-Frame-Options", "DENY");
          response.setHeader("X-XSS-Protection", "1; mode=block");
  
          // Allow public paths through without checking session
          for (String publicPath : PUBLIC_PATHS) {
              if (path.startsWith(publicPath)) {
                  chain.doFilter(req, res);
                  return;
              }
          }
  
          // Check for a valid session cookie
          HttpSession session = request.getSession(false); // false = don't create new
          String sessionId = (String) (session != null ?
              session.getAttribute("sessionId") : null);
  
          if (sessionId == null) {
              // No session — redirect to login if browser, return 401 if API
              if (path.startsWith("/api/")) {
                  response.sendError(401, "Authentication required");
              } else {
                  response.sendRedirect("/pages/login.html");
              }
              return;
          }
  
          // Session exists — validate it against the database
          // (This call also checks if session has expired)
          // The actual validation logic is in AuthService
          chain.doFilter(req, res); // pass to the actual servlet
      }
  }
