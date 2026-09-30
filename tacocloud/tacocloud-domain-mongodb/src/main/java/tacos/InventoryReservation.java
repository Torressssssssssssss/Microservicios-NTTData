package tacos;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
@org.springframework.data.mongodb.core.index.CompoundIndex(name="inventoryreservation_unique", def="{'orderId':1}", unique=true)
public class InventoryReservation {
 @Id private String id;
 private String orderId; private java.util.Map<String,Long> quantities; private boolean released;
}
