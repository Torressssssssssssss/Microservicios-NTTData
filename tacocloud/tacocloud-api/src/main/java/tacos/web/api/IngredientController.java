package tacos.web.api;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.data.IngredientRepository;

@RestController
@RequestMapping(path="/api/ingredients", produces="application/json")
@CrossOrigin(origins="${taco.api.allowed-origin}")
public class IngredientController {

  private IngredientRepository repo;

  @Autowired
  public IngredientController(IngredientRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  public Flux<Ingredient> allIngredients() {
    return repo.findAll();
  }

  @GetMapping("/{id}")
  public Mono<Ingredient> byId(@PathVariable String id) {
    return repo.findById(id).switchIfEmpty(Mono.error(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found")));
  }

  @PutMapping(path="/{id}", consumes="application/json")
  public Mono<ResponseEntity<Ingredient>> updateIngredient(
      @PathVariable String id, @RequestBody Ingredient ingredient) {
    return Mono.defer(() -> {
      validate(ingredient);
      if (!id.equals(ingredient.getId())) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inconsistent ingredient ID"));
      }
      // La escritura solo se ejecuta cuando el framework suscribe la cadena.
      return repo.findById(id)
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found")))
          .flatMap(existing -> repo.save(ingredient))
          .map(ResponseEntity::ok);
    });
  }

  @PostMapping(consumes="application/json")
  public Mono<ResponseEntity<Ingredient>> postIngredient(
      @RequestBody Ingredient ingredient, HttpServletRequest request) {
    ServletUriComponentsBuilder location = ServletUriComponentsBuilder.fromRequestUri(request);
    return Mono.defer(() -> {
      validate(ingredient);
      return repo.save(ingredient).map(saved -> ResponseEntity.created(
          location.cloneBuilder().pathSegment(saved.getId()).build().encode().toUri()).body(saved));
    });
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> deleteIngredient(@PathVariable String id) {
    return Mono.defer(() -> repo.findById(id))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found")))
        .flatMap(existing -> repo.deleteById(id).thenReturn(ResponseEntity.noContent().build()));
  }

  private void validate(Ingredient ingredient) {
    if (ingredient.getName() == null || ingredient.getName().trim().isEmpty() || ingredient.getType() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and type are required");
    }
  }
}
