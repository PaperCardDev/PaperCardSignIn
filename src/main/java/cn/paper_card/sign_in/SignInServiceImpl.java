package cn.paper_card.sign_in;

import cn.paper_card.database.api.DatabaseApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class SignInServiceImpl implements SignInService {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Connection connection;
    private SignInTable table = null;

    SignInServiceImpl(DatabaseApi.@NotNull MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    private @NotNull SignInTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new SignInTable(newCon);
        this.connection = newCon;

        return this.table;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final SignInTable t = this.table;
            this.connection = null;
            this.table = null;
            if (t != null) t.close();
        }
    }

    @Override
    public void addSignIn(@NotNull SignInInfo info) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final SignInTable t = this.getTable();

                final int inserted = t.insert(info.playerId(), info.time());
                this.mySqlConnection.setLastUseTime();

                if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable SignInInfo queryOneTimeAfter(@NotNull UUID playerId, long time) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final SignInTable t = this.getTable();

                final SignInInfo signInInfo = t.queryPlayerTimeAfter(playerId, time);
                this.mySqlConnection.setLastUseTime();

                return signInInfo;

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<SignInInfo> queryAllTimeBetween(long begin, long end) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final SignInTable t = this.getTable();

                final List<SignInInfo> list = t.queryTimeBetween(begin, end);
                this.mySqlConnection.setLastUseTime();

                return list;

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }
}
