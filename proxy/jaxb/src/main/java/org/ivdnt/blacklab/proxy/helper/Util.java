package org.ivdnt.blacklab.proxy.helper;

import java.text.Collator;
import java.util.Locale;

public class Util {

    public static final Collator DEFAULT_COLLATOR;

    public static final Collator DEFAULT_COLLATOR_INSENSITIVE;

    static {
        DEFAULT_COLLATOR = Collator.getInstance(new Locale("nl", "NL"));
        DEFAULT_COLLATOR.setStrength(Collator.TERTIARY);

        DEFAULT_COLLATOR_INSENSITIVE = Collator.getInstance(new Locale("nl", "NL"));
        DEFAULT_COLLATOR_INSENSITIVE.setStrength(Collator.PRIMARY);
    }
}
