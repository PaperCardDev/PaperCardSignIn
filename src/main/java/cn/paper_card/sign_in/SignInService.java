package cn.paper_card.sign_in;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

interface SignInService {

    void addSignIn(@NotNull SignInInfo info) throws Exception;

    @Nullable SignInInfo queryOneTimeAfter(@NotNull UUID playerId, long time) throws Exception;

    @NotNull List<SignInInfo> queryAllTimeBetween(long begin, long end) throws Exception;
}
