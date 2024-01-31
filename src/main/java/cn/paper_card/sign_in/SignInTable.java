package cn.paper_card.sign_in;

import cn.paper_card.database.api.Parser;
import cn.paper_card.database.api.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class SignInTable extends Parser<SignInInfo> {

    private static final String NAME = "player_sign_in";

    private PreparedStatement statementInsert = null;

    private PreparedStatement statementQueryTimeBetween = null;

    private PreparedStatement statementQueryPlayerTimeAfter = null;

    private PreparedStatement statementQueryNoToday = null;

    private final @NotNull Connection connection;

    SignInTable(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s
                (
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    time BIGINT NOT NULL
                );""".formatted(NAME));
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement("""
                    INSERT INTO %s (uid1, uid2, time)
                    VALUES (?, ?, ?);""".formatted(NAME));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementQueryTimeBetween() throws SQLException {
        if (this.statementQueryTimeBetween == null) {
            this.statementQueryTimeBetween = this.connection.prepareStatement("""
                    SELECT uid1, uid2, time
                    FROM %s
                    WHERE time >= ?
                      AND time < ?;""".formatted(NAME));
        }
        return this.statementQueryTimeBetween;
    }

    private @NotNull PreparedStatement getStatementQueryPlayerTimeAfter() throws SQLException {
        if (this.statementQueryPlayerTimeAfter == null) {
            this.statementQueryPlayerTimeAfter = this.connection.prepareStatement("""
                    SELECT uid1, uid2, time
                    FROM %s
                    WHERE (uid1, uid2) = (?, ?)
                      AND time > ?
                    LIMIT 1;""".formatted(NAME));
        }
        return this.statementQueryPlayerTimeAfter;
    }

    private @NotNull PreparedStatement getStatementQueryNoToday() throws SQLException {
        if (this.statementQueryNoToday == null) {
            this.statementQueryNoToday = this.connection.prepareStatement("""
                    SELECT no
                    FROM (SELECT row_number() over (ORDER BY time) as no, uid1, uid2
                          FROM %s
                          WHERE time >= ?) p
                    WHERE (p.uid1, p.uid2) = (?, ?)
                    LIMIT 1;""".formatted(NAME));
        }
        return this.statementQueryNoToday;
    }

    int insert(@NotNull UUID playerId, long time) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setLong(1, playerId.getMostSignificantBits());
        ps.setLong(2, playerId.getLeastSignificantBits());
        ps.setLong(3, time);
        return ps.executeUpdate();
    }

    @Override
    public @NotNull SignInInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
        final long id1 = resultSet.getLong(1);
        final long id2 = resultSet.getLong(2);
        final long time = resultSet.getLong(3);
        return new SignInInfo(new UUID(id1, id2), time);
    }

    @NotNull List<SignInInfo> queryTimeBetween(long begin, long end) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryTimeBetween();
        ps.setLong(1, begin);
        ps.setLong(2, end);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseAll(resultSet);
    }

    @Nullable SignInInfo queryPlayerTimeAfter(@NotNull UUID uuid, long time) throws SQLException {

        final PreparedStatement ps = this.getStatementQueryPlayerTimeAfter();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, time);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }

    @Nullable Integer queryNo(@NotNull UUID uuid, long todayBegin) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryNoToday();
        ps.setLong(1, todayBegin);
        ps.setLong(2, uuid.getMostSignificantBits());
        ps.setLong(3, uuid.getLeastSignificantBits());
        final ResultSet resultSet = ps.executeQuery();

        return new Parser<Integer>() {
            @Override
            public @NotNull Integer parseRow(@NotNull ResultSet resultSet) throws SQLException {
                return resultSet.getInt(1);
            }
        }.parseOne(resultSet);
    }
}
