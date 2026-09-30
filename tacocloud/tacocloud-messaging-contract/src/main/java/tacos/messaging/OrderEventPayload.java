package tacos.messaging;
import lombok.Data;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@Data @JsonIgnoreProperties(ignoreUnknown=true)
public class OrderEventPayload {
  private String orderId;
  private String status;
  private long orderVersion;
  private List<Item> items=new ArrayList<>();
  @Data @JsonIgnoreProperties(ignoreUnknown=true) public static class Item {
    private String name; private int quantity; private List<String> ingredients;
  }
}
