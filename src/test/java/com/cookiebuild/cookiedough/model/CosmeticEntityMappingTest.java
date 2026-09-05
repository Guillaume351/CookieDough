package com.cookiebuild.cookiedough.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

class CosmeticEntityMappingTest {
    @Test
    void entitlementUsesTheRequiredCompositeKeyAndColumns() {
        assertEquals("cosmetic_entitlements", CosmeticEntitlement.class.getAnnotation(Table.class).name());
        assertEquals(CosmeticEntitlementId.class,
                CosmeticEntitlement.class.getAnnotation(IdClass.class).value());
        assertEquals(Set.of("player_id", "cosmetic_id", "source"),
                idColumns(CosmeticEntitlement.class));
        assertEquals(Set.of("player_id", "cosmetic_id", "source", "granted_at", "revoked_at", "expires_at"),
                columns(CosmeticEntitlement.class));
        assertNotNull(field(CosmeticEntitlement.class, "revokedAt").getAnnotation(Column.class));
        assertEquals(128, field(CosmeticEntitlement.class, "source").getAnnotation(Column.class).length());
        assertNotNull(field(CosmeticEntitlement.class, "expiresAt").getAnnotation(Column.class));
    }

    @Test
    void selectionUsesTheRequiredCompositeKeyAndColumns() {
        assertEquals("cosmetic_selections", CosmeticSelection.class.getAnnotation(Table.class).name());
        assertEquals(CosmeticSelectionId.class,
                CosmeticSelection.class.getAnnotation(IdClass.class).value());
        assertEquals(Set.of("player_id", "slot"), idColumns(CosmeticSelection.class));
        assertEquals(Set.of("player_id", "slot", "cosmetic_id", "selected_at"),
                columns(CosmeticSelection.class));
    }

    private static Set<String> idColumns(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .map(field -> field.getAnnotation(Column.class).name())
                .collect(Collectors.toSet());
    }

    private static Set<String> columns(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .map(field -> field.getAnnotation(Column.class).name())
                .collect(Collectors.toSet());
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException error) {
            throw new AssertionError(error);
        }
    }
}
