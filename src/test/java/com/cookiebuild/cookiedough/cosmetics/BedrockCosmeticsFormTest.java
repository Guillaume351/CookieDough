package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

class BedrockCosmeticsFormTest {
    @Test
    void serializedNativeButtonsDispatchTheSameActionsAsJavaIncludingPreviews() throws Exception {
        UUID player = UUID.randomUUID();
        var repository = new InMemoryCosmeticRepository();
        var service = new CosmeticService(repository);
        for (String id : List.of(CosmeticCatalog.COOKIE_CHEER, CosmeticCatalog.GOLDEN_COOKIE_BURST)) {
            repository.grant(player, id, "purchase:test", new Date(), null);
            service.select(player, CosmeticCatalog.find(id).orElseThrow().slot(), id);
        }
        var inventory = service.inventory(player);
        List<CosmeticMenuAction> received = new ArrayList<>();
        var form = BedrockCosmeticsForm.create(inventory, key -> "Étoile ★ " + key, received::add);
        var definitions = FormDefinitions.instance();
        var json = JsonParser.parseString(definitions.codecFor(form).jsonData(form)).getAsJsonObject();
        assertEquals("form", json.get("type").getAsString());
        int cosmetics = CosmeticMenuView.entries(inventory).size();
        assertEquals(cosmetics + 2, json.getAsJsonArray("buttons").size());
        assertTrue(json.get("title").getAsString().contains("Étoile ★"));
        for (int i = 0; i < form.buttons().size(); i++) {
            definitions.definitionFor(form).handleFormResponse(form, Integer.toString(i));
        }
        var javaActions = CosmeticMenuView.entries(inventory).stream()
                .map(entry -> CosmeticMenuAction.parse(entry.action()).orElseThrow()).toList();
        assertEquals(javaActions, received.subList(0, cosmetics));
        assertEquals(CosmeticMenuAction.Kind.PREVIEW_EMOTE, received.get(cosmetics).kind());
        assertEquals(CosmeticMenuAction.Kind.PREVIEW_VICTORY, received.get(cosmetics + 1).kind());
    }

    @Test
    void inSeasonLimitedItemsShowTheirLabelAndDispatchABuyAction() throws Exception {
        var service = new CosmeticService(new InMemoryCosmeticRepository());
        service.setActiveSeasons(() -> java.util.Set.of("halloween"));
        var inventory = service.inventory(UUID.randomUUID());
        List<CosmeticMenuAction> received = new ArrayList<>();
        var form = BedrockCosmeticsForm.create(inventory, 10_000, key -> key, received::add);
        var definitions = FormDefinitions.instance();
        var json = JsonParser.parseString(definitions.codecFor(form).jsonData(form)).getAsJsonObject();
        var buttons = json.getAsJsonArray("buttons");
        int pumpkin = -1;
        for (int i = 0; i < buttons.size(); i++) {
            String text = buttons.get(i).getAsJsonObject().get("text").getAsString();
            if (text.contains("cosmetics.item.pumpkin_trail.name")) pumpkin = i;
            assertEquals(text.contains("cosmetics.item.pumpkin_trail.name")
                            || text.contains("cosmetics.item.witch_spell_victory.name"),
                    text.contains("cosmetics.season.halloween.label"), text);
        }
        assertTrue(pumpkin >= 0);
        definitions.definitionFor(form).handleFormResponse(form, Integer.toString(pumpkin));
        assertEquals(List.of(new CosmeticMenuAction(CosmeticMenuAction.Kind.BUY, CosmeticSlot.HUB_TRAIL,
                CosmeticCatalog.PUMPKIN_TRAIL)), received);
    }

    @Test
    void closedAndInvalidNativeResponsesNeverDispatchAnAction() throws Exception {
        var inventory = new CosmeticService(new InMemoryCosmeticRepository()).inventory(UUID.randomUUID());
        List<CosmeticMenuAction> received = new ArrayList<>();
        var form = BedrockCosmeticsForm.create(inventory, key -> key, received::add);
        var definition = FormDefinitions.instance().definitionFor(form);
        for (String response : List.of("null", "-1", "100", "\"not a button\"")) {
            definition.handleFormResponse(form, response);
        }
        assertTrue(received.isEmpty());
    }
}
