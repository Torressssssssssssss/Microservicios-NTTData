package tacos;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data @Document
@org.springframework.data.mongodb.core.index.CompoundIndex(name="favorite_unique", def="{'userId':1,'tacoId':1}", unique=true)
public class Favorite {
 @Id private String id;
 private String userId; private String tacoId;
}
