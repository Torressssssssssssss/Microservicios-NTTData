package tacos;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
@org.springframework.data.mongodb.core.index.CompoundIndex(name="reorderrecord_unique", def="{'userId':1,'key':1}", unique=true)
public class ReorderRecord {
 @Id private String id;
 private String userId; private String key; private String requestHash; private String orderId;
}
