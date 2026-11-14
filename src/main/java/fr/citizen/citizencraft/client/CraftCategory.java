package fr.citizen.citizencraft.client;

/**
 * Categories du Livre des Recettes (ordre d'affichage).
 */
public enum CraftCategory {
    ALL("Tout"),
    PECHE("Pêche"),
    ARTEFACTS("Artefacts"),
    BOUCHERIE("Chasse & Boucherie"),
    ANIMAUX("Animaux"),
    FORGE("Forge & Armures"),
    ARTISANAT("Artisanat"),
    NOURRITURE("Nourriture & Bière"),
    COURRIER("Courrier"),
    AFFICHES("Affiches"),
    MENUISERIE("Menuiserie"),
    ECONOMIE("Économie"),
    MINECRAFT("Minecraft"),
    AUTRES("Autres mods");

    public final String label;

    CraftCategory(String label) {
        this.label = label;
    }
}
