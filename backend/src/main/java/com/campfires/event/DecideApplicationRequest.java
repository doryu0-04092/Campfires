package com.campfires.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 【役割】申込みの承認・却下で送られてくるデータ（docs/api/openapi.yaml の DecideApplicationRequest）。
 * 【なぜ必要か】approved（承認）か rejected（却下）のどちらかだけを受け付けるため。
 *
 * @param status approved か rejected
 */
public record DecideApplicationRequest(
        @NotNull(message = "approved か rejected を指定してください。")
        @Pattern(regexp = "approved|rejected", message = "approved か rejected を指定してください。")
        String status) {
}
