package tacos.business;
import lombok.Data;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
public class Announcement {
  @Id private String id;
  private String text;
  private Severity severity;
  private Instant createdAt;
  private Instant expiresAt;
  private String createdBy;
  private boolean active;
  public enum Severity { INFO, WARNING, CRITICAL }
}
