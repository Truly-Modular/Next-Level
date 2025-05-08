package smartin.tmnextlevel;

import smartin.tmnextlevel.upgrade.Upgrade;

public final class Tmnextlevel {
    public static final String MOD_ID = "tmnextlevel";

    public static void init() {
        Upgrade.setup();
    }
}
