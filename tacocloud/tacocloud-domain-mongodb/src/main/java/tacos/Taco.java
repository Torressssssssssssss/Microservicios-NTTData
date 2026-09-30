package tacos;

import java.util.Date;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.rest.core.annotation.RestResource;

import lombok.Data;

@Data
@RestResource(rel = "tacos", path = "tacos")
@Document
public class Taco {

  @Id
  private String id;
  
  @NotNull
  @Size(min = 5, message = "Name must be at least 5 characters long")
  @org.springframework.data.mongodb.core.index.Indexed
  private String name;
  private java.util.Set<Ingredient.DietaryTag> dietaryTags = new java.util.HashSet<>();
  private java.util.Set<Ingredient.Allergen> allergens = new java.util.HashSet<>();
  private int spiceLevel;
  private boolean published = true;
  private boolean beverage;
  private boolean veganRequested;
  
  private Date createdAt = new Date();
  
  @Size(min=1, message="You must choose at least 1 ingredient")
  private List<Ingredient> ingredients;

}
