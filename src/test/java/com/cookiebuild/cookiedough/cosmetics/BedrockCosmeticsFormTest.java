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
        assertEquals(10, json.getAsJsonArray("buttons").size());
        assertTrue(json.get("title").getAsString().contains("Étoile ★"));
        for (int i = 0; i < form.buttons().size(); i++) {
            definitions.definitionFor(form).handleFormResponse(form, Integer.toString(i));
        }
        var javaActions = CosmeticMenuView.entries(inventory).stream()
                .map(entry -> CosmeticMenuAction.parse(entry.action()).orElseThrow()).toList();
        assertEquals(javaActions, received.subList(0, 8));
        assertEquals(CosmeticMenuAction.Kind.PREVIEW_EMOTE, received.get(8).kind());
        assertEquals(CosmeticMenuAction.Kind.PREVIEW_VICTORY, received.get(9).kind());
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
