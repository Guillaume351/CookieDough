package com.cookiebuild.cookiedough.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PostgresAdminCommandRepositoryTest {
    @Test
    void mapsDatabaseSuccessToTheStableWireContract() {
        assertEquals("succeeded", PostgresAdminCommandRepository.toDatabaseStatus("completed"));
        assertEquals("completed", PostgresAdminCommandRepository.toWireStatus("succeeded"));
        assertEquals("failed", PostgresAdminCommandRepository.toDatabaseStatus("failed"));
        assertEquals("expired", PostgresAdminCommandRepository.toWireStatus("expired"));
    }
}
