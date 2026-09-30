package tacos;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class OrderLine {
  private Taco taco;
  private int quantity;
  private BigDecimal unitPriceAtPurchase;
  private BigDecimal subtotal;
}
