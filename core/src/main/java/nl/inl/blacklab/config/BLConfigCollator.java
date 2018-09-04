package nl.inl.blacklab.config;

import java.text.Collator;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidConfiguration;

public class BLConfigCollator {
    String language = "en";
    
    String country = null;
    
    String variant = null;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getVariant() {
        return variant;
    }

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