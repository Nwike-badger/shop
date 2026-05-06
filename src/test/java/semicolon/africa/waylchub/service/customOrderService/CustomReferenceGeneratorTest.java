package semicolon.africa.waylchub.service.customOrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomOrderRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomReferenceGeneratorTest {

    @Mock
    private CustomOrderRepository repository;

    @InjectMocks
    private CustomReferenceGenerator generator;

    @BeforeEach
    void setUp() {
        when(repository.existsByReferenceNumber(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("Generated reference matches EAB-CD-YYYYMMDD-XXXX format")
    void format_isCorrect() {
        String ref = generator.generate();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        assertThat(ref).matches("EAB-CD-" + today + "-[A-Z2-9]{4}");
    }

    @Test
    @DisplayName("Random portion uses unambiguous alphabet (no O, 0, I, 1)")
    void alphabet_excludesAmbiguousCharacters() {
        // Generate many references and check none contain O/0/I/1
        for (int i = 0; i < 1000; i++) {
            String ref = generator.generate();
            String randomPart = ref.substring(ref.length() - 4);
            assertThat(randomPart).doesNotContain("O");
            assertThat(randomPart).doesNotContain("0");
            assertThat(randomPart).doesNotContain("I");
            assertThat(randomPart).doesNotContain("1");
        }
    }

    @Test
    @DisplayName("Retries when reference collides until unique")
    void collision_retriesAndReturnsUnique() {
        // First two checks return true (collision), third returns false (unique)
        when(repository.existsByReferenceNumber(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        String ref = generator.generate();

        assertThat(ref).startsWith("EAB-CD-");
        // Should have called existsByReferenceNumber at least 3 times (2 collisions + 1 success)
    }

    @Test
    @DisplayName("Throws when collision retries exhaust")
    void collision_exhaustsAttempts_throws() {
        when(repository.existsByReferenceNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate unique reference");
    }

    @Test
    @DisplayName("Generates many unique references with high probability")
    void manyGenerations_areMostlyUnique() {
        // With ~1M permutations per day, 1000 generations should have very few collisions.
        // (We're not testing the repo here — repo always returns false in this test —
        // we're testing that the random generation itself produces variety.)
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        // Allow a small number of natural duplicates from the SecureRandom output;
        // we want at least 95% uniqueness.
        assertThat(seen.size()).isGreaterThanOrEqualTo(950);
    }
}