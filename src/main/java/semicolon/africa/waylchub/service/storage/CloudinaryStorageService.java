package semicolon.africa.waylchub.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Cloudinary-backed implementation of {@link StorageService}.
 *
 * Active when {@code storage.provider=cloudinary} (which is the default unless
 * explicitly overridden). See {@link semicolon.africa.waylchub.config.CloudinaryConfig}
 * for the bean wiring.
 *
 * Why Cloudinary:
 *   - Generous free tier (25GB storage + bandwidth) covers thousands of orders
 *   - Automatic image optimization (quality, format) reduces bandwidth costs
 *   - URL-based transformations (we can request thumbnails on the fly)
 *   - No need to manage object storage buckets
 *
 * Folder organization (use the {@code folder} param):
 *   - "custom-orders/style-refs"  — client-uploaded inspiration images
 *   - "custom-orders/admin-styles" — admin-uploaded category gallery photos
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary", matchIfMissing = true)
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public UploadResult uploadDataUrl(String dataUrl, String folder) {
        validateDataUrl(dataUrl);
        return uploadInternal(dataUrl, folder);
    }

    @Override
    public UploadResult uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds 5MB limit");
        }
        if (!isImageContentType(file.getContentType())) {
            throw new IllegalArgumentException("Only image files are accepted");
        }

        try {
            return uploadInternal(file.getBytes(), folder);
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            // Don't throw — orphaned assets are recoverable, blocking the
            // calling flow on a delete failure isn't worth it.
            log.warn("[Cloudinary] Delete failed for publicId '{}': {}", publicId, e.getMessage());
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private UploadResult uploadInternal(Object source, String folder) {
        try {
            String publicId = "eab_" + UUID.randomUUID().toString().substring(0, 12);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(source, ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", false,
                    // Auto-format and auto-quality cut bandwidth dramatically
                    "fetch_format", "auto",
                    "quality", "auto"
            ));

            String secureUrl = (String) result.get("secure_url");
            String returnedPublicId = (String) result.get("public_id");

            if (secureUrl == null) {
                throw new RuntimeException("Cloudinary did not return a secure_url");
            }

            log.info("[Cloudinary] Uploaded to {} (publicId={})", folder, returnedPublicId);
            return new UploadResult(secureUrl, returnedPublicId);

        } catch (IOException e) {
            log.error("[Cloudinary] Upload failed", e);
            throw new RuntimeException("Image upload failed: " + e.getMessage(), e);
        }
    }

    private void validateDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IllegalArgumentException("Data URL is empty");
        }
        if (!dataUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException("Only image data URLs are accepted");
        }
        // Rough size check — base64 inflates ~33%, so 5MB image ≈ 6.7M chars
        if (dataUrl.length() > 7_000_000) {
            throw new IllegalArgumentException("Image data exceeds 5MB limit");
        }
    }

    private boolean isImageContentType(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
}