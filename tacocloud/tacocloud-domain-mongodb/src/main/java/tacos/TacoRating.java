package tacos;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
@org.springframework.data.mongodb.core.index.CompoundIndex(name="tacorating_unique", def="{'userId':1,'tacoId':1}", unique=true)
public class TacoRating {
 @Id private String id;
 private String userId; private String tacoId; private int score;
}
