import { NextRequest, NextResponse } from "next/server";

const backendBase = process.env.BACKEND_BASE_URL ?? "http://localhost:8080";

async function handle(req: NextRequest, context: { params: { path?: string[] } }) {
  const path = context.params.path?.join("/") ?? "";
  const target = new URL(path, backendBase);
  // keep original query string
  target.search = req.nextUrl.search;

  // clone headers and drop hop-by-hop
  const headers = new Headers(req.headers);
  headers.delete("host");
  headers.delete("connection");
  headers.delete("content-length");
  headers.delete("accept-encoding");

  const res = await fetch(target.toString(), {
    method: req.method,
    headers,
    body: req.body,
    redirect: "manual"
  });

  const responseHeaders = new Headers(res.headers);
  // NextResponse will handle set-cookie headers; no special casing needed
  return new NextResponse(res.body, {
    status: res.status,
    statusText: res.statusText,
    headers: responseHeaders
  });
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE, handle as OPTIONS, handle as HEAD };
