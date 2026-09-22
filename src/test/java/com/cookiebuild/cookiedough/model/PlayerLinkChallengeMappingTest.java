package com.cookiebuild.cookiedough.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;

class PlayerLinkChallengeMappingTest {
    @Test
    void challengePurposeIsRequiredAndBoundedForTheSharedDbContract() throws Exception {
        Field field = PlayerLinkChallenge.class.getDeclaredField("purpose");
        Column column = field.getAnnotation(Column.class);

        assertFalse(column.nullable());
        assertEquals(24, column.length());
    }
}
