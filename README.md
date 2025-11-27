# SAML (Entra ID) + Password Login Sandbox

Next.js フロントエンドと Spring Boot バックエンドで、Entra ID の SAML SSO と通常のユーザー名/パスワード認証を併用するサンプルです。`docker compose` でローカル起動できます。

## 構成
- frontend (Next.js 14) : `/frontend`
- backend (Spring Boot 3 / Spring Security SAML2) : `/backend`
- docker compose : フロント `3000`, バックエンド `8080`

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
   https://localhost:8080/saml2/service-provider-metadata/entra
   ```
   - または、`docker-compose.yml` の `SAML_ENTITY_ID` 環境変数で変更した場合はそれに合わせる
   - この値は、Service Provider（このアプリケーション）を一意に識別するIDです
   - Microsoft Entra ID に対してアプリケーションを識別する一意の ID。この値は、Microsoft Entra ID テナント内のすべてのアプリケーションで一意である必要があります。既定の識別子は、IDP で開始された SSO の SAML 応答の対象ユーザーになります。

2. **応答 URL (Assertion Consumer Service URL)** フィールド（必須）に以下を入力：
   ```
   https://localhost:8080/login/saml2/sso/entra
   ```
   - これは Spring Security SAML2 のデフォルトエンドポイントです
   - Entra IDが認証成功後にSAMLレスポンスを送信する先のURLです
   - 応答 URL は、アプリケーションが認証トークンを受け取る場所です。これは、SAML では Assertion Consumer Service (ACS) とも呼ばれます。

3. **ログアウト URL (省略可能)** フィールド（任意）に以下を入力：
   ```
   https://localhost:8080/logout
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

**重要**: SPメタデータを取得するには、まずSAMLを有効にする必要があります。

1. **SAMLを有効にしてバックエンドを起動**:
   - 環境変数 `SAML_ENABLED=true` を設定して起動します
   - まだEntra IDのメタデータURLが取得できていない場合は、一時的にプレースホルダーを使用できます:
     ```bash
     SAML_ENABLED=true SAML_IDP_METADATA_URI=classpath:saml/idp-placeholder.xml docker compose up
     ```
   - または、`.env` ファイルを作成して設定:
     - **配置場所**: プロジェクトルート（`docker-compose.yml`と同じディレクトリ）に `.env` ファイルを作成
     - 内容例:
     ```
     SAML_ENABLED=true
     SAML_IDP_METADATA_URI=classpath:saml/idp-placeholder.xml
     ```
     - Docker Composeは、`docker-compose.yml`と同じディレクトリにある`.env`ファイルを自動的に読み込みます
   - 既にEntra IDのメタデータURLを取得している場合は、それを使用:
     ```bash
     SAML_ENABLED=true SAML_IDP_METADATA_URI=https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id> docker compose up
     ```

2. ブラウザで以下のURLにアクセス:
   ```
   https://localhost:8080/saml2/service-provider-metadata/entra
   ```
   - 正常に動作している場合、XMLメタデータが表示されます
   - 401エラーが表示される場合は、`SAML_ENABLED=true` が設定されているか確認してください

3. 表示されたXMLメタデータをコピー

4. Entra ID の **シングル サインオン** → **SAML** 画面で **フェデレーション メタデータ XML のアップロード** を選択

5. コピーしたXMLをアップロード

**注意**: この手順は双方向の信頼関係を確立するために重要です。SPメタデータをアップロードすることで、Entra IDがアプリケーションの公開鍵やエンドポイントを認識できるようになります。

## ローカル実行（Docker）
```bash
docker compose build
docker compose up
```
環境変数（必要に応じて `docker-compose.yml` を上書き）:
- `SAML_ENABLED` : SAML認証を有効にする場合は `true` を設定（デフォルトは `false`）
  - SPメタデータエンドポイントにアクセスするには、この値を `true` にする必要があります
- `SAML_IDP_METADATA_URI` : Entra ID メタデータ URL または `file:` パス
- `SAML_ENTITY_ID` : SP Entity ID（デフォルトは `https://localhost:8080/saml2/service-provider-metadata/entra`）
- `APP_FRONTEND_BASE_URL` : SAML ログイン成功時のリダイレクト先（デフォルト `https://localhost:3000`）
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
  - **重要**: `sp-signing.key`（秘密鍵）は絶対にコミットしないでください。`.gitignore`で除外されています。
  - `sp-signing.crt`（証明書）のみがリポジトリに含まれます。これは公開情報のため問題ありません。

### 接続元IP・認証情報
- ログイン可能な接続元（CORS & セッション想定）: `https://localhost:3000` および `https://127.0.0.1:3000`
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

## トラブルシューティング

### SPメタデータエンドポイントで401エラーが発生する場合

**症状**: `https://localhost:8080/saml2/service-provider-metadata/entra` にアクセスすると401エラーが表示される

**原因と対処法**:

1. **SAMLが有効になっていない**
   - `.env`ファイルまたは環境変数で `SAML_ENABLED=true` が設定されているか確認
   - バックエンドのログで `RelyingPartyRegistrationRepository` が作成されているか確認
   - ログに `Creating bean 'relyingPartyRegistrationRepository'` が表示されていればOK

2. **メタデータURIが設定されていない**
   - `.env`ファイルまたは環境変数で `SAML_IDP_METADATA_URI` が設定されているか確認
   - プレースホルダーを使用する場合: `SAML_IDP_METADATA_URI=classpath:saml/idp-placeholder.xml`
   - 実際のEntra IDメタデータを使用する場合: `SAML_IDP_METADATA_URI=https://login.microsoftonline.com/...`
   - **重要**: `SAML_IDP_METADATA_URI` が空の場合、`RelyingPartyRegistrationRepository` が作成されず、メタデータエンドポイントが利用できません

3. **ファイルパスの確認**
   - `classpath:saml/idp-placeholder.xml` を使用する場合、ファイルが `backend/src/main/resources/saml/idp-placeholder.xml` に存在するか確認
   - **注意**: Spring Bootアプリケーションでは、リソースファイルはJARファイル内にパッケージされます
   - Dockerコンテナ内でJARファイル内のリソースを確認する場合:
     ```bash
     docker compose exec backend jar -tf /app/app.jar | grep idp-placeholder.xml
     ```
   - または、JARファイル内のsamlディレクトリの内容を確認:
     ```bash
     docker compose exec backend jar -tf /app/app.jar | grep "saml/"
     ```

4. **バックエンドのログを確認**
   - バックエンドの起動ログで以下のエラーが出ていないか確認:
     - `Failed to load SAML metadata`
     - `Cannot create RelyingPartyRegistration`
   - ログレベルを `DEBUG` に設定すると詳細な情報が確認できます:
     ```yaml
     logging:
       level:
         org.springframework.security.saml2: DEBUG
     ```

5. **環境変数の確認方法**
   - Dockerコンテナ内で環境変数を確認:
     ```bash
     docker compose exec backend env | grep SAML
     ```
   - または、バックエンドのログで環境変数が読み込まれているか確認

**推奨される`.env`ファイルの設定例**:
```
SAML_ENABLED=true
SAML_IDP_METADATA_URI=classpath:saml/idp-placeholder.xml
SAML_ENTITY_ID=http://localhost:8080/saml2/service-provider-metadata/entra
APP_FRONTEND_BASE_URL=http://localhost:3000
```

**重要**: `.env`ファイルを変更した後は、**必ずバックエンドを再起動**してください:
```bash
docker compose restart backend
```

または、完全に再起動する場合:
```bash
docker compose down
docker compose up -d
```

**`RelyingPartyRegistrationRepository`が作成されているか確認する方法**:
- バックエンドの起動ログで以下のメッセージを確認:
  - `Creating bean 'relyingPartyRegistrationRepository'` が表示されていればOK
- または、以下のコマンドで確認:
  ```bash
  docker compose logs backend | grep -i "relying\|Creating bean"
  ```
- `RelyingPartyRegistrationRepository`が作成されていない場合、メタデータエンドポイントは存在せず、401エラーが返されます
