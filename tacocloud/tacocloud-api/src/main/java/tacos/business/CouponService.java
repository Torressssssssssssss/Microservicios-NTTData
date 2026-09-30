package tacos.business;
import java.math.*;
import java.time.*;
import java.util.Locale;
import org.springframework.stereotype.Service;
import tacos.web.api.ApiException;
@Service
public class CouponService {
  private final BusinessConfig.Coupons properties; private final Clock clock;
  public CouponService(BusinessConfig.Coupons properties, Clock clock) { this.properties=properties; this.clock=clock; }
  public String normalize(String code) { return code==null || code.trim().isEmpty() ? null : code.trim().toUpperCase(Locale.ROOT); }
  public BigDecimal discount(String code, BigDecimal subtotal) {
    code=normalize(code); if(code==null) return BigDecimal.ZERO.setScale(2);
    BusinessConfig.Coupon c=properties.getCodes().get(code); Instant now=clock.instant();
    if(c==null || c.getType()==null || c.getValue()==null || c.getValue().signum()<0
        || c.getStartsAt()==null || c.getExpiresAt()==null || now.isBefore(c.getStartsAt()) || !now.isBefore(c.getExpiresAt())
        || subtotal.compareTo(c.getMinimum())<0) throw ApiException.invalid("COUPON_NOT_APPLICABLE");
    BigDecimal amount=c.getType()==BusinessConfig.Coupon.Type.FIXED ? c.getValue() : subtotal.multiply(c.getValue()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
    if(c.getMaximum()!=null) amount=amount.min(c.getMaximum().max(BigDecimal.ZERO));
    return amount.min(subtotal).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
  }
}
