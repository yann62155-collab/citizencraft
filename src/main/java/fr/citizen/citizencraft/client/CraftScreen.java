package fr.citizen.citizencraft.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Le "Livre des Recettes du Royaume" : recherche, categories, liste des objets craftables et detail de la recette.
 */
public class CraftScreen extends Screen {

    // ---- Palette medievale (noir, or ancien, rouge sombre, parchemin) ----
    private static final int C_DIM = 0xB0000000;
    private static final int C_PANEL = 0xF0161210;
    private static final int C_PANEL_INNER = 0xF0201914;
    private static final int C_GOLD = 0xFFD4AF37;
    private static final int C_GOLD_DARK = 0xFF8C6D2A;
    private static final int C_RED = 0xFF7A1F1F;
    private static final int C_RED_LIGHT = 0xFFC0504D;
    private static final int C_PARCHMENT = 0xFFE8D9B0;
    private static final int C_GREY = 0xFF8A7F70;
    private static final int C_SLOT = 0xFF2A211A;
    private static final int C_SLOT_BORDER = 0xFF5A4630;

    private static final String NOTE_FORGE =
            "Artefacts, armures, filets de pêche, boîtes aux lettres, lettres, beuglantes, cartons d'envoi et affiches doivent être confectionnés à la forge.";
    private static final String NOTE_TAVERN =
            "Nourriture et bière doivent être préparées à la taverne ou au restaurant.";
    private static final String NOTE_MENUISIER =
            "Les portes à clé et les clés ne peuvent être fabriquées que par le menuisier.";
    private static final String NOTE_PECHE =
            "La pêche est réservée aux pêcheurs. Seuls le pêcheur, la taverne et le restaurant peuvent cuisiner le poisson.";

    private static final int SLOT = 18;
    private static final int CAT_H = 12;

    // ---- Donnees ----
    private final RecipeIndex index;
    private List<RecipeIndex.Entry> filtered = List.of();
    private final Map<CraftCategory, Integer> counts = new EnumMap<>(CraftCategory.class);
    private CraftCategory category = CraftCategory.ALL;
    private String searchText = "";
    private RecipeIndex.Entry selected;
    private int recipeIdx;
    private final Deque<RecipeIndex.Entry> history = new ArrayDeque<>();
    private int scrollRow;
    private List<AnimalTable.Animal> animals = List.of();
    private int animalScroll;
    private static final int ROW_H = 20;
    private boolean draggingScrollbar;

    // ---- Mise en page ----
    private int px, py, pw, ph;
    private int catX, catY, catW;
    private int gridX, gridY, cols, rows;
    private int barX;
    private int detailX, detailY, detailW, detailH;
    private int noticeY, noticeH;
    private List<FormattedCharSequence> noticeLines = List.of();
    private EditBox search;

    // ---- Zones cliquables / survolables (reconstruites a chaque image) ----
    private record Hot(int x, int y, int w, int h, ItemStack stack, RecipeIndex.Entry target, Runnable action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private final List<Hot> hots = new ArrayList<>();

    public CraftScreen() {
        super(Component.literal("Livre des Recettes du Royaume"));
        this.index = RecipeIndex.build(Minecraft.getInstance());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @Override
    protected void init() {
        pw = Math.min(width - 12, 470);
        ph = Math.min(height - 12, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int inner = pw - 16;
        noticeLines = new ArrayList<>();
        noticeLines.addAll(font.split(Component.literal("⚒ " + NOTE_FORGE), inner));
        noticeLines.addAll(font.split(Component.literal("» " + NOTE_TAVERN), inner));
        noticeLines.addAll(font.split(Component.literal("» " + NOTE_PECHE), inner));
        noticeLines.addAll(font.split(Component.literal("» " + NOTE_MENUISIER), inner));
        noticeH = noticeLines.size() * 10 + 6;
        noticeY = py + ph - 6 - noticeH;

        catX = px + 6;
        catY = py + 40;
        catW = 120;

        detailW = Math.max(120, Math.min(170, pw * 2 / 5));
        detailX = px + pw - 6 - detailW;
        detailY = py + 40;
        detailH = noticeY - 4 - detailY;

        gridX = catX + catW + 6;
        gridY = py + 40;
        int gridAvailW = detailX - 6 - 6 - gridX; // 6 = barre de defilement
        cols = Math.max(1, gridAvailW / SLOT);
        rows = Math.max(1, (noticeY - 4 - gridY) / SLOT);
        barX = gridX + cols * SLOT + 2;

        search = new EditBox(font, gridX + 1, py + 22, cols * SLOT - 2, 14, Component.literal("Recherche"));
        search.setMaxLength(64);
        search.setHint(Component.literal("Rechercher… (@mod)").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(searchText);
        search.setResponder(s -> {
            searchText = s;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        refilter();
    }

    private void refilter() {
        String[] tokens = RecipeIndex.normalize(searchText.trim()).split("\\s+");
        counts.clear();
        List<RecipeIndex.Entry> out = new ArrayList<>();
        for (RecipeIndex.Entry e : index.entries) {
            if (!matches(e, tokens)) continue;
            counts.merge(CraftCategory.ALL, 1, Integer::sum);
            counts.merge(e.category, 1, Integer::sum);
            if (category == CraftCategory.ALL || e.category == category) out.add(e);
        }
        filtered = out;
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());

        List<AnimalTable.Animal> an = new ArrayList<>();
        for (AnimalTable.Animal a : AnimalTable.ANIMALS) {
            String key = RecipeIndex.normalize(a.name() + " " + a.entityId() + " " + a.productName() + " " + a.kind());
            boolean ok = true;
            for (String t : tokens) if (!t.isEmpty() && !t.startsWith("@") && !key.contains(t)) ok = false;
            if (ok) an.add(a);
        }
        animals = an;
        counts.put(CraftCategory.ANIMAUX, an.size());
        animalScroll = Mth.clamp(animalScroll, 0, maxAnimalScroll());
    }

    private static boolean matches(RecipeIndex.Entry e, String[] tokens) {
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            if (t.startsWith("@")) {
                String m = t.substring(1);
                if (!m.isEmpty() && !e.modId.contains(m) && !RecipeIndex.normalize(e.modName).contains(m)) return false;
            } else if (!e.searchKey.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private int totalRows() {
        return (filtered.size() + cols - 1) / cols;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - rows);
    }

    // =====================================================================
    //  Rendu
    // =====================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Pas de flou vanilla : fond assombri + parchemin sombre a bordure doree.
        g.fill(0, 0, width, height, C_DIM);
        frame(g, px, py, pw, ph, C_PANEL, C_GOLD);
        g.fill(px + 2, py + 2, px + pw - 2, py + 3, C_RED);

        String title = "✦ " + getTitle().getString() + " ✦";
        g.drawString(font, title, px + (pw - font.width(title)) / 2, py + 8, C_GOLD, true);

        frame(g, catX - 2, catY - 2, catW + 4, noticeY - 4 - catY + 4, C_PANEL_INNER, C_GOLD_DARK);
        if (category == CraftCategory.ANIMAUX) {
            frame(g, gridX - 2, gridY - 2, detailX + detailW + 2 - (gridX - 2), detailH + 4, C_PANEL_INNER, C_GOLD_DARK);
        } else {
            frame(g, gridX - 2, gridY - 2, cols * SLOT + 10, rows * SLOT + 4, C_PANEL_INNER, C_GOLD_DARK);
            frame(g, detailX - 2, detailY - 2, detailW + 4, detailH + 4, C_PANEL_INNER, C_GOLD_DARK);
        }
        frame(g, px + 4, noticeY, pw - 8, noticeH, 0xF0281010, C_RED);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hots.clear();
        super.render(g, mouseX, mouseY, partialTick); // fond + barre de recherche

        renderCategories(g, mouseX, mouseY);
        if (category == CraftCategory.ANIMAUX) {
            renderAnimals(g, mouseX, mouseY);
        } else {
            renderGrid(g, mouseX, mouseY);
            renderDetail(g, mouseX, mouseY);
        }

        int ny = noticeY + 4;
        for (int i = 0; i < noticeLines.size(); i++) {
            g.drawString(font, noticeLines.get(i), px + 8, ny + i * 10, C_RED_LIGHT, false);
        }

        // Info-bulle
        if (pendingTooltip != null) {
            g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
            return;
        }
        for (Hot h : hots) {
            if (h.stack != null && !h.stack.isEmpty() && h.in(mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, h.stack));
                if (h.target != null && h.target.isInfo() && h.stack == h.target.icon && !lines.isEmpty()) {
                    lines.set(0, Component.literal(h.target.name).withStyle(ChatFormatting.GOLD));
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(h.stack.getItem());
                lines.add(Component.literal(RecipeIndex.modName(id.getNamespace()))
                        .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
                RecipeIndex.Entry e = index.byItem.get(h.stack.getItem());
                if (h.target != null && e != null && e != selected) {
                    lines.add(Component.literal("Clic : voir la recette").withStyle(ChatFormatting.GOLD));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    private void renderCategories(GuiGraphics g, int mx, int my) {
        int y = catY;
        for (CraftCategory c : CraftCategory.values()) {
            int n = counts.getOrDefault(c, 0);
            boolean active = c == category;
            boolean hover = mx >= catX && mx < catX + catW && my >= y && my < y + CAT_H;
            int bg = active ? C_RED : hover ? 0xFF3A2C20 : 0x00000000;
            if (bg != 0) g.fill(catX, y, catX + catW, y + CAT_H, bg);
            if (active) g.fill(catX, y, catX + 2, y + CAT_H, C_GOLD);

            int color = n == 0 ? C_GREY : active ? C_GOLD : C_PARCHMENT;
            g.drawString(font, c.label, catX + 5, y + 2, color, false);
            String cnt = String.valueOf(n);
            g.drawString(font, cnt, catX + catW - 3 - font.width(cnt), y + 2, active ? C_GOLD : C_GREY, false);

            final CraftCategory cat = c;
            hots.add(new Hot(catX, y, catW, CAT_H, null, null, () -> {
                category = cat;
                scrollRow = 0;
                refilter();
            }));
            y += CAT_H;
        }
    }

    private void renderGrid(GuiGraphics g, int mx, int my) {
        if (filtered.isEmpty()) {
            String s = "Aucune recette trouvée.";
            g.drawString(font, s, gridX + (cols * SLOT - font.width(s)) / 2, gridY + rows * SLOT / 2 - 4, C_GREY, false);
        }
        int start = scrollRow * cols;
        int end = Math.min(filtered.size(), start + rows * cols);
        for (int i = start; i < end; i++) {
            int rel = i - start;
            int x = gridX + (rel % cols) * SLOT;
            int y = gridY + (rel / cols) * SLOT;
            RecipeIndex.Entry e = filtered.get(i);
            boolean hover = mx >= x && my >= y && mx < x + SLOT && my < y + SLOT;
            if (e == selected) {
                g.fill(x, y, x + SLOT, y + SLOT, C_GOLD);
                g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF4A3010);
            } else if (hover) {
                g.fill(x, y, x + SLOT, y + SLOT, 0x60FFFFFF);
            }
            g.renderItem(e.icon, x + 1, y + 1);
            if (e.forgeRequired) g.fill(x + 14, y + 1, x + 17, y + 4, C_RED_LIGHT);   // petit repere : forge
            if (e.tavernRequired) g.fill(x + 14, y + 1, x + 17, y + 4, 0xFFD08A30); // petit repere : taverne
            hots.add(new Hot(x, y, SLOT, SLOT, e.icon, e, () -> select(e, false)));
        }

        // Barre de defilement
        int trackH = rows * SLOT;
        g.fill(barX, gridY, barX + 4, gridY + trackH, 0xFF0E0B09);
        int max = maxScroll();
        if (max > 0) {
            int thumbH = Math.max(12, trackH * rows / totalRows());
            int thumbY = gridY + (trackH - thumbH) * scrollRow / max;
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, C_GOLD_DARK);
        }
    }

    private void renderDetail(GuiGraphics g, int mx, int my) {
        int x = detailX + 4;
        int w = detailW - 8;
        int y = detailY + 4;

        if (selected == null) {
            List<FormattedCharSequence> help = font.split(Component.literal(
                    "Choisissez un objet pour voir sa recette.\n\nAstuce : tapez @nomdumod dans la recherche pour filtrer par mod."), w);
            for (FormattedCharSequence l : help) {
                g.drawString(font, l, x, y, C_GREY, false);
                y += 10;
            }
            return;
        }

        // Retour
        if (!history.isEmpty()) {
            boolean hov = mx >= x && mx < x + 40 && my >= y && my < y + 10;
            g.drawString(font, "← Retour", x, y, hov ? C_GOLD : C_GREY, false);
            hots.add(new Hot(x, y, 44, 10, null, null, this::goBack));
            y += 12;
        }

        // Nom de l'objet
        g.renderItem(selected.icon, x, y);
        List<FormattedCharSequence> name = font.split(Component.literal(selected.name), w - 20);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            g.drawString(font, name.get(i), x + 20, y + (name.size() == 1 ? 4 : i * 9), C_GOLD, false);
        }
        hots.add(new Hot(x, y, 16, 16, selected.icon, null, null));
        y += 19;
        g.drawString(font, font.plainSubstrByWidth(selected.modName, w), x, y, C_GREY, false);
        y += 11;

        // Fiche d'information (objet sans craft)
        if (selected.isInfo()) {
            for (FormattedCharSequence l : font.split(Component.literal(selected.info == null ? "" : selected.info), w)) {
                g.drawString(font, l, x, y, C_PARCHMENT, false);
                y += 10;
            }
            y += 4;
            for (int i = 0; i < selected.infoItems.size(); i++) {
                int sx = x + (i % 6) * SLOT, sy = y + (i / 6) * SLOT;
                frame(g, sx, sy, SLOT, SLOT, C_SLOT, C_SLOT_BORDER);
                ItemStack st = selected.infoItems.get(i);
                g.renderItem(st, sx + 1, sy + 1);
                RecipeIndex.Entry t = index.byItem.get(st.getItem());
                hots.add(new Hot(sx + 1, sy + 1, 16, 16, st, t, t == null ? null : () -> select(t, true)));
            }
            if (selected.modId.equals(RecipeIndex.NS_MENUISERIE)) {
                y += ((selected.infoItems.size() + 5) / 6) * SLOT + 6;
                List<FormattedCharSequence> lines = font.split(Component.literal("⚒ Réservé au menuisier"), w - 6);
                int boxH = lines.size() * 10 + 4;
                if (y + boxH <= detailY + detailH) {
                    g.fill(x, y, x + w, y + boxH, 0xFF3A1212);
                    g.fill(x, y, x + 2, y + boxH, C_RED_LIGHT);
                    for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 5, y + 3 + i * 10, C_PARCHMENT, false);
                }
            }
            return;
        }

        // Navigation entre recettes
        List<RecipeHolder<?>> recipes = selected.recipes;
        recipeIdx = Mth.clamp(recipeIdx, 0, recipes.size() - 1);
        RecipeHolder<?> holder = recipes.get(recipeIdx);
        Recipe<?> recipe = holder.value();

        String station = stationName(recipe);
        if (recipes.size() > 1) {
            String nav = (recipeIdx + 1) + "/" + recipes.size();
            int navW = font.width(nav);
            int ax = x + w - navW - 22;
            boolean hl = mx >= ax && mx < ax + 8 && my >= y && my < y + 10;
            boolean hr = mx >= x + w - 8 && mx < x + w && my >= y && my < y + 10;
            g.drawString(font, "<", ax, y, hl ? C_GOLD : C_PARCHMENT, false);
            g.drawString(font, nav, ax + 11, y, C_PARCHMENT, false);
            g.drawString(font, ">", x + w - 6, y, hr ? C_GOLD : C_PARCHMENT, false);
            hots.add(new Hot(ax - 2, y - 1, 10, 11, null, null, () -> recipeIdx = (recipeIdx - 1 + recipes.size()) % recipes.size()));
            hots.add(new Hot(x + w - 8, y - 1, 10, 11, null, null, () -> recipeIdx = (recipeIdx + 1) % recipes.size()));
            g.drawString(font, font.plainSubstrByWidth(station, ax - x - 4), x, y, C_PARCHMENT, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(station, w), x, y, C_PARCHMENT, false);
        }
        y += 13;

        // Recette
        y = renderRecipe(g, recipe, x, y, w);
        y += 6;

        // Consigne RP
        boolean fishing = selected.category == CraftCategory.PECHE || isFish(selected);
        List<String> heads = new ArrayList<>();
        if (selected.forgeRequired) heads.add("⚒ À confectionner à la forge (forgeron)");
        else if (selected.tavernRequired) heads.add("» À préparer à la taverne / au restaurant");
        if (selected.modId.equals(RecipeIndex.NS_AFFICHE)) {
            heads.add("» Recopier : 1 affiche imprimée + 1 colorant noir (ou poche d'encre) + 1 à 7 affiches vierges "
                    + "= autant de copies. L'originale est rendue.");
        }
        if (selected.modId.equals(RecipeIndex.NS_ECLIPSE) && selected.item instanceof net.minecraft.world.item.ArmorItem) {
            heads.add("» Protège les yeux pendant l'éclipse. Perd 1 point de durabilité toutes les 5 s d'observation.");
            heads.add("» Réparation : verre teinté sur une enclume.");
        }
        if (selected.id.getPath().contains("tinted_glass")) {
            heads.add("» Sert à fabriquer et à réparer (enclume) le casque d'éclipse.");
        }
        String hibou = hibouInfo(selected);
        if (hibou != null) heads.add("» " + hibou);
        String realismeInfo = RealismeRules.describe(selected.icon);
        if (realismeInfo != null) heads.add("» " + realismeInfo);
        if (RealismeRules.active() && recipe.getType() == RecipeType.CRAFTING) {
            RealismeRules.Plan plan = RealismeRules.plan(safeResult(recipe), recipe.getIngredients());
            if (plan.instant()) {
                heads.add("» Craft instantané, sans risque d'échec.");
            } else {
                if (plan.tool() != null) {
                    heads.add("⚒ Outil requis : " + RealismeRules.toolName(plan.tool()) + " (dans l'inventaire)");
                }
                String rate = plan.tool() != null
                        ? plan.rate() + " % (" + RealismeRules.rateWithTool(plan.rate()) + " % avec un outil en bon état)"
                        : plan.rate() + " %";
                heads.add("» Réussite " + rate + " · " + (plan.ticks() / 20f) + " s. Échec : chaque ingrédient a "
                        + RealismeRules.PERTE_POURCENT + " % de risque d'être perdu.");
            }
        }
        if (selected.modId.equals(RecipeIndex.NS_MENUISERIE)) {
            String p = selected.id.getPath();
            if (p.contains("machine")) heads.add("» Sert à forger les clés vierges et à dupliquer les clés.");
            else if (p.contains("bench") || p.contains("etabli") || p.contains("table") || p.contains("workshop"))
                heads.add("» Sert à fabriquer les portes à clé (avec leur clé).");
            heads.add("⚒ Portes à clé et clés : réservées au menuisier (pas à l'établi classique).");
        }
        if (RecipeIndex.isFishBait(selected)) {
            heads.add("» La chair putréfiée et l'œil d'araignée servent aussi d'appât.");
        }
        if (fishing) heads.add("» Pêche réservée aux pêcheurs. Poisson cuisiné uniquement par le pêcheur, la taverne ou le restaurant.");
        for (String head : heads) {
            List<FormattedCharSequence> lines = font.split(Component.literal(head), w - 6);
            int boxH = lines.size() * 10 + 4;
            if (y + boxH <= detailY + detailH) {
                g.fill(x, y, x + w, y + boxH, 0xFF3A1212);
                g.fill(x, y, x + 2, y + boxH, C_RED_LIGHT);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(font, lines.get(i), x + 5, y + 3 + i * 10, C_PARCHMENT, false);
                }
            }
            y += boxH + 3;
        }
    }

    /** Aide des objets du Hibou Postal (d'apres son README 1.2.0). */
    private static String hibouInfo(RecipeIndex.Entry e) {
        if (!e.modId.equals(RecipeIndex.NS_HIBOU)) return null;
        String p = e.id.getPath();
        if (p.contains("beuglante")) {
            return "Lettre hurlante : volière (/hibou), onglet « ‼ Beuglante », ou /hibou beugler. Cri de 100 caractères "
                    + "+ voix de 20 s (PC). Éclate sur le destinataire (entendue à 24 blocs), seule après 60 s. "
                    + "Sans beuglante vierge : 1 papier + 1 poudre à canon.";
        }
        if (p.contains("letter")) {
            return "Chaque envoi consomme une lettre vierge. Clic droit avec une lettre vierge : ouvre la volière.";
        }
        if (p.contains("parcel")) {
            return "Carton de 5 emplacements : /hibou colis, ou clic droit avec un carton. Un carton vide est consommé par envoi.";
        }
        if (p.contains("mailbox")) {
            return "Appartient à celui qui la pose (lui seul l'ouvre et la casse). 27 emplacements, retrait seul. "
                    + "Reçoit le courrier quand vous êtes déconnecté ou que votre besace est pleine.";
        }
        return null;
    }

    /** Poissons (crus ou cuits) : vanilla + mots-cles dans l'identifiant. */
    private static boolean isFish(RecipeIndex.Entry e) {
        String p = e.id.getPath();
        return p.contains("cod") || p.contains("salmon") || p.contains("fish") || p.contains("poisson") || p.contains("fillet");
    }

    // ---- Onglet Animaux (citizenchasse) ----
    private int animalListTop() { return gridY + 34; }
    private int animalVisibleRows() { return Math.max(1, (detailY + detailH - 2 - animalListTop()) / ROW_H); }
    private int maxAnimalScroll() { return Math.max(0, animals.size() - animalVisibleRows()); }

    private void renderAnimals(GuiGraphics g, int mx, int my) {
        int x = gridX + 2;
        int right = detailX + detailW - 2;
        int w = right - x;
        int colWeight = x + Math.max(90, w * 30 / 100);
        int colPieces = colWeight + 62;
        int colProduct = colPieces + 58;

        g.drawString(font, "Ce que rapporte chaque bête à la table de boucher / de découpe", x, gridY + 1, C_GOLD, false);
        String rule = "Viande : " + AnimalTable.MEAT_BASE_PIECES + " morceaux min., +1 par tranche de "
                + fmt(AnimalTable.MEAT_KG_PER_PIECE) + " kg au-delà de " + fmt(AnimalTable.MEAT_MIN_KG) + " kg. Gros poids rares.";
        g.drawString(font, font.plainSubstrByWidth(rule, w), x, gridY + 11, C_GREY, false);

        int hy = animalListTop() - 1;
        g.fill(x, hy, right, hy + 1, C_GOLD_DARK);

        int top = animalListTop() + 2;
        int vis = animalVisibleRows();
        if (animals.isEmpty()) {
            g.drawString(font, "Aucun animal trouvé.", x + 4, top + 4, C_GREY, false);
        }
        for (int i = 0; i < vis && animalScroll + i < animals.size(); i++) {
            AnimalTable.Animal a = animals.get(animalScroll + i);
            int y = top + i * ROW_H;
            if (i % 2 == 0) g.fill(x, y - 1, right, y + ROW_H - 1, 0x30FFFFFF);

            ItemStack egg = a.egg();
            g.renderItem(egg, x + 1, y + 1);
            hots.add(new Hot(x + 1, y + 1, 16, 16, egg, null, null));
            g.drawString(font, font.plainSubstrByWidth(a.name(), colWeight - x - 24), x + 21, y + 5, C_PARCHMENT, false);

            g.drawString(font, fmt(a.minKg()) + " – " + fmt(a.maxKg()) + " kg", colWeight, y + 5, C_PARCHMENT, false);

            int min = a.minPieces(), max = a.maxPieces();
            g.drawString(font, min == max ? String.valueOf(min) : min + " – " + max, colPieces, y + 5, C_GOLD, false);

            ItemStack prod = a.product();
            g.renderItem(prod, colProduct, y + 1);
            hots.add(new Hot(colProduct, y + 1, 16, 16, prod, null, null));
            g.drawString(font, font.plainSubstrByWidth(a.productName(), right - colProduct - 22), colProduct + 20, y + 5,
                    a.kind() == AnimalTable.Kind.POISSON ? 0xFF7FB8D8 : a.kind() == AnimalTable.Kind.POULET ? 0xFFE8C080 : C_RED_LIGHT, false);

            if (mx >= x && mx < right && my >= y && my < y + ROW_H - 2 && !(mx >= x && mx < x + 18) && !(mx >= colProduct && mx < colProduct + 18)) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(a.name()).withStyle(ChatFormatting.GOLD));
                double step = a.kind() == AnimalTable.Kind.POISSON ? a.kgPerPiece() : AnimalTable.MEAT_KG_PER_PIECE * 5;
                for (double kg = a.minKg(); kg <= a.maxKg() + 1e-9; kg += step) {
                    tip.add(Component.literal(fmt(kg) + " kg → " + a.piecesAt(kg) + " " + a.productName().toLowerCase())
                            .withStyle(ChatFormatting.GRAY));
                    if (tip.size() > 12) break;
                }
                pendingTooltip = tip;
            }
        }

        // en-tetes de colonnes (dessines apres pour rester lisibles)
        g.drawString(font, "Poids", colWeight, hy - 9, C_GOLD_DARK, false);
        g.drawString(font, "Morceaux", colPieces, hy - 9, C_GOLD_DARK, false);
        g.drawString(font, "Produit", colProduct, hy - 9, C_GOLD_DARK, false);
    }

    private List<Component> pendingTooltip;

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(java.util.Locale.FRENCH, "%.1f", d);
    }

    /** Dessine la recette ; renvoie le y sous le dessin. */
    private int renderRecipe(GuiGraphics g, Recipe<?> recipe, int x, int y, int w) {
        ItemStack result = safeResult(recipe);

        // --- Table de craft (avec forme) ---
        if (recipe instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3) {
            List<Ingredient> ing = shaped.getIngredients();
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    Ingredient in = (c < shaped.getWidth() && r < shaped.getHeight())
                            ? ing.get(r * shaped.getWidth() + c) : Ingredient.EMPTY;
                    slot(g, x + c * SLOT, y + r * SLOT, in);
                }
            }
            arrowAndResult(g, x + 3 * SLOT + 4, y + SLOT, result);
            return y + 3 * SLOT;
        }

        // --- Cuisson (four, haut fourneau, fumoir, feu de camp) ---
        if (recipe instanceof AbstractCookingRecipe cook) {
            List<Ingredient> ing = cook.getIngredients();
            slot(g, x, y + 4, ing.isEmpty() ? Ingredient.EMPTY : ing.get(0));
            arrowAndResult(g, x + SLOT + 4, y + 4, result);
            String time = (cook.getCookingTime() / 20f) + " s";
            g.drawString(font, "Durée : " + time, x, y + SLOT + 10, C_GREY, false);
            if (cook.getExperience() > 0) {
                g.drawString(font, "XP : " + cook.getExperience(), x, y + SLOT + 20, C_GREY, false);
                return y + SLOT + 30;
            }
            return y + SLOT + 20;
        }

        // --- Table de forgeron ---
        List<Ingredient> ing = new ArrayList<>(recipe.getIngredients());
        if (ing.isEmpty() && (recipe instanceof SmithingTransformRecipe || recipe instanceof SmithingTrimRecipe)) {
            ing = smithingIngredients(recipe);
        }
        ing.removeIf(Ingredient::isEmpty);

        // --- Generique (sans forme, tailleur de pierre, recettes de mods...) ---
        int shown = Math.min(ing.size(), 9);
        int gridRows = Math.max(1, (shown + 2) / 3);
        for (int i = 0; i < shown; i++) {
            slot(g, x + (i % 3) * SLOT, y + (i / 3) * SLOT, ing.get(i));
        }
        if (ing.isEmpty()) {
            g.drawString(font, "(ingrédients spéciaux)", x, y + 4, C_GREY, false);
        }
        arrowAndResult(g, x + 3 * SLOT + 4, y + (gridRows - 1) * SLOT / 2, result);
        int endY = y + gridRows * SLOT;
        if (ing.size() > 9) {
            g.drawString(font, "+ " + (ing.size() - 9) + " autres", x, endY + 2, C_GREY, false);
            endY += 12;
        }
        return endY;
    }

    private void arrowAndResult(GuiGraphics g, int x, int y, ItemStack result) {
        g.drawString(font, "→", x, y + 5, C_GOLD, false);
        int rx = x + 12;
        frame(g, rx - 1, y - 1, SLOT + 2, SLOT + 2, C_SLOT, C_GOLD);
        if (!result.isEmpty()) {
            g.renderItem(result, rx, y);
            g.renderItemDecorations(font, result, rx, y);
            hots.add(new Hot(rx, y, 16, 16, result, null, null));
        }
    }

    private void slot(GuiGraphics g, int x, int y, Ingredient ingredient) {
        frame(g, x, y, SLOT, SLOT, C_SLOT, C_SLOT_BORDER);
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) return;
        ItemStack shown = options[(int) ((System.currentTimeMillis() / 1000L) % options.length)];
        g.renderItem(shown, x + 1, y + 1);
        if (shown.getCount() > 1) g.renderItemDecorations(font, shown, x + 1, y + 1);
        RecipeIndex.Entry target = index.byItem.get(shown.getItem());
        hots.add(new Hot(x + 1, y + 1, 16, 16, shown, target,
                target == null ? null : () -> select(target, true)));
    }

    // =====================================================================
    //  Interactions
    // =====================================================================

    private void select(RecipeIndex.Entry e, boolean pushHistory) {
        if (e == selected) return;
        if (pushHistory && selected != null) history.push(selected);
        if (!pushHistory) history.clear();
        selected = e;
        recipeIdx = 0;
    }

    private void goBack() {
        if (!history.isEmpty()) {
            selected = history.pop();
            recipeIdx = 0;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (category != CraftCategory.ANIMAUX && button == 0 && mx >= barX && mx < barX + 4 && my >= gridY && my < gridY + rows * SLOT) {
            draggingScrollbar = true;
            scrollTo(my);
            return true;
        }
        if (button == 0) {
            for (Hot h : hots) {
                if (h.action != null && h.in(mx, my)) {
                    h.action.run();
                    return true;
                }
            }
        }
        if (button == 1 && search.isMouseOver(mx, my)) { // clic droit : vider la recherche
            search.setValue("");
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            scrollTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    private void scrollTo(double my) {
        int max = maxScroll();
        if (max <= 0) return;
        double f = (my - gridY) / (double) (rows * SLOT);
        scrollRow = Mth.clamp((int) Math.round(f * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (category == CraftCategory.ANIMAUX) {
            animalScroll = Mth.clamp(animalScroll - (int) Math.signum(scrollY), 0, maxAnimalScroll());
            return true;
        }
        if (mx >= gridX && mx < barX + 4 && my >= gridY && my < gridY + rows * SLOT) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        if (selected != null && selected.recipes.size() > 1
                && mx >= detailX && mx < detailX + detailW && my >= detailY && my < detailY + detailH) {
            int n = selected.recipes.size();
            recipeIdx = Math.floorMod(recipeIdx - (int) Math.signum(scrollY), n);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    private void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private ItemStack safeResult(Recipe<?> recipe) {
        try {
            ItemStack s = recipe.getResultItem(minecraft.level.registryAccess());
            return s == null ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static String stationName(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING) return "Table de craft";
        if (type == RecipeType.SMELTING) return "Four";
        if (type == RecipeType.BLASTING) return "Haut fourneau";
        if (type == RecipeType.SMOKING) return "Fumoir";
        if (type == RecipeType.CAMPFIRE_COOKING) return "Feu de camp";
        if (type == RecipeType.STONECUTTING) return "Tailleur de pierre";
        if (type == RecipeType.SMITHING) return "Table de forgeron";
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return "Recette spéciale";
        String path = key.getPath().replace('_', ' ').replace('/', ' ');
        String pretty = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return pretty + " (" + RecipeIndex.modName(key.getNamespace()) + ")";
    }

    /** Les recettes de forgeron ne remplissent pas getIngredients() : on lit leurs champs (noms Mojang, NeoForge). */
    private static List<Ingredient> smithingIngredients(Recipe<?> recipe) {
        List<Ingredient> out = new ArrayList<>();
        for (String f : new String[]{"template", "base", "addition"}) {
            try {
                Field field = recipe.getClass().getDeclaredField(f);
                field.setAccessible(true);
                Object v = field.get(recipe);
                if (v instanceof Ingredient i) out.add(i);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }
}
