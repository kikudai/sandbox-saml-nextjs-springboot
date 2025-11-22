# SAML (Entra ID) + Password Login Sandbox

Next.js フロントエンドと Spring Boot バックエンドで、Entra ID の SAML SSO と通常のユーザー名/パスワード認証を併用するサンプルです。`docker-compose` でローカル起動できます。

## 構成
- frontend (Next.js 14) : `/frontend`
- backend (Spring Boot 3 / Spring Security SAML2) : `/backend`
- docker-compose : フロント `3000`, バックエンド `8080`

### バージョン情報
- フロントエンド: Next.js 14.1.0 / React 18.2.0 / TypeScript 5.3.3（`frontend/package.json` に準拠）
- バックエンド: Java 17 / Spring Boot 3.2.5 / Spring Security 6.2.4 / Spring Security SAML2 / Maven / OpenSAML (Shibboleth repo)

## 事前準備（Entra ID 側設定）

### 重要：エンタープライズアプリケーション vs アプリの登録

**SAML認証には「エンタープライズアプリケーション（Enterprise Application）」を使用してください。**

- ✅ **エンタープライズアプリケーション**: SAML認証に対応。本プロジェクトで使用する方法です。
- ❌ **アプリの登録（App Registration）**: OAuth2/OIDC用。SAML認証には使用できません。

### 詳細手順

#### 1. エンタープライズアプリケーションの作成

1. Azure Portal にログインし、**Microsoft Entra ID**（旧 Azure AD）に移動
2. 左メニューから **エンタープライズ アプリケーション** を選択
3. **新しいアプリケーション** をクリック
4. **独自のアプリケーションを作成する** をクリック
5. アプリケーション名を入力（例: "SAML Sandbox App"）
6. **ギャラリーに見つからないその他のアプリケーションを統合します (ギャラリー以外)** を選択し作成ボタンをクリック

#### 2. Basic SAML Configuration の設定

作成したアプリケーションを開き、左メニューから **シングル サインオン** を選択し、**SAML** を選択します。

**Basic SAML Configuration** セクションで以下を設定：

1. **識別子 (エンティティ ID)** フィールド（必須）に以下を入力：
   ```
   http://localhost:8080/saml2/service-provider-metadata/entra
   ```
   - または、`docker-compose.yml` の `SAML_ENTITY_ID` 環境変数で変更した場合はそれに合わせる
   - この値は、Service Provider（このアプリケーション）を一意に識別するIDです

2. **応答 URL (Assertion Consumer Service URL)** フィールド（必須）に以下を入力：
   ```
   http://localhost:8080/login/saml2/sso/entra
   ```
   - これは Spring Security SAML2 のデフォルトエンドポイントです
   - Entra IDが認証成功後にSAMLレスポンスを送信する先のURLです

3. **ログアウト URL (省略可能)** フィールド（任意）に以下を入力：
   ```
   http://localhost:8080/logout
   ```
   - シングルログアウト（SLO）を使用する場合に設定します

**注意**: 画面右上の **編集** ボタンをクリックして編集モードにしてから、各フィールドに入力してください。

**保存** をクリックして設定を保存します。

#### 3. メタデータURLの取得

1. **シングル サインオン** 画面の **SAML 署名証明書** セクションを確認
2. **アプリのフェデレーション メタデータ URL** をコピー
   - 形式例: `https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id>`
3. このURLを `docker-compose.yml` の環境変数 `SAML_IDP_METADATA_URI` に設定
   ```yaml
   SAML_IDP_METADATA_URI: https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id>
   ```
   - または、メタデータXMLをダウンロードしてローカルファイルとして使用する場合:
     ```yaml
     SAML_IDP_METADATA_URI: file:/app/saml/idp-metadata.xml
     ```

#### 4. ユーザーとグループの割り当て

1. 左メニューから **ユーザーとグループ** を選択
2. **ユーザー/グループの追加** をクリック
3. テスト用のユーザーまたはグループを選択して割り当て
4. **割り当て** をクリック

#### 5. 属性（クレーム）の設定（オプション）

1. **シングル サインオン** → **SAML** 画面で **ユーザー属性とクレーム** をクリック
2. **一意のユーザー識別子 (名前 ID)** をクリックして編集画面を開く

   **Name ID の設定**:
   - **名前識別子の形式**: 
     - **電子メールアドレス** を選択する場合 → **ソース属性** を `user.mail` に設定
     - **ユーザー プリンシパル名** を選択する場合 → **ソース属性** を `user.userprincipalname` に設定
     - **永続的な識別子** を選択する場合 → アプリケーション固有の永続的なIDが使用されます
   - **ソース**: 「属性」を選択（通常はデフォルト）
   - **ソース属性**: 上記の形式に合わせて `user.mail` または `user.userprincipalname` を選択
   - **名前** と **名前空間** は通常変更不要（デフォルト値で問題ありません）
     - 名前: `nameidentifier`
     - 名前空間: `http://schemas.xmlsoap.org/ws/2005/05/identity/claims`
   - **保存** をクリック

   **推奨設定**:
   - 名前識別子の形式: **電子メールアドレス**
   - ソース属性: `user.mail`
   - これにより、ユーザーのメールアドレスがName IDとして使用されます

3. **その他のクレームの追加/編集**（必要に応じて）:
   - **email**: `user.mail`
   - **givenname**: `user.givenname`
   - **surname**: `user.surname`
   - **name**: `user.userprincipalname` または `user.displayname`
   - その他必要な属性

#### 6. Service Provider (SP) メタデータの登録

1. バックエンドを起動（`docker-compose up` など）
2. ブラウザで以下のURLにアクセス:
   ```
   http://localhost:8080/saml2/service-provider-metadata/entra
   ```
3. 表示されたXMLメタデータをコピー
4. Entra ID の **シングル サインオン** → **SAML** 画面で **フェデレーション メタデータ XML のアップロード** を選択
5. コピーしたXMLをアップロード

**注意**: この手順は双方向の信頼関係を確立するために重要です。SPメタデータをアップロードすることで、Entra IDがアプリケーションの公開鍵やエンドポイントを認識できるようになります。

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
