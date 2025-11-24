# SAML 認証シーケンス (Entra ID, SP Initiated)

Mermaid で現在の Spring Security + Next.js 構成のシーケンスをまとめました。

```mermaid
sequenceDiagram
    participant U as ユーザー (ブラウザ)
    participant FE as Next.js フロント
    participant BE as Spring Boot (SP)
    participant IDP as Entra ID (IdP)

    Note over BE,IDP: 初期設定（初回のみ）
    IDP->>BE: GET /saml2/service-provider-metadata/entra<br/>（SPメタデータ取得）
    BE-->>IDP: SPメタデータ（証明書含む）<br/>sp-signing.crt の公開鍵
    Note over IDP: SPの証明書を登録・保存

    Note over U,IDP: 認証フロー
    U->>FE: "/" にアクセス
    FE->>BE: GET /saml2/authenticate/entra<br/>（ログインボタン押下）
    Note over BE: AuthnRequest 生成<br/>sp-signing.key で署名
    BE-->>U: 302 リダイレクト<br/>Entra ID SSO へ（署名付きAuthnRequest）
    U->>IDP: 署名付きAuthnRequest 付きリダイレクト
    Note over IDP: SP証明書で署名検証<br/>（登録済みの証明書を使用）
    IDP->>U: ログインUI表示・認証
    IDP-->>U: POST SAMLResponse（Assertion）<br/>宛先: /login/saml2/sso/entra<br/>（IdPの署名付き）
    U->>BE: POST /login/saml2/sso/entra<br/>（ブラウザからのフォーム POST）
    Note over BE: IdP証明書で署名検証<br/>（IdPメタデータから取得）
    BE->>BE: 署名検証・属性抽出<br/>Saml2Authentication を生成
    BE-->>U: 302 -> frontendBaseUrl<br/>例: http://localhost:3000
    U->>BE: GET /api/me（セッションクッキー）
    BE-->>U: ログイン済み情報 + SAML 属性
```

## フローの要点

### 初期設定（初回のみ）
- Entra ID が SP メタデータエンドポイント `/saml2/service-provider-metadata/entra` にアクセスして SP メタデータを取得します。
- SP メタデータには、SP の署名証明書（`sp-signing.crt` の公開鍵）が含まれています。
- Entra ID はこの証明書を登録・保存し、後続の署名検証に使用します。

### 認証フロー
- フロントエンド `/` の「Entra IDでログイン」リンクは SP Initiated で `/saml2/authenticate/entra` を叩き、Spring Security が AuthnRequest を生成します。
- **SP側の署名**: AuthnRequest は `sp-signing.key`（秘密鍵）を使用して署名されます（オプションですが、セキュリティのベストプラクティスとして推奨）。
- 署名付き AuthnRequest が Entra ID にリダイレクトされます。
- **IdP側の署名検証**: Entra ID は登録済みの SP 証明書（`sp-signing.crt` の公開鍵）を使用して AuthnRequest の署名を検証します。
- Entra ID で認証後、SAMLResponse は ACS エンドポイント `/login/saml2/sso/entra` に POST され、IdP の署名が付与されます。
- **SP側の署名検証**: `SecurityFilterChain` の SAML フィルタで、IdP メタデータから取得した IdP の証明書を使用して SAMLResponse の署名を検証し、ログイン処理を行います（`Saml2AuthenticatedPrincipal`）。
- 成功時は `SecurityConfig` の `defaultSuccessUrl(frontendBaseUrl, true)` に従い、`APP_FRONTEND_BASE_URL`（既定 `http://localhost:3000`）へリダイレクト。セッションは `HttpSessionSecurityContextRepository` で保持されます。
- フロントは同一オリジン/ポートのクッキーを付けて `/api/me` を呼び、SAML 属性や権限を確認できます。

### 署名鍵について
- **SP側の署名鍵**: `sp-signing.key`（秘密鍵）と `sp-signing.crt`（証明書）は `backend/src/main/resources/saml/` に配置されます。
- 秘密鍵（`sp-signing.key`）は絶対にコミットしないでください（`.gitignore` で除外済み）。
- 証明書（`sp-signing.crt`）は SP メタデータに含まれ、Entra ID に公開されます。
- 署名鍵を変更した場合は、新しい SP メタデータを Entra ID にアップロードする必要があります。
