package smartin.tmnextlevel;

import com.redpxnda.nucleus.codec.auto.AutoCodec;
import com.redpxnda.nucleus.codec.auto.ConfigAutoCodec;

@ConfigAutoCodec.ConfigClassMarker
public class NextLevelConfig {

    @AutoCodec.Name("level_to_xp")
    public String levelToXp = "(2^x)*10+100";

}
