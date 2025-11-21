# SAML (Entra ID) + Password Login Sandbox

Next.js フロントエンドと Spring Boot バックエンドで、Entra ID の SAML SSO と通常のユーザー名/パスワード認証を併用するサンプルです。`docker-compose` でローカル起動できます。

## 構成
- frontend (Next.js 14) : `/frontend`
- backend (Spring Boot 3 / Spring Security SAML2) : `/backend`
- docker-compose : フロント `3000`, バックエンド `8080`

### バージョン情報
- フロントエンド: Next.js 14.1 / React 18 / TypeScript 5（package.json に準拠）
- バックエンド: Java 17 / Spring Boot 3.2.5 / Spring Security SAML2 / Maven

## 事前準備（Entra ID 側設定）
1. Entra ID で Enterprise Application を作成し、**SAML** を選択
2. Basic SAML Configuration
   - Identifier (Entity ID) : `http://localhost:8080/saml2/service-provider-metadata/entra`（または `SAML_ENTITY_ID` に合わせる）
   - Reply URL (ACS) : `http://localhost:8080/login/saml2/sso/entra`
   - Logout URL (任意) : `http://localhost:8080/logout`
3. 証明書書き出し/メタデータ:
   - Federation Metadata XML の URL を控える（例: `https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id>`）
   - `docker-compose.yml` の環境変数 `SAML_IDP_METADATA_URI` に設定（ローカルファイルを使う場合は `file:/app/saml/idp-metadata.xml` など）
4. SP メタデータの登録:
   - バックエンド起動後 `http://localhost:8080/saml2/service-provider-metadata/entra` を Entra ID にアップロード
5. ユーザー/グループをアプリに割り当て、必要に応じて NameID/属性 (email, givenname など) を発行

## ローカル実行（Docker）
```bash
docker-compose build
docker-compose up
```
環境変数（必要に応じて `docker-compose.yml` を上書き）:
- `SAML_IDP_METADATA_URI` : Entra ID メタデータ URL または `file:` パス
- `SAML_ENTITY_ID` : SP Entity ID（デフォルトは `http://localhost:8080/saml2/service-provider-metadata/entra`）
- `APP_FRONTEND_BASE_URL` : SAML ログイン成功時のリダイレクト先（デフォルト `http://localhost:3000`）
- フロント: `NEXT_PUBLIC_API_BASE_URL`（バックエンド URL）

## ローカル実行（開発用メモ）
- フロント: `cd frontend && npm install && npm run dev`
- バックエンド: `cd backend && mvn spring-boot:run`

## バックエンド概要
- エンドポイント
  - `POST /api/auth/login` : JSON ログイン (`{ "username": "user", "password": "password" }`)
  - `POST /api/auth/logout`
  - `GET /api/me` : ログイン情報・SAML 属性を返却
  - `GET /saml2/authenticate/entra` : SP Initiated SAML フロー開始
  - `GET /saml2/service-provider-metadata/entra` : SP メタデータ
- 開発用インメモリユーザー
  - `user/password` (ROLE_USER)
  - `admin/adminpass` (ROLE_ADMIN, ROLE_USER)
- SAML 署名鍵は `backend/src/main/resources/saml/sp-signing.*` にサンプルを同梱。実運用では置き換えてください。

### 接続元IP・認証情報
- ログイン可能な接続元（CORS & セッション想定）: `http://localhost:3000` および `http://127.0.0.1:3000`
- パスワード認証ユーザー:
  - ユーザー: `user` / パスワード: `password`
  - 管理者: `admin` / パスワード: `adminpass`

## フロントエンド概要
- `/` に以下を表示
  - Entra ID ログインボタン（バックエンド `/saml2/authenticate/entra` へリダイレクト）
  - パスワードログインフォーム（バックエンド `/api/auth/login` を叩き、セッションを共有）
  - `/api/me` の結果表示（SAML 属性も含めて確認可能）

## よくある確認ポイント
- `SAML_IDP_METADATA_URI` が正しいか（tenant/appid が一致しているか）
- Entra ID に `/saml2/service-provider-metadata/entra` をアップロード済みか
- フロントとバックエンドで同一ホスト名を使う（`localhost`/`127.0.0.1` を混在させない）ことでセッションが共有される
