package com.campfires.common.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 【役割】画面のファイル（React をビルドしたもの）を返す設定。
 * 【なぜ必要か】本番では、画面と API を1つの箱にまとめ、Spring Boot が画面のファイルも返す（Issue #47）。
 * 画面の URL（例：/communities/1）は、React が画面の中で切り替えているだけで、そういう名前のファイルはない。
 * そのため、ファイルが見つからないときは、画面の入り口（index.html）を返し、React に画面を選んでもらう。
 *
 * 画面のファイルは、Docker イメージを作るときに src/main/resources/static/ に入れる（Dockerfile を参照）。
 * 開発中は static/ に画面のファイルがないので、何も返さない（画面は npm run dev が返す）。
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    /**
     * 【何のため】/ から始まるすべての URL で、画面のファイルを探す決まりを登録する。
     * API（/api/〜）は、Controller が先に受け付けるので、ここには来ない。来たとき（ない API）は、何も返さない（404）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /assets/ の中のファイル（ビルドで作られる JavaScript と CSS）は、ブラウザに1年間覚えさせる（毎回取りに来させない）。
        // ファイルの名前に、中身から作った印（例：index-Ds-20vn2.js の Ds-20vn2）が入っていて、中身が変わると名前も変わるので、
        // 古いファイルを使い続けることはない。immutable：「この URL の中身は変わらない」という印（確かめの通信も省ける）。
        // 見つからないときは 404（画面の入り口は返さない）
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(true);
        // それ以外（index.html など）は、覚えさせない（新しい版を公開したら、すぐに新しい画面を使ってもらうため）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * 【何のため】URL に合うファイルを探す。見つからなければ、画面の入り口（index.html）を返す。
     * PathResourceResolver は、Spring が用意している「URL からファイルを探す」部品。それを土台にして、決まりを足す。
     */
    public static class SpaResourceResolver extends PathResourceResolver {

        /**
         * 【何のため】URL に合うファイルを探す（自動テストから、パストラバーサルの対策を確かめるために使う）。
         */
        public Resource find(String resourcePath, Resource location) throws IOException {
            return getResource(resourcePath, location);
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            // ない API の URL に、画面を返さない（API のエラーは、JSON の 404 で返したいため）
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            // ファイルがあれば、それを返す（例：/assets/index-xxxx.js）。
            // super.getResource は、Spring の確認（ファイルが static/ の中にあるか）を通してから返す。
            // 自分で location.createRelative を使うと、この確認を通らず、../ で static/ の外のファイル（設定ファイルなど）を
            // 読まれるおそれがあるため、必ず super を通す（パストラバーサルの対策。セキュリティの見直し）
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            // 拡張子の付いたファイル（例：/logo.png）が見つからないときは、画面を返さずに 404 にする
            if (resourcePath.contains(".")) {
                return null;
            }
            // それ以外（画面の URL）は、画面の入り口を返す。React が URL を見て、画面を選ぶ
            return super.getResource("index.html", location);
        }
    }
}
