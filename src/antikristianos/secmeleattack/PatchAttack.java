package antikristianos.secmeleattack;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.CombatManager;
import zombie.characters.IsoPlayer;

@Patch(className = "zombie.CombatManager", methodName = "pressedAttack", strictMatch = true, warmUp = true)
public final class PatchAttack {
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This CombatManager manager, @Patch.Argument(0) IsoPlayer player) {
        return Main.blockAttack(manager, player);
    }
}
