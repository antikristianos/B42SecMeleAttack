package antikristianos.secmeleattack;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.CombatManager;
import zombie.characters.IsoPlayer;

@Patch(className = "zombie.CombatManager", methodName = "highlightTargets", strictMatch = true, warmUp = true)
public final class PatchOutline {
    @Patch.OnExit
    public static void exit(@Patch.This CombatManager manager, @Patch.Argument(0) IsoPlayer player) {
        Main.outlineBoth(manager, player);
    }
}
