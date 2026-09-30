package tacos;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;
@Data @AllArgsConstructor @NoArgsConstructor
public class StatusChange {
  private TacoOrder.Status from;
  private TacoOrder.Status to;
  private String actor;
  private Instant at;
  private String source;
  private String reason;
}
