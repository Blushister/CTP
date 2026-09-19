package fr.arcadia.ctp.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Refuses to apply a mixin unless its target still looks the way the injection expects.
 *
 * <p>Targets are inspected with ASM instead of being loaded, so a missing Create, a
 * renamed field or a changed signature disables that patch before Mixin validates any
 * {@code @Shadow} - which would otherwise be a startup crash rather than a log line.
 * A disabled patch means the protection is absent, which is the pre-existing behaviour.
 */
public final class CTPMixinPlugin implements IMixinConfigPlugin {

    // Not ArcadiaCTP.LOGGER: the mod class is loaded far later than this plugin runs.
    private static final Logger LOGGER = LogManager.getLogger("arcadia_ctp");
    private static final Set<String> LOGGED_SKIPS = new HashSet<>();

    private record Member(String name, String descriptor) {
    }

    private record Target(String className, List<Member> fields, List<Member> methods) {
    }

    private static final String BLOCK_POS = "Lnet/minecraft/core/BlockPos;";
    private static final String COMPOUND_TAG = "Lnet/minecraft/nbt/CompoundTag;";
    private static final String MOVEMENT_CONTEXT =
        "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;";

    private static final String UUID_DESC = "Ljava/util/UUID;";
    private static final String WAYSTONE = "net.blay09.mods.waystones.api.Waystone";
    private static final String WAYSTONE_VISIBILITY = "net.blay09.mods.waystones.api.WaystoneVisibility";
    private static final String WAYSTONE_MANAGER = "net.blay09.mods.waystones.core.WaystoneManagerImpl";
    private static final String FTB_TEAMS_API = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private static final String FTB_TEAMS_API_INSTANCE = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API";
    private static final String FTB_TEAM_MANAGER = "dev.ftb.mods.ftbteams.api.TeamManager";
    private static final String FTB_TEAM = "dev.ftb.mods.ftbteams.api.Team";
    private static final String FTB_TEAM_RANK = "dev.ftb.mods.ftbteams.api.TeamRank";

    /**
     * Mixin simple name to what must still be there for it to work.
     *
     * <p>More than one class per mixin: an injection can be perfectly valid and still
     * break on the first call, because the guard behind it reads an API that moved. The
     * whole path is checked, not just the injection point.
     */
    private static final Map<String, List<Target>> REQUIREMENTS = Map.of(
        "MixinBlockEntityConfigurationPacket", List.of(new Target(
            "com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket",
            List.of(new Member("pos", BLOCK_POS)),
            List.of(new Member("handle", "(Lnet/minecraft/server/level/ServerPlayer;)V"))
        )),
        "MixinAbstractContraptionEntity", List.of(new Target(
            "com.simibubi.create.content.contraptions.AbstractContraptionEntity",
            List.of(),
            List.of(
                new Member("tickActors", "()V"),
                new Member("refreshPSIs", "()V"),
                new Member("getControllingPlayer", "()Ljava/util/Optional;"),
                new Member("addAdditionalSaveData", "(" + COMPOUND_TAG + ")V"),
                new Member("readAdditionalSaveData", "(" + COMPOUND_TAG + ")V")
            )
        )),
        "MixinContraption", List.of(new Target(
            "com.simibubi.create.content.contraptions.Contraption",
            // The anchor is read from the contraption by the guards, not shadowed, but a
            // rename would still break them - so it is checked here alongside the method.
            List.of(new Member("anchor", BLOCK_POS)),
            List.of(new Member("searchMovedStructure",
                "(Lnet/minecraft/world/level/Level;" + BLOCK_POS + "Lnet/minecraft/core/Direction;)Z"))
        )),
        "MixinPortableStorageInterfaceMovement", List.of(new Target(
            "com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement",
            List.of(),
            List.of(new Member("findInterface", "(" + MOVEMENT_CONTEXT + BLOCK_POS + ")Z"))
        )),
        // Waystones is an optional dependency: absent, the first target is unreadable and
        // the mixin is skipped, which leaves Waystones' own scoreboard behaviour in place.
        "MixinWaystoneIndexManager", List.of(
            new Target(
                "net.blay09.mods.waystones.core.WaystoneIndexManager",
                List.of(),
                List.of(new Member("getTeamTargets",
                    "(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/Collection;"))
            ),
            new Target(
                WAYSTONE_MANAGER,
                List.of(),
                List.of(
                    new Member("get", "(Lnet/minecraft/server/MinecraftServer;)L"
                        + WAYSTONE_MANAGER.replace('.', '/') + ";"),
                    new Member("getWaystones", "()Ljava/util/stream/Stream;")
                )
            ),
            new Target(
                WAYSTONE,
                List.of(),
                List.of(
                    new Member("getOwnerUid", "()" + UUID_DESC),
                    new Member("getVisibility", "()L" + WAYSTONE_VISIBILITY.replace('.', '/') + ";")
                )
            ),
            new Target(
                WAYSTONE_VISIBILITY,
                List.of(new Member("TEAM", "L" + WAYSTONE_VISIBILITY.replace('.', '/') + ";")),
                List.of()
            ),
            new Target(
                FTB_TEAMS_API,
                List.of(),
                List.of(new Member("api", "()L" + FTB_TEAMS_API_INSTANCE.replace('.', '/') + ";"))
            ),
            new Target(
                FTB_TEAMS_API_INSTANCE,
                List.of(),
                List.of(
                    new Member("isManagerLoaded", "()Z"),
                    new Member("getManager", "()L" + FTB_TEAM_MANAGER.replace('.', '/') + ";")
                )
            ),
            new Target(
                FTB_TEAM_MANAGER,
                List.of(),
                List.of(
                    new Member("arePlayersInSameTeam", "(" + UUID_DESC + UUID_DESC + ")Z"),
                    new Member("getTeamForPlayerID", "(" + UUID_DESC + ")Ljava/util/Optional;")
                )
            ),
            new Target(
                FTB_TEAM,
                List.of(),
                List.of(new Member("getRankForPlayer",
                    "(" + UUID_DESC + ")L" + FTB_TEAM_RANK.replace('.', '/') + ";"))
            ),
            new Target(
                FTB_TEAM_RANK,
                List.of(),
                List.of(new Member("isAllyOrBetter", "()Z"))
            )
        )
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        List<Target> targets = REQUIREMENTS.get(simpleName);
        if (targets == null) {
            return true;
        }
        for (Target target : targets) {
            if (!isUsable(target)) {
                // Said out loud once: a silently absent patch is a protection that looks
                // configured in the panel and does nothing in game.
                if (LOGGED_SKIPS.add(simpleName)) {
                    LOGGER.warn("[ArcadiaCTP] {} disabled: {} is missing or no longer has the "
                        + "members it is written against.", simpleName, target.className());
                }
                return false;
            }
        }
        return true;
    }

    private static boolean isUsable(Target target) {
        ClassNode node = read(target.className());
        if (node == null) {
            return false;
        }
        for (Member field : target.fields()) {
            if (!hasField(node, field)) {
                return false;
            }
        }
        for (Member method : target.methods()) {
            if (!hasMethod(node, method)) {
                return false;
            }
        }
        return true;
    }

    private static ClassNode read(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream input = CTPMixinPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return node;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean hasField(ClassNode node, Member member) {
        if (node.fields == null) {
            return false;
        }
        for (FieldNode field : node.fields) {
            if (field.name.equals(member.name()) && field.desc.equals(member.descriptor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode node, Member member) {
        if (node.methods == null) {
            return false;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(member.name()) && method.desc.equals(member.descriptor())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
