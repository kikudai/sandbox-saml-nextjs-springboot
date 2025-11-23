# SAML 認証シーケンス (Entra ID, SP Initiated)

Mermaid で現在の Spring Security + Next.js 構成のシーケンスをまとめました。

```mermaid
sequenceDiagram
    participant U as ユーザー (ブラウザ)
    participant FE as Next.js フロント
    participant BE as Spring Boot (SP)
    participant IDP as Entra ID (IdP)

    U->>FE: "/" にアクセス
    FE->>BE: GET /saml2/authenticate/entra<br/>（ログインボタン押下）
    BE-->>U: 302 リダイレクト<br/>Entra ID SSO へ（AuthnRequest）
    U->>IDP: AuthnRequest 付きリダイレクト
    IDP->>U: ログインUI表示・認証
    IDP-->>U: POST SAMLResponse（Assertion）<br/>宛先: /login/saml2/sso/entra
    U->>BE: POST /login/saml2/sso/entra<br/>（ブラウザからのフォーム POST）
    BE->>BE: 署名検証・属性抽出<br/>Saml2Authentication を生成
    BE-->>U: 302 -> frontendBaseUrl<br/>例: http://localhost:3000
    U->>BE: GET /api/me（セッションクッキー）
    BE-->>U: ログイン済み情報 + SAML 属性
```

## フローの要点
- フロントエンド `/` の「Entra IDでログイン」リンクは SP Initiated で `/saml2/authenticate/entra` を叩き、Spring Security が AuthnRequest を発行して Entra ID にリダイレクトします。
- Entra ID で認証後、SAMLResponse は ACS エンドポイント `/login/saml2/sso/entra` に POST され、`SecurityFilterChain` の SAML フィルタで検証・ログインします（`Saml2AuthenticatedPrincipal`）。
- 成功時は `SecurityConfig` の `defaultSuccessUrl(frontendBaseUrl, true)` に従い、`APP_FRONTEND_BASE_URL`（既定 `http://localhost:3000`）へリダイレクト。セッションは `HttpSessionSecurityContextRepository` で保持されます。
- フロントは同一オリジン/ポートのクッキーを付けて `/api/me` を呼び、SAML 属性や権限を確認できます。
