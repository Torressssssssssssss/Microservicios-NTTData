package tacos.business;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.*;
import tacos.security.*;
import tacos.api.dto.Requests;
class CorrelationAndIdempotencyTest {
  @Test void correlationIsValidatedReturnedAndMdcIsCleanedEvenOnFailure() throws Exception {
    CorrelationFilter filter=new CorrelationFilter();
    for(String header:new String[]{null,"request-valid_1","bad\nvalue","x".repeat(65)}) {
      MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/tacos");if(header!=null)request.addHeader(Correlation.HEADER,header);
      MockHttpServletResponse response=new MockHttpServletResponse();
      filter.doFilter(request,response,(r,s)->assertNotNull(org.slf4j.MDC.get(Correlation.KEY)));
      String id=response.getHeader(Correlation.HEADER);assertNotNull(id);assertTrue(id.matches("[A-Za-z0-9_-]{1,64}"));
      if("request-valid_1".equals(header))assertEquals(header,id);else java.util.UUID.fromString(id);
      assertNull(org.slf4j.MDC.get(Correlation.KEY));
    }
    assertThrows(javax.servlet.ServletException.class,()->filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(r,s)->{throw new javax.servlet.ServletException();}));
    assertNull(org.slf4j.MDC.get(Correlation.KEY));
  }
  @Test void legacyAliasAdvertisesDeprecation() throws Exception {
    MockHttpServletResponse response=new MockHttpServletResponse();new CorrelationFilter().doFilter(new MockHttpServletRequest("GET","/api/tacos"),response,(r,s)->{});
    assertEquals("true",response.getHeader("Deprecation"));assertTrue(response.getHeader("Link").contains("openapi.yaml"));
  }
  @Test void canonicalFingerprintNormalizesCouponButIncludesBusinessFields() {
    Requests.OrderCreateRequest a=new Requests.OrderCreateRequest(),b=new Requests.OrderCreateRequest();a.setCouponCode(" class ");b.setCouponCode("CLASS");
    assertEquals(IdempotentOrders.fingerprint(a),IdempotentOrders.fingerprint(b));b.setPaymentMethodId("other");assertNotEquals(IdempotentOrders.fingerprint(a),IdempotentOrders.fingerprint(b));
  }
}
