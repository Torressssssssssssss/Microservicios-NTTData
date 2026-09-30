package tacos.business;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.Data;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
@Configuration @EnableConfigurationProperties(BusinessConfig.Coupons.class)
public class BusinessConfig {
  @Bean public Clock businessClock(@Value("${tacocloud.time-zone:America/Mexico_City}") String zone) { return Clock.system(ZoneId.of(zone)); }
  @Data @ConfigurationProperties("tacocloud.coupons") public static class Coupons {
    private Map<String,Coupon> codes=new HashMap<>();
  }
  @Data public static class Coupon {
    public enum Type { PERCENTAGE, FIXED }
    private Type type; private BigDecimal value; private BigDecimal minimum=BigDecimal.ZERO;
    private BigDecimal maximum; private Instant startsAt; private Instant expiresAt;
  }
}
