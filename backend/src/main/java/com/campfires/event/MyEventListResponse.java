package com.campfires.event;

import java.util.List;

/**
 * 【役割】自分が申し込んだイベントの一覧の返事（docs/api/openapi.yaml の MyEventListResponse）。
 * 【なぜ必要か】申し込んだイベントは数が限られるので、ページに分けずに全件を items に入れて返す（要件定義書 5.8）。
 *
 * @param items 申し込んだ開催前のイベント（開始日時の近い順）
 */
public record MyEventListResponse(List<MyEvent> items) {
}
