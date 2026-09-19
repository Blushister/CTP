package fr.arcadia.ctp.menu;

import fr.arcadia.ctp.config.CTPConfig;
import fr.arcadia.ctp.config.ContraptionSettings;
import fr.arcadia.ctp.config.WaystoneSettings;
import fr.arcadia.ctp.rules.Policy;
import fr.arcadia.ctp.rules.Rule;
import fr.arcadia.ctp.runtime.ModPresence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Admin panel rendered as a vanilla double chest.
 *
 * <p>A vanilla menu type keeps this entirely server-side: the client draws it with its own
 * chest screen, so there is no custom screen, packet or asset, and the panel works for a
 * player who only has the modpack. Text that cannot be expressed as a click - rule names,
 * block ids, packet patterns - is collected through {@link ChatPrompts}.
 *
 * <p>Every change is written to the config file immediately, so what an admin sees in game
 * and what survives a restart are the same thing.
 */
public class CTPAdminMenu extends AbstractContainerMenu {

    public enum Page {
        ROOT,
        CONTRAPTIONS,
        WAYSTONES,
        RULES,
        RULE_EDIT
    }

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    private static final List<Policy> CYCLE =
        List.of(Policy.ALLOW, Policy.CHECK, Policy.ALLY_ONLY, Policy.TEAM_ONLY, Policy.DENY);

    private static final int BACK_SLOT = 0;

    // ROOT
    private static final int ROOT_INFO = 4;
    private static final int ROOT_ENABLED = 10;
    private static final int ROOT_DEFAULT_POLICY = 12;
    private static final int ROOT_NOTIFY = 14;
    private static final int ROOT_BYPASS = 16;
    private static final int ROOT_LOG = 22;
    private static final int ROOT_CONTRAPTIONS = 29;
    private static final int ROOT_WAYSTONES = 31;
    private static final int ROOT_RULES = 33;
    private static final int ROOT_RELOAD = 49;

    // CONTRAPTIONS
    private static final int CT_ENABLED = 10;
    private static final int CT_BREAKING = 12;
    private static final int CT_DEPLOYER = 14;
    private static final int CT_STORAGE = 16;
    private static final int CT_ASSEMBLY = 21;
    private static final int CT_PILOT = 23;

    // WAYSTONES
    private static final int WS_ENABLED = 11;
    private static final int WS_VISIBILITY = 14;
    private static final int WS_INTERACTION = 16;

    // RULES
    private static final int RULES_FIRST = 9;
    private static final int RULES_PER_PAGE = 36;
    private static final int RULES_PREV = 45;
    private static final int RULES_ADD = 49;
    private static final int RULES_NEXT = 53;

    // RULE_EDIT
    private static final int EDIT_NAME = 20;
    private static final int EDIT_PACKETS = 22;
    private static final int EDIT_BLOCKS = 24;
    private static final int EDIT_POLICY = 31;
    private static final int EDIT_DELETE = 49;

    private final SimpleContainer container = new SimpleContainer(SIZE);
    private Page page;
    private int ruleIndex;
    private int rulePage;

    public CTPAdminMenu(int containerId, Inventory playerInventory, Page page, int ruleIndex) {
        super(MenuType.GENERIC_9x6, containerId);
        this.page = page;
        this.ruleIndex = ruleIndex;
        this.rulePage = ruleIndex < 0 ? 0 : ruleIndex / RULES_PER_PAGE;

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        int inventoryY = 18 + ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(playerInventory, column + row * 9 + 9,
                    8 + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new LockedSlot(playerInventory, column, 8 + column * 18, inventoryY + 58));
        }

        refresh();
    }

    public static void open(ServerPlayer player, Page page, int ruleIndex) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new CTPAdminMenu(containerId, inventory, page, ruleIndex),
            Component.literal("Arcadia CTP").withStyle(ChatFormatting.DARK_RED)));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer) || player.hasPermissions(2);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) {
            return;
        }
        if (slotId < 0 || slotId >= SIZE) {
            // Clicks in the player's own inventory rows carry no meaning here, and letting
            // them through would let an admin drop items into a container that is not real.
            return;
        }

        boolean rightClick = button == 1;
        boolean shift = clickType == ClickType.QUICK_MOVE;

        switch (page) {
            case ROOT -> clickRoot(serverPlayer, slotId, rightClick);
            case CONTRAPTIONS -> clickContraptions(serverPlayer, slotId, rightClick);
            case WAYSTONES -> clickWaystones(serverPlayer, slotId, rightClick);
            case RULES -> clickRules(serverPlayer, slotId, rightClick, shift);
            case RULE_EDIT -> clickRuleEdit(serverPlayer, slotId, rightClick, shift);
        }
    }

    // --- ROOT -------------------------------------------------------------------------

    private void clickRoot(ServerPlayer player, int slot, boolean rightClick) {
        CTPConfig.Snapshot config = CTPConfig.get();
        switch (slot) {
            case ROOT_ENABLED -> save(player, config.withEnabled(!config.enabled()));
            case ROOT_DEFAULT_POLICY ->
                save(player, config.withDefaultPolicy(cycle(config.engine().defaultPolicy(), rightClick)));
            case ROOT_NOTIFY -> save(player, config.withNotifyOnDeny(!config.notifyOnDeny()));
            case ROOT_BYPASS -> save(player, config.withRespectAdminBypass(!config.respectAdminBypass()));
            case ROOT_LOG -> save(player, config.withLogDenials(!config.logDenials()));
            case ROOT_CONTRAPTIONS -> goTo(Page.CONTRAPTIONS, -1);
            case ROOT_WAYSTONES -> goTo(Page.WAYSTONES, -1);
            case ROOT_RULES -> goTo(Page.RULES, -1);
            case ROOT_RELOAD -> {
                String summary = CTPConfig.load();
                player.sendSystemMessage(Component.literal("[Arcadia CTP] " + summary)
                    .withStyle(ChatFormatting.GREEN));
                refresh();
            }
            default -> {
            }
        }
    }

    // --- CONTRAPTIONS -----------------------------------------------------------------

    private void clickContraptions(ServerPlayer player, int slot, boolean rightClick) {
        CTPConfig.Snapshot config = CTPConfig.get();
        ContraptionSettings ct = config.contraptions();
        switch (slot) {
            case BACK_SLOT -> goTo(Page.ROOT, -1);
            case CT_ENABLED -> save(player, config.withContraptions(ct.withEnabled(!ct.enabled())));
            case CT_BREAKING ->
                save(player, config.withContraptions(ct.withBlockBreaking(cycle(ct.blockBreaking(), rightClick))));
            case CT_DEPLOYER ->
                save(player, config.withContraptions(ct.withDeployer(cycle(ct.deployer(), rightClick))));
            case CT_STORAGE ->
                save(player, config.withContraptions(ct.withStorageInterface(cycle(ct.storageInterface(), rightClick))));
            case CT_ASSEMBLY ->
                save(player, config.withContraptions(ct.withAssembly(cycle(ct.assembly(), rightClick))));
            case CT_PILOT ->
                save(player, config.withContraptions(ct.withPilotFallback(!ct.pilotFallback())));
            default -> {
            }
        }
    }

    // --- WAYSTONES --------------------------------------------------------------------

    private void clickWaystones(ServerPlayer player, int slot, boolean rightClick) {
        CTPConfig.Snapshot config = CTPConfig.get();
        WaystoneSettings ws = config.waystones();
        switch (slot) {
            case BACK_SLOT -> goTo(Page.ROOT, -1);
            case WS_ENABLED -> save(player, config.withWaystones(ws.withEnabled(!ws.enabled())));
            case WS_VISIBILITY ->
                save(player, config.withWaystones(ws.withTeamVisibility(cycle(ws.teamVisibility(), rightClick))));
            case WS_INTERACTION ->
                save(player, config.withWaystones(ws.withClaimInteraction(cycle(ws.claimInteraction(), rightClick))));
            default -> {
            }
        }
    }

    // --- RULES ------------------------------------------------------------------------

    private void clickRules(ServerPlayer player, int slot, boolean rightClick, boolean shift) {
        CTPConfig.Snapshot config = CTPConfig.get();
        List<Rule> rules = config.engine().rules();

        if (slot == BACK_SLOT) {
            goTo(Page.ROOT, -1);
            return;
        }
        if (slot == RULES_PREV && rulePage > 0) {
            rulePage--;
            refresh();
            return;
        }
        if (slot == RULES_NEXT && (rulePage + 1) * RULES_PER_PAGE < rules.size()) {
            rulePage++;
            refresh();
            return;
        }
        if (slot == RULES_ADD) {
            promptNewRule(player);
            return;
        }

        int index = ruleIndexAt(slot);
        if (index < 0 || index >= rules.size()) {
            return;
        }

        List<Rule> updated = new ArrayList<>(rules);
        if (shift) {
            // Order is the whole semantics of the engine, so reordering has to be reachable.
            int target = rightClick ? index + 1 : index - 1;
            if (target < 0 || target >= updated.size()) {
                return;
            }
            updated.add(target, updated.remove(index));
            save(player, config.withRules(updated));
            return;
        }
        if (rightClick) {
            goTo(Page.RULE_EDIT, index);
            return;
        }
        updated.set(index, rules.get(index).withPolicy(cycle(rules.get(index).policy(), false)));
        save(player, config.withRules(updated));
    }

    private int ruleIndexAt(int slot) {
        if (slot < RULES_FIRST || slot >= RULES_FIRST + RULES_PER_PAGE) {
            return -1;
        }
        return rulePage * RULES_PER_PAGE + (slot - RULES_FIRST);
    }

    private void promptNewRule(ServerPlayer player) {
        ChatPrompts.ask(player, "Nom de la nouvelle règle ?", null,
            input -> {
                CTPConfig.Snapshot config = CTPConfig.get();
                List<Rule> rules = new ArrayList<>(config.engine().rules());
                // A new rule starts on the block it will most likely target: an empty rule
                // would match everything and be dropped on the next reload.
                rules.add(Rule.of(input, List.of(), List.of("create:*"), Policy.TEAM_ONLY));
                applyQuietly(player, config.withRules(rules));
                open(player, Page.RULE_EDIT, rules.size() - 1);
            },
            () -> open(player, Page.RULES, -1));
    }

    // --- RULE_EDIT --------------------------------------------------------------------

    private void clickRuleEdit(ServerPlayer player, int slot, boolean rightClick, boolean shift) {
        CTPConfig.Snapshot config = CTPConfig.get();
        List<Rule> rules = config.engine().rules();
        if (ruleIndex < 0 || ruleIndex >= rules.size()) {
            goTo(Page.RULES, -1);
            return;
        }
        Rule rule = rules.get(ruleIndex);

        switch (slot) {
            case BACK_SLOT -> goTo(Page.RULES, ruleIndex);
            case EDIT_NAME -> ChatPrompts.ask(player, "Nouveau nom de la règle ?", rule.name(),
                input -> replaceRule(player, rule.withName(input)),
                () -> open(player, Page.RULE_EDIT, ruleIndex));
            case EDIT_PACKETS -> ChatPrompts.ask(player,
                "Packets (séparés par des virgules, '-' pour vider) ?", String.join(", ", rule.rawPackets()),
                input -> replaceRule(player, rule.withPackets(parseList(input))),
                () -> open(player, Page.RULE_EDIT, ruleIndex));
            case EDIT_BLOCKS -> ChatPrompts.ask(player,
                "Blocs (séparés par des virgules, '-' pour vider) ?", String.join(", ", rule.rawBlocks()),
                input -> replaceRule(player, rule.withBlocks(parseList(input))),
                () -> open(player, Page.RULE_EDIT, ruleIndex));
            // Cycled in place: this click needs no chat prompt, so the menu never closed
            // and reopening it would make the screen flicker on every step of the cycle.
            case EDIT_POLICY -> {
                List<Rule> updated = new ArrayList<>(rules);
                updated.set(ruleIndex, rule.withPolicy(cycle(rule.policy(), rightClick)));
                save(player, config.withRules(updated));
            }
            case EDIT_DELETE -> {
                if (!shift) {
                    player.sendSystemMessage(Component.literal(
                        "[Arcadia CTP] Shift + clic pour confirmer la suppression.")
                        .withStyle(ChatFormatting.YELLOW));
                    return;
                }
                List<Rule> updated = new ArrayList<>(rules);
                updated.remove(ruleIndex);
                page = Page.RULES;
                ruleIndex = -1;
                save(player, config.withRules(updated));
            }
            default -> {
            }
        }
    }

    private void replaceRule(ServerPlayer player, Rule rule) {
        CTPConfig.Snapshot config = CTPConfig.get();
        List<Rule> rules = new ArrayList<>(config.engine().rules());
        if (ruleIndex < 0 || ruleIndex >= rules.size()) {
            return;
        }
        rules.set(ruleIndex, rule);
        applyQuietly(player, config.withRules(rules));
        open(player, Page.RULE_EDIT, ruleIndex);
    }

    private static List<String> parseList(String input) {
        if (input.equals("-") || input.equalsIgnoreCase("none") || input.isBlank()) {
            return List.of();
        }
        return Arrays.stream(input.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    // --- shared -----------------------------------------------------------------------

    private static Policy cycle(Policy current, boolean backwards) {
        int index = CYCLE.indexOf(current);
        int size = CYCLE.size();
        return CYCLE.get(((index + (backwards ? -1 : 1)) % size + size) % size);
    }

    private void goTo(Page target, int index) {
        page = target;
        ruleIndex = index;
        if (index >= 0) {
            rulePage = index / RULES_PER_PAGE;
        }
        refresh();
    }

    private void save(ServerPlayer player, CTPConfig.Snapshot snapshot) {
        applyQuietly(player, snapshot);
        refresh();
    }

    private static void applyQuietly(ServerPlayer player, CTPConfig.Snapshot snapshot) {
        String problem = CTPConfig.apply(snapshot);
        if (problem != null) {
            player.sendSystemMessage(Component.literal("[Arcadia CTP] " + problem)
                .withStyle(ChatFormatting.RED));
        }
    }

    private void refresh() {
        container.clearContent();
        switch (page) {
            case ROOT -> buildRoot();
            case CONTRAPTIONS -> buildContraptions();
            case WAYSTONES -> buildWaystones();
            case RULES -> buildRules();
            case RULE_EDIT -> buildRuleEdit();
        }
        broadcastChanges();
    }

    private void buildRoot() {
        CTPConfig.Snapshot config = CTPConfig.get();
        container.setItem(ROOT_INFO, info(Items.BOOK, "Arcadia CTP",
            "§7Claim & Team Protection",
            "",
            "FTB Chunks : " + (ModPresence.hasFtbChunks() ? "présent" : "ABSENT - aucune protection"),
            "Règles : " + config.engine().rules().size(),
            "Fichier : config/arcadia-ctp.json"));

        container.setItem(ROOT_ENABLED, toggle("Protection des réglages Create", config.enabled(),
            "Intercepte les packets de configuration",
            "de Create (molettes, filtres, seuils)."));
        container.setItem(ROOT_DEFAULT_POLICY, policy("Politique par défaut", config.engine().defaultPolicy(),
            "Appliquée quand aucune règle ne correspond."));
        container.setItem(ROOT_NOTIFY, toggle("Prévenir le joueur bloqué", config.notifyOnDeny(),
            "Message en barre d'action, anti-spam 2 s."));
        container.setItem(ROOT_BYPASS, toggle("Respecter le bypass admin", config.respectAdminBypass(),
            "Laisse passer /ftbchunks admin bypass."));
        container.setItem(ROOT_LOG, toggle("Journaliser les refus", config.logDenials(),
            "Joueur, position, bloc, règle et politique",
            "dans les logs du serveur."));

        container.setItem(ROOT_CONTRAPTIONS, nav(Items.PISTON, "Contraptions",
            "Foreuses, scies, deployers, PSI,",
            "et vol de blocs à l'assemblage.",
            "",
            config.contraptions().enabled() ? "§aActif" : "§7Inactif"));
        container.setItem(ROOT_WAYSTONES, nav(Items.LODESTONE, "Waystones",
            "Waystones \"visible par l'équipe\"",
            "résolu via FTB Teams, pas via",
            "l'équipe de scoreboard vanilla.",
            "",
            config.waystones().enabled() ? "§aActif" : "§7Inactif"));
        container.setItem(ROOT_RULES, nav(Items.WRITABLE_BOOK, "Règles",
            config.engine().rules().size() + " règle(s)",
            "Première correspondance gagnante."));
        container.setItem(ROOT_RELOAD, action(Items.HOPPER, "Recharger le fichier",
            "Relit config/arcadia-ctp.json.",
            "Annule les modifications non écrites."));
    }

    private void buildContraptions() {
        ContraptionSettings ct = CTPConfig.get().contraptions();
        container.setItem(BACK_SLOT, back());
        container.setItem(CT_ENABLED, toggle("Protection des contraptions", ct.enabled(),
            "Coupe les quatre gardes d'un coup."));
        container.setItem(CT_BREAKING, policy("Casse de blocs", ct.blockBreaking(),
            "Foreuse, scie, moissonneuse -",
            "et tout acteur d'addon non reconnu."));
        container.setItem(CT_DEPLOYER, policy("Deployer", ct.deployer(),
            "Pose, casse et utilise des objets",
            "à la position visitée."));
        container.setItem(CT_STORAGE, policy("Portable Storage Interface", ct.storageInterface(),
            "Le vecteur qui vide les coffres."));
        container.setItem(CT_ASSEMBLY, policy("Assemblage", ct.assembly(),
            "Empêche une contraption d'embarquer",
            "les blocs du claim voisin."));
        container.setItem(CT_PILOT, toggle("Repli sur le pilote", ct.pilotFallback(),
            "Une contraption sans équipe emprunte",
            "les droits du joueur qui la conduit."));
    }

    private void buildWaystones() {
        WaystoneSettings ws = CTPConfig.get().waystones();
        container.setItem(BACK_SLOT, back());
        container.setItem(WS_ENABLED, toggle("Visibilité équipe corrigée", ws.enabled(),
            "Waystones distribue une waystone",
            "\"équipe\" à tous ceux qui partagent",
            "l'équipe de scoreboard du proprio -",
            "soit, chez nous, son grade."));
        container.setItem(WS_VISIBILITY, policy("Audience", ws.teamVisibility(),
            "Qui voit une waystone \"équipe\".",
            "§8ALLOW : laisse Waystones décider",
            "§8TEAM_ONLY : la party FTB du proprio",
            "§8ALLY_ONLY : + les équipes alliées",
            "§8DENY : le proprio seul"));
        container.setItem(WS_INTERACTION, policy("Enregistrement dans un claim", ws.claimInteraction(),
            "Qui peut clic droit une waystone",
            "posée chez quelqu'un d'autre.",
            "§8ALLOW : n'importe quel visiteur",
            "§8ALLY_ONLY : équipe + alliés",
            "§8TEAM_ONLY : équipe seule",
            "§8CHECK : rendu à FTB Chunks",
            "§8DENY : personne"));
    }

    private void buildRules() {
        List<Rule> rules = CTPConfig.get().engine().rules();
        container.setItem(BACK_SLOT, back());

        int start = rulePage * RULES_PER_PAGE;
        for (int offset = 0; offset < RULES_PER_PAGE; offset++) {
            int index = start + offset;
            if (index >= rules.size()) {
                break;
            }
            Rule rule = rules.get(index);
            container.setItem(RULES_FIRST + offset, item(policyItem(rule.policy()), ChatFormatting.WHITE,
                (index + 1) + ". " + rule.name(),
                "§7Politique : §f" + rule.policy(),
                "§7Packets : §f" + display(rule.rawPackets()),
                "§7Blocs : §f" + display(rule.rawBlocks()),
                "",
                "§eClic gauche §7: politique suivante",
                "§eClic droit §7: éditer",
                "§eShift + clic §7: monter / descendre"));
        }

        if (rulePage > 0) {
            container.setItem(RULES_PREV, nav(Items.ARROW, "Page précédente"));
        }
        if (start + RULES_PER_PAGE < rules.size()) {
            container.setItem(RULES_NEXT, nav(Items.ARROW, "Page suivante"));
        }
        container.setItem(RULES_ADD, action(Items.EMERALD, "Ajouter une règle",
            "Demande un nom dans le chat."));
    }

    private void buildRuleEdit() {
        List<Rule> rules = CTPConfig.get().engine().rules();
        container.setItem(BACK_SLOT, back());
        if (ruleIndex < 0 || ruleIndex >= rules.size()) {
            return;
        }
        Rule rule = rules.get(ruleIndex);

        container.setItem(EDIT_NAME, action(Items.NAME_TAG, "Nom",
            "§f" + rule.name(), "", "§eClic §7: modifier dans le chat"));
        container.setItem(EDIT_PACKETS, action(Items.PAPER, "Packets",
            "§f" + display(rule.rawPackets()),
            "§7Nom simple ou complet, jokers * et ?",
            "§7Vide = tous les packets.",
            "", "§eClic §7: modifier dans le chat"));
        container.setItem(EDIT_BLOCKS, action(Items.STONE, "Blocs",
            "§f" + display(rule.rawBlocks()),
            "§7Id de bloc, #tag, jokers * et ?",
            "§7Vide = tous les blocs.",
            "", "§eClic §7: modifier dans le chat"));
        container.setItem(EDIT_POLICY, policy("Politique", rule.policy(),
            "§eClic droit §7: politique précédente"));
        container.setItem(EDIT_DELETE, item(Items.BARRIER, ChatFormatting.RED, "Supprimer la règle",
            "§7Shift + clic pour confirmer."));
    }

    private static String display(List<String> values) {
        return values.isEmpty() ? "(tous)" : String.join(", ", values);
    }

    // --- item helpers -----------------------------------------------------------------

    private static ItemStack back() {
        return item(Items.ARROW, ChatFormatting.GRAY, "Retour");
    }

    private static ItemStack toggle(String title, boolean on, String... lines) {
        List<String> lore = new ArrayList<>(List.of(lines));
        lore.add("");
        lore.add(on ? "§aActivé" : "§cDésactivé");
        lore.add("§eClic §7: basculer");
        return item(on ? Items.LIME_DYE : Items.GRAY_DYE,
            on ? ChatFormatting.GREEN : ChatFormatting.GRAY, title, lore.toArray(new String[0]));
    }

    private static ItemStack policy(String title, Policy value, String... lines) {
        List<String> lore = new ArrayList<>(List.of(lines));
        lore.add("");
        lore.add("§7Politique : §f" + value);
        lore.add("§8" + describe(value));
        lore.add("§eClic §7: politique suivante");
        return item(policyItem(value), ChatFormatting.YELLOW, title, lore.toArray(new String[0]));
    }

    private static String describe(Policy policy) {
        return switch (policy) {
            case ALLOW -> "toujours autorisé";
            case CHECK -> "comme un clic droit (FTB Chunks)";
            case ALLY_ONLY -> "équipe propriétaire + alliés";
            case TEAM_ONLY -> "équipe propriétaire seule";
            case DENY -> "jamais autorisé";
        };
    }

    private static Item policyItem(Policy policy) {
        return switch (policy) {
            case ALLOW -> Items.LIME_DYE;
            case CHECK -> Items.YELLOW_DYE;
            case ALLY_ONLY -> Items.ORANGE_DYE;
            case TEAM_ONLY -> Items.RED_DYE;
            case DENY -> Items.BARRIER;
        };
    }

    private static ItemStack nav(Item item, String title, String... lines) {
        return item(item, ChatFormatting.AQUA, title, lines);
    }

    private static ItemStack action(Item item, String title, String... lines) {
        return item(item, ChatFormatting.GOLD, title, lines);
    }

    private static ItemStack info(Item item, String title, String... lines) {
        return item(item, ChatFormatting.WHITE, title, lines);
    }

    private static ItemStack item(Item item, ChatFormatting titleColor, String title, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(titleColor));
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static final class LockedSlot extends Slot {

        private LockedSlot(net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
