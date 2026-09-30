package tacos.business;
import tacos.web.api.ApiException;
import tacos.api.dto.Responses.OrderResponse;
import java.math.BigDecimal;
public class PriceChangedException extends ApiException {
  public final BigDecimal previousTotal;
  public final OrderResponse quote;
  public PriceChangedException(BigDecimal previousTotal,OrderResponse quote) {
    super(org.springframework.http.HttpStatus.CONFLICT,"PRICE_CHANGED_CONFIRM_REQUIRED");this.previousTotal=previousTotal;this.quote=quote;
  }
}
