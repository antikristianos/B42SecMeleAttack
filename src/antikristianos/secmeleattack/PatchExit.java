package antikristianos.secmeleattack;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoLivingCharacter;

@Patch(className = "zombie.ai.states.SwipeStatePlayer", methodName = "exit", strictMatch = true, warmUp = true)
public final class PatchExit {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) IsoGameCharacter owner) {
        if (owner instanceof IsoLivingCharacter living) Main.endAttack(living);
    }
}
