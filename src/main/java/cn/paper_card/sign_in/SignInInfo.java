package cn.paper_card.sign_in;

import java.util.UUID;

record SignInInfo(
        UUID playerId,
        long time
) {
}
