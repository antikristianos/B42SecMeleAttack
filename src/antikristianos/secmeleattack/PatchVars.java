package antikristianos.secmeleattack;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoLivingCharacter;
import zombie.network.fields.hit.AttackVars;

@Patch(className = "zombie.CombatManager", methodName = "calculateAttackVars", strictMatch = true, warmUp = true)
public final class PatchVars {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) IsoLivingCharacter owner, @Patch.Argument(1) AttackVars vars) {
        Main.floorVars(owner, vars);
    }
}
