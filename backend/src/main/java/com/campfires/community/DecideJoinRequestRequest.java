package com.campfires.community;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 【役割】参加申請の承認・却下で送られてくるデータ（docs/api/openapi.yaml の DecideJoinRequestRequest）。
 * 【なぜ必要か】approved（承認）か rejected（却下）のどちらかだけを受け付けるため。
 *
 * @param status approved か rejected
 */
public record DecideJoinRequestRequest(
        @NotNull(message = "approved か rejected を指定してください。")
        // | は「または」。approved か rejected と、ちょうど一致するときだけ通す
        @Pattern(regexp = "approved|rejected", message = "approved か rejected を指定してください。")
        String status) {
}
