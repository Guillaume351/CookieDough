package com.cookiebuild.cookiedough.theme.hunt;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.cookiebuild.cookiedough.persistence.SqlTransactionExecutor;

/** JDBC access to {@code seasonal_collectibles} (website migration 0027). */
public final class PostgresCollectibleRepository implements CollectibleRepository {
    @Override
    public Set<String> found(UUID playerId, String huntId) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    select collectible_id from seasonal_collectibles
                     where player_id = ? and theme_id = ?
                    """)) {
                statement.setObject(1, playerId);
                statement.setString(2, huntId);
                Set<String> found = new HashSet<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) found.add(rows.getString(1));
                }
                return found;
            }
        });
    }

    @Override
    public boolean record(UUID playerId, String huntId, String collectibleId) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into seasonal_collectibles (player_id, theme_id, collectible_id, found_at)
                    values (?, ?, ?, now())
                    on conflict (player_id, theme_id, collectible_id) do nothing
                    """)) {
                statement.setObject(1, playerId);
                statement.setString(2, huntId);
                statement.setString(3, collectibleId);
                return statement.executeUpdate() == 1;
            }
        });
    }
}
