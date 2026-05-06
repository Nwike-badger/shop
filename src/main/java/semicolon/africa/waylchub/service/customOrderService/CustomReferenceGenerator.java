package semicolon.africa.waylchub.service.customOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomOrderRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-readable order references in format:
 *   EAB-CD-YYYYMMDD-XXXX
 *
 * Where:
 *   EAB    = ExploreAba
 *   CD     = Custom Design
 *   YYYYMMDD = submission date (Africa/Lagos)
 *   XXXX   = 4-character base32 random (no easily-confused chars)
 *
 * The XXXX uses a 32-char alphabet excluding O/0/I/1 to avoid spoken/written
 * ambiguity over WhatsApp ("Was that an oh or a zero?"). With 4 chars from a
 * 32-char alphabet that's ~1M permutations per day — collision is rare but
 * not impossible, so we still re-roll if {@code existsByReferenceNumber} is true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomReferenceGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no O/0/I/1
    private static final int RANDOM_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 10;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CustomOrderRepository repository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = buildReference();
            if (!repository.existsByReferenceNumber(candidate)) {
                return candidate;
            }
            log.warn("[Reference] Collision on '{}' — retrying ({}/{}).",
                    candidate, attempt + 1, MAX_ATTEMPTS);
        }
        // Astronomically unlikely, but if we hit this, the unique index on
        // referenceNumber will reject the save anyway — we surface a clear error.
        throw new IllegalStateException(
                "Unable to generate unique reference after " + MAX_ATTEMPTS + " attempts");
    }

    private String buildReference() {
        String date = LocalDate.now().format(DATE_FMT);
        StringBuilder rand = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            rand.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "EAB-CD-" + date + "-" + rand;
    }
}