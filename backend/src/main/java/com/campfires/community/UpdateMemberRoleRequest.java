package com.campfires.community;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 【役割】運営スタッフの任命・解除で送られてくるデータ（docs/api/openapi.yaml の UpdateMemberRoleRequest）。
 * 【なぜ必要か】staff（運営スタッフにする）か member（メンバーに戻す）のどちらかだけを受け付けるため。
 * owner は指定できない（オーナーを替える機能は MVP にない）。
 *
 * @param role staff か member
 */
public record UpdateMemberRoleRequest(
        @NotNull(message = "staff か member を指定してください。")
        @Pattern(regexp = "staff|member", message = "staff か member を指定してください。")
        String role) {
}
