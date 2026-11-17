package antikristianos.secmeleattack;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;

@Patch(className = "zombie.CombatManager", methodName = "shouldIgnoreProneZombies", strictMatch = true, warmUp = true)
public final class PatchOutlineProne {
    // Skipping this boolean method returns false; only our separate outline query is affected.
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.Argument(0) IsoGameCharacter player) {
        return Main.isOutlineQuery(player);
    }
}
