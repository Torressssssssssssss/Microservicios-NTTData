package tacos.security;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class CorrelationFilter extends OncePerRequestFilter {
  @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    String id=(String)request.getAttribute(Correlation.KEY);
    if(id==null)id=Correlation.validOrNew(request.getHeader(Correlation.HEADER));
    request.setAttribute(Correlation.KEY,id);response.setHeader(Correlation.HEADER,id);
    if(request.getRequestURI().startsWith(request.getContextPath()+"/api/") && !request.getRequestURI().startsWith(request.getContextPath()+"/api/v1/")) {
      response.setHeader("Deprecation","true");response.setHeader("Link","</openapi.yaml>; rel=\"describedby\"");
    }
    MDC.put(Correlation.KEY,id);
    try { chain.doFilter(request,response); } finally { MDC.remove(Correlation.KEY); }
  }
}
