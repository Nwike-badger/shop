package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import semicolon.africa.waylchub.dto.customOrderDto.ImageUploadResponse;
import semicolon.africa.waylchub.service.storage.StorageService;

/**
 * Image upload endpoint for custom-order style references.
 *
 * URL prefix is /api/v1/ to match the rest of the codebase. Frontend axios
 * baseURL ends in /api so client code calls /v1/custom-uploads relative.
 *
 * The frontend can call this either:
 *   1. As the user adds each image (recommended — better UX, faster submit)
 *   2. Or skip this and embed dataUrl values directly in the submission
 *      (the order service uploads them at submit time)
 *
 * Both flows produce the same end result: a permanent URL stored in the order's
 * style.referenceImageUrls list.
 *
 * Open to guests — anyone submitting a custom order needs this. Rate limiting
 * should be applied at the gateway/proxy level (e.g., 10 uploads/minute per IP)
 * to prevent abuse since this hits Cloudinary's bandwidth quota.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/custom-uploads")
@RequiredArgsConstructor
public class CustomUploadController {

    private final StorageService storageService;

    @PostMapping(value = "/style-reference", consumes = "multipart/form-data")
    public ResponseEntity<ImageUploadResponse> uploadStyleReference(
            @RequestParam("file") MultipartFile file) {

        StorageService.UploadResult result =
                storageService.uploadFile(file, "custom-orders/style-refs");

        return ResponseEntity.ok(ImageUploadResponse.builder()
                .url(result.url())
                .publicId(result.publicId())
                .build());
    }
}