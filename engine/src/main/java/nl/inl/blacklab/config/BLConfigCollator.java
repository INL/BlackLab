package nl.inl.blacklab.config;

import java.text.Collator;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidConfiguration;

public class BLConfigCollator {
    String language = "en";
    
    String country = null;
    
    String variant = null;

    @SuppressWarnings("unused")
    public void setLanguage(String language) {
        this.language = language;
    }

    @SuppressWarnings("unused")
    public void setCountry(String country) {
        this.country = country;
    }

    @SuppressWarnings("unused")
    public void setVariant(String variant) {
        this.variant = variant;
    }

    public Collator get() {
        if (language == null || country == null && variant != null)
            throw new InvalidConfiguration(
                    "Collator must have language, language+country or language+country+variant");
        if (StringUtils.isEmpty(variant)) {
            if (StringUtils.isEmpty(country)) {
                return Collator.getInstance(new Locale(language));
            } else {
                return Collator.getInstance(new Locale(language, country));
            }
        } else {
            return Collator.getInstance(new Locale(language, country, variant));
        }
    }
}