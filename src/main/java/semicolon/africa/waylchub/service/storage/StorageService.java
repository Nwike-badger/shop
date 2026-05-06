package semicolon.africa.waylchub.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Image storage abstraction.
 *
 * Two implementations are wired with @ConditionalOnProperty:
 *   - {@link CloudinaryStorageService} — production. Uploads to Cloudinary.
 *   - {@link InlineStorageService}     — dev fallback. Returns the data URL
 *                                        unchanged so MongoDB stores it inline.
 *
 * Switch via storage.provider in application.properties:
 *   storage.provider=cloudinary  (default in prod)
 *   storage.provider=inline      (dev only — DO NOT use in prod, will blow Atlas storage)
 *
 * The SAME StorageService can power BOTH client-uploaded style references
 * AND admin-uploaded category style photos. Use the {@code folder} parameter
 * to keep them organized.
 */
public interface StorageService {

    /**
     * Upload a base64 data URL (e.g., "data:image/png;base64,iVBORw0...").
     *
     * @param dataUrl     the data URL string
     * @param folder      logical folder for organization (e.g., "custom-orders/styles")
     * @return            an {@link UploadResult} containing the public URL and provider id
     */
    UploadResult uploadDataUrl(String dataUrl, String folder);

    /**
     * Upload a MultipartFile (used by the upload endpoint).
     *
     * @param file        the uploaded file
     * @param folder      logical folder for organization
     * @return            an {@link UploadResult}
     */
    UploadResult uploadFile(MultipartFile file, String folder);

    /**
     * Delete an uploaded asset by its provider-specific public id.
     * No-op for the inline provider.
     */
    void delete(String publicId);

    /** Result of a successful upload. */
    record UploadResult(String url, String publicId) {}
}