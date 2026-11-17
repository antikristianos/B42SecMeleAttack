package antikristianos.secmeleattack;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import me.zed_0xff.zombie_buddy.Exposer;
import zombie.CombatManager;
import zombie.characters.IsoLivingCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoWorld;
import zombie.input.Mouse;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameServer;
import zombie.network.fields.hit.AttackVars;
import zombie.network.fields.hit.HitInfo;

@Exposer.LuaClass(name = "B42SecMeleAttack")
public final class Main {
    private static boolean supported;
    private static boolean blockPrimaryFloor;
    private static IsoPlayer directPlayer;
    private static HandWeapon directWeapon;
    private static IsoPlayer outlinePlayer;
    private static final ThreadLocal<IsoPlayer> outlineQuery = new ThreadLocal<>();
    private static HandWeapon outlineWeapon;
    private static final List<HitInfo> proneTargets = new ArrayList<>(), standingTargets = new ArrayList<>();
    private static final List<IsoZombie> outlineTargets = new ArrayList<>();
    private static long nextOutlineQuery;

    private Main() {}

    public static void main(String[] args) {
        supported = baselineMatches();
        System.out.println("[B42SecMeleAttack] 0.1 " + (supported
            ? "active; direct weapon floor attack; client input only"
            : "inactive: unreviewed Vanilla combat classes"));
    }

    public static boolean isSupported() { return supported; }
    public static void setBlockPrimaryFloor(boolean value) { blockPrimaryFloor = value; }

    public static boolean mouseBindingDown(int key) {
        int button = key - Mouse.BTN_OFFSET;
        return button >= 2 && button < 8 && Mouse.buttonDownStates != null
            && button < Mouse.buttonDownStates.length && Mouse.isButtonDown(button);
    }

    public static boolean mouseBindingAvailable(int key) {
        int button = key - Mouse.BTN_OFFSET;
        return button >= 2 && button < 8 && Mouse.uiCaptured != null
            && button < Mouse.uiCaptured.length && !Mouse.uiCaptured[button];
    }

    public static boolean localKeyboardPlayer(IsoPlayer player) {
        return !GameServer.server && player != null && player.isLocalPlayer()
            && !player.isNpc() && player.getPlayerNum() == 0 && player.getJoypadBind() == -1;
    }

    public static HandWeapon meleeWeapon(IsoPlayer player) {
        InventoryItem primary = player.getPrimaryHandItem();
        if (!(primary instanceof HandWeapon weapon) || weapon.isBareHands() || weapon.isRanged()
                || weapon.getCondition() <= 0 || weapon.isBroken() || weapon.getSwingAnim() == null
                || "throwing".equalsIgnoreCase(weapon.getSwingAnim())) return null;
        if (weapon.getOtherHandRequire() != null) {
            InventoryItem secondary = player.getSecondaryHandItem();
            if (secondary == null || !secondary.hasTag(weapon.getOtherHandRequire())
                    || secondary.getCurrentUses() == 0) return null;
        }
        return weapon;
    }

    /** One key-down edge, no queued or repeated attack and no direct damage calls. */
    public static boolean attack(IsoPlayer player) {
        if (!supported || !blockPrimaryFloor || !localKeyboardPlayer(player) || !player.isAiming() || player.isDead() || player.isAsleep()
                || player.getVehicle() != null || player.isKnockedDown() || player.isClimbing()
                || player.isPerformingAnAction() || player.isPerformingHostileAnimation()
                || player.isAttackStarted() || player.isAttacking() || player.isBlockMovement()
                || player.isBannedAttacking() || !player.isAuthorizedHandToHandAction()
                || !player.CanAttack() || !player.isWeaponReady() || !player.canPerformHandToHandCombat()
                || player.isMeleeButtonDown() || player.isGrapplePressed()) return false;
        HandWeapon weapon = meleeWeapon(player);
        if (weapon == null || player.getAttackVars().isProcessed) return false;
        directPlayer = player;
        directWeapon = weapon;
        HandWeapon oldUseWeapon = player.getUseHandWeapon();
        boolean oldShove = player.isDoShove(), oldGrapple = player.isDoGrapple();
        player.setDoShove(false);
        player.setDoGrapple(false);
        player.setUseHandWeapon(weapon);
        try {
            // Keep Vanilla's state, hit-list, animation, stamina and network path.
            player.pressedAttack();
            return player.isAttackStarted();
        } finally {
            if (!player.isAttackStarted()) {
                endAttack(player);
                player.setDoShove(oldShove);
                player.setDoGrapple(oldGrapple);
                player.setUseHandWeapon(oldUseWeapon);
            }
        }
    }

    /** Called only after Vanilla's private two-argument calculation. */
    public static void floorVars(IsoLivingCharacter owner, AttackVars vars) {
        if (!supported || owner != directPlayer || vars.isProcessed
                || directWeapon == null || owner.getPrimaryHandItem() != directWeapon) return;
        owner.setUseHandWeapon(directWeapon);
        vars.setWeapon(directWeapon);
        vars.aimAtFloor = true;
        vars.doShove = false;
        vars.doGrapple = false;
        vars.closeKill = false;
        vars.targetStanding.set(null);
        HitInfo prone = vars.targetsProne.isEmpty() ? null : vars.targetsProne.get(0);
        vars.targetOnGround.set(prone == null ? null : prone.getObject());
        vars.targetDistance = prone == null ? 0f : (float) Math.sqrt(prone.distSq);
        owner.setDoShove(false);
        owner.setDoGrapple(false);
    }

    public static boolean blockAttack(CombatManager manager, IsoPlayer player) {
        if (!supported || !blockPrimaryFloor || !localKeyboardPlayer(player)
                || player == directPlayer || !Mouse.isLeftDown() || meleeWeapon(player) == null
                || player.isDoShove() || player.isDoGrapple() || player.isMeleeButtonDown()
                || player.isGrapplePressed() || player.isAttackStarted()) return false;
        manager.calculateAttackVars(player);
        AttackVars vars = player.getAttackVars();
        return isWeaponFloor(vars);
    }

    public static boolean isWeaponFloor(AttackVars vars) {
        return vars.aimAtFloor && !vars.doShove && !vars.doGrapple;
    }

    public static void endAttack(IsoLivingCharacter owner) {
        if (owner == directPlayer) { directPlayer = null; directWeapon = null; }
    }

    public static void update(IsoPlayer player) {
        if (directPlayer != null && (player != directPlayer || player.isDead()
                || (!player.isAttackStarted() && !player.isPerformingHostileAnimation()
                    && !player.isAttacking()))) reset();
        if (outlinePlayer != null && (player != outlinePlayer || player.isDead() || !player.isAiming()
                || player.getPrimaryHandItem() != outlineWeapon)) clearOutlines();
    }

    public static void reset() { directPlayer = null; directWeapon = null; clearOutlines(); }

    private static void clearOutlines() {
        outlinePlayer = null; outlineWeapon = null; outlineTargets.clear(); nextOutlineQuery = 0L;
    }

    /** Only this read-only candidate query may bypass Vanilla's standing-threat prone filter. */
    public static boolean isOutlineQuery(IsoGameCharacter player) { return player != null && player == outlineQuery.get(); }

    public static void outlineBoth(CombatManager manager, IsoPlayer player) {
        if (!supported || !localKeyboardPlayer(player) || !player.isAiming() || player.isDead()
                || !Core.getInstance().getOptionMeleeOutline() || IsoWorld.instance.currentCell == null) {
            clearOutlines(); return;
        }
        HandWeapon weapon = meleeWeapon(player);
        if (weapon == null) { clearOutlines(); return; }
        long now = System.nanoTime();
        if (player != outlinePlayer || weapon != outlineWeapon || now - nextOutlineQuery >= 0L) {
            outlineTargets.clear(); outlinePlayer = player; outlineWeapon = weapon;
            outlineQuery.set(player);
            try {
                manager.calcValidTargets(player, weapon, proneTargets, standingTargets);
                collectOutlineTargets(standingTargets);
                collectOutlineTargets(proneTargets);
            } finally {
                outlineQuery.remove();
                manager.hitInfoPool.release(proneTargets); proneTargets.clear();
                manager.hitInfoPool.release(standingTargets); standingTargets.clear();
            }
            nextOutlineQuery = now + 100_000_000L;
        }
        ColorInfo color = Core.getInstance().getBadHighlitedColor();
        for (IsoZombie zombie : outlineTargets) {
            if (zombie.isDead() || zombie.getCurrentSquare() == null) continue;
            zombie.setOutlineHighlight(player.getIndex(), true);
            zombie.setOutlineHighlightCol(color.r, color.g, color.b, 1f);
        }
    }

    private static void collectOutlineTargets(List<HitInfo> hits) {
        for (HitInfo hit : hits) if (hit.getObject() instanceof IsoZombie zombie) outlineTargets.add(zombie);
    }

    public static boolean baselineMatches() {
        try (InputStream manifest = Main.class.getResourceAsStream("vanilla.sha256")) {
            if (manifest == null) return false;
            Properties pins = new Properties();
            pins.load(manifest);
            if (pins.size() != 3) return false;
            for (String name : pins.stringPropertyNames()) {
                try (InputStream bytes = Main.class.getResourceAsStream("/" + name)) {
                    if (bytes == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(bytes.readAllBytes())).equals(pins.getProperty(name))) return false;
                }
            }
            return true;
        } catch (Exception exception) { return false; }
    }
}
