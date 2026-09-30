package tacos.business;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
public class StationSlot { @Id private String id; private String orderId; }
