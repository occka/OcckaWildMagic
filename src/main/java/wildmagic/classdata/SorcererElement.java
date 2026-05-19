package wildmagic.classdata;

import java.util.Locale;

public enum SorcererElement {
    FIRE, ICE, LIGHTNING, POISON, THUNDER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SorcererElement fromId(String id) {
        if (id == null || id.isBlank()) return FIRE;
        for (SorcererElement e : values()) {
            if (e.id().equals(id.toLowerCase(Locale.ROOT))) return e;
        }
        return FIRE;
    }
}