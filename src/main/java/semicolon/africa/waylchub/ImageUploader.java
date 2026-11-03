package semicolon.africa.waylchub;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Uploads every image in a local folder to Cloudinary and prints the links.
 *
 *   ~/exploreaba/uploads/hoodies/front.jpg  ->  Cloudinary: exploreaba/media/hoodies/front
 *
 * Links are printed and also saved to ~/exploreaba/uploads/links.txt.
 * Uploaded files are moved to ~/exploreaba/uploads/_done so they aren't uploaded twice.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exploreaba.uploader.enabled", havingValue = "true")
public class ImageUploader implements CommandLineRunner {

    private final StorageService storage;

    @Value("${exploreaba.uploader.path}") private String uploadPath;
    @Value("${exploreaba.uploader.root-folder:exploreaba/media}") private String rootFolder;

    private static final Set<String> EXT = Set.of("jpg", "jpeg", "png", "webp", "gif", "avif");

    @Override
    public void run(String... args) throws Exception {
        System.out.println("📤 EXPLOREABA IMAGE UPLOADER");
        Path root = Paths.get(uploadPath);
        if (!Files.isDirectory(root)) {
            System.out.println("⚠️  Folder not found: " + root);
            return;
        }

        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(root, p))
                    .filter(p -> EXT.contains(ext(p)))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            System.out.println("ℹ️  No new images in " + root);
            return;
        }

        List<String> lines = new ArrayList<>();
        int ok = 0, failed = 0;

        for (Path file : files) {
            Path rel = root.relativize(file);
            String folder = cloudFolder(rel);
            String name = clean(baseName(file));
            try {
                var result = storage.uploadLocalFile(file, folder, name);
                String line = folder + "/" + name + "  ->  " + result.url();
                System.out.println("   ☁️  " + line);
                lines.add(line);

                Path dest = root.resolve("_done").resolve(rel);
                Files.createDirectories(dest.getParent());
                Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
                ok++;
            } catch (Exception ex) {
                System.out.println("   ❌ " + rel + ": " + ex.getMessage());
                failed++;
            }
        }

        if (!lines.isEmpty()) {
            Files.write(root.resolve("links.txt"), lines,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.out.println("✅ UPLOAD COMPLETE — " + ok + " uploaded, " + failed + " failed. "
                + "Links are in " + root.resolve("links.txt")
                + ". Set exploreaba.uploader.enabled=false now.");
    }

    /** exploreaba/media + the sub-folder the file sits in. */
    private String cloudFolder(Path rel) {
        StringBuilder sb = new StringBuilder(rootFolder);
        Path parent = rel.getParent();
        if (parent != null) {
            for (Path part : parent) sb.append('/').append(clean(part.toString()));
        }
        return sb.toString();
    }

    /** Ignore anything inside a folder or file starting with "_" (like _done). */
    private boolean isSkipped(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            if (part.toString().startsWith("_")) return true;
        }
        return false;
    }

    private String clean(String s) {
        String c = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return c.isEmpty() ? "image" : c;
    }

    private String ext(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1);
    }

    private String baseName(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }
}