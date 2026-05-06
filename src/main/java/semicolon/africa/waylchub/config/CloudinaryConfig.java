package semicolon.africa.waylchub.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudinary client bean.
 *
 * Loaded only when storage.provider is cloudinary (or unset — cloudinary is the
 * default). The Cloudinary SDK pulls credentials from properties.
 *
 * application.properties:
 *   storage.provider=cloudinary
 *   cloudinary.cloud-name=your-cloud-name
 *   cloudinary.api-key=your-api-key
 *   cloudinary.api-secret=your-api-secret
 *
 * Get free credentials at: https://cloudinary.com (free tier = 25GB storage + bandwidth)
 */
@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary", matchIfMissing = true)
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
}