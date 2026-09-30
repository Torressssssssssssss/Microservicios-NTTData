package tacos;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
public class ProcessedEvent {
 @Id private String id;
 private java.time.Instant processedAt; private String result;
}
