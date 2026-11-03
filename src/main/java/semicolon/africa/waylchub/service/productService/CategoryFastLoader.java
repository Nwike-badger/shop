package semicolon.africa.waylchub.service.productService;

import com.mongodb.DBRef;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.model.product.Category;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads categories in ONE query, without Spring resolving each @DBRef parent
 * with its own extra query (which costs ~1 round trip per category).
 *
 * Returns ordinary Category objects with id, name, slug, description, imageUrl,
 * lineage, featured, displayOrder and active filled in. getParent() is set to
 * the parent object from the same result (or, if the parent wasn't loaded, a
 * stub holding only its id), so getParent().getId() always works.
 *
 * READ-ONLY: never pass these objects to categoryRepository.save(). Use
 * targeted mongoTemplate updates instead.
 */
@Component
@RequiredArgsConstructor
public class CategoryFastLoader {

    private final MongoTemplate mongoTemplate;

    /** Every category, parents linked in memory. One query. */
    public List<Category> findAll() {
        return load(new Query());
    }

    /** Categories with these slugs. One query. */
    public List<Category> findBySlugs(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) return new ArrayList<>();
        return load(new Query(Criteria.where("slug").in(slugs)));
    }

    /** Every category keyed by id. One query. */
    public Map<String, Category> byId() {
        return findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<Category> load(Query query) {
        String collection = mongoTemplate.getCollectionName(Category.class);
        List<Document> docs = mongoTemplate.find(query, Document.class, collection);

        Map<String, Category> byId = new LinkedHashMap<>();
        Map<String, String> parentIdOf = new HashMap<>();

        for (Document d : docs) {
            Category c = toCategory(d);
            byId.put(c.getId(), c);
            String pid = refId(d.get("parent"));
            if (pid != null) parentIdOf.put(c.getId(), pid);
        }

        parentIdOf.forEach((childId, pid) -> {
            Category parent = byId.get(pid);
            if (parent == null) {            // parent not in this result → id-only stub
                parent = new Category();
                parent.setId(pid);
            }
            byId.get(childId).setParent(parent);
        });

        return new ArrayList<>(byId.values());
    }

    private static Category toCategory(Document d) {
        Category c = new Category();
        c.setId(String.valueOf(d.get("_id")));
        c.setName(d.getString("name"));
        c.setSlug(d.getString("slug"));
        c.setDescription(d.getString("description"));
        c.setImageUrl(d.getString("imageUrl"));
        c.setLineage(d.getString("lineage"));
        c.setFeatured(bool(d, true == false, "isFeatured", "featured"));
        c.setActive(bool(d, true, "isActive", "active"));
        Object order = d.get("displayOrder");
        c.setDisplayOrder(order instanceof Number n ? n.intValue() : null);
        return c;
    }

    /** First present key wins; otherwise the default. */
    private static boolean bool(Document d, boolean dflt, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            if (v instanceof Boolean b) return b;
        }
        return dflt;
    }

    /** Extracts the id from a stored DBRef (either form). */
    private static String refId(Object value) {
        if (value instanceof DBRef ref) return String.valueOf(ref.getId());
        if (value instanceof Document doc && doc.get("$id") != null) return String.valueOf(doc.get("$id"));
        return null;
    }
}