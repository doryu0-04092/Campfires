package com.campfires.common.web;

/**
 * 【役割】入力のチェックで引っかかった項目1つ分（docs/api/openapi.yaml の FieldError）。
 * 【なぜ必要か】エラーの返事の errors に、「どの項目が、なぜだめだったか」を入れるため。
 *
 * record（レコード）は、値をまとめて持つだけのクラスを、短く書くための Java の書き方。
 * field と message を受け取る constructor や、値を取り出すメソッド（field()、message()）が自動で作られる。
 *
 * @param field   引っかかった項目の名前（例："title"）
 * @param message 利用者に見せる、日本語の説明
 */
public record FieldErrorItem(String field, String message) {
}
