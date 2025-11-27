"use client";

import useSWR from "swr";
import { useMemo, useState } from "react";

type UserInfo = {
  name: string;
  authorities: string[];
  attributes?: Record<string, string | string[]>;
};

const fetcher = (url: string) => fetch(url, { credentials: "include" }).then((r) => (r.ok ? r.json() : null));

export default function Home() {
  // Proxy経由で同一オリジンに揃える（NEXT_PUBLIC_API_BASE_URL を指定すれば直叩きにも切替可）
  const apiBase = useMemo(() => process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/backend", []);
  const { data, mutate, isLoading } = useSWR<UserInfo | null>(`${apiBase}/api/me`, fetcher, {
    revalidateOnFocus: false,
    revalidateOnReconnect: false,
    refreshInterval: 0,
    shouldRetryOnError: false
  });
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const onPasswordLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    const res = await fetch(`${apiBase}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password })
    });
    if (!res.ok) {
      const msg = await res.text();
      setError(msg || "ログインに失敗しました");
      return;
    }
    setUsername("");
    setPassword("");
    mutate();
  };

  const onLogout = async () => {
    await fetch(`${apiBase}/api/auth/logout`, { method: "POST", credentials: "include" });
    mutate(null, false);
  };

  return (
    <div className="panel">
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <div className="tag">Next.js + Spring Boot</div>
          <h1 style={{ margin: "8px 0 0", letterSpacing: "0.4px" }}>Entra ID SAML SSO / Password Login</h1>
          <p className="muted" style={{ marginTop: 6 }}>
            SAML SSO (Entra ID) と通常ログインの両方を検証するためのサンドボックス
          </p>
        </div>
        {data ? (
          <button className="btn" style={{ width: "auto" }} onClick={onLogout}>
            ログアウト
          </button>
        ) : null}
      </header>

      <div className="grid">
        <section className="card">
          <h3 style={{ marginTop: 0 }}>SAML SSO (Entra ID)</h3>
          <p className="muted">バックエンドの SAML エンドポイントへリダイレクトします。</p>
          <a className="btn" href={`${apiBase}/saml2/authenticate/entra`}>
            Entra IDでログイン
          </a>
          <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
            SP Initiated Flow: /saml2/authenticate/entra へ遷移 → Entra ID → Assertion 受信後 /login/saml2/sso/entra に戻る想定。
          </p>
        </section>

        <section className="card">
          <h3 style={{ marginTop: 0 }}>パスワードログイン</h3>
          <form onSubmit={onPasswordLogin} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <label className="label">
              ユーザー名
              <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} required />
            </label>
            <label className="label">
              パスワード
              <input
                className="input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>
            <button className="btn" type="submit">
              ログイン
            </button>
            {error ? <div className="error">{error}</div> : null}
          </form>
          <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
            サンプルでは Spring Security のインメモリユーザーを利用します。Entra ID と組み合わせても、同じセッションで /api/me
            を確認できます。
          </p>
        </section>
      </div>

      <section className="card" style={{ marginTop: 20 }}>
        <h3 style={{ marginTop: 0 }}>ログイン状態</h3>
        {isLoading ? (
          <p className="muted">読み込み中...</p>
        ) : data ? (
          <div>
            <div className="tag">サインイン済み</div>
            <p style={{ marginTop: 10 }}>名前: {data.name}</p>
            <p className="muted" style={{ marginTop: 4 }}>
              権限: {data.authorities.join(", ")}
            </p>
            {data.attributes ? (
              <details style={{ marginTop: 10 }}>
                <summary className="muted">SAML 属性</summary>
                <pre
                  style={{
                    background: "#0b1223",
                    padding: 12,
                    borderRadius: 10,
                    border: "1px solid rgba(255,255,255,0.05)",
                    overflowX: "auto"
                  }}
                >
                  {JSON.stringify(data.attributes, null, 2)}
                </pre>
              </details>
            ) : null}
          </div>
        ) : (
          <div className="muted">未ログイン</div>
        )}
      </section>
    </div>
  );
}
