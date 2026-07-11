"use strict";
const http = require("http");
const fs = require("fs");
const path = require("path");

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const DATA_FILE = path.join(DATA_DIR, "state.json");
const PUBLIC_DIR = path.join(__dirname, "public");
const PORT = process.env.PORT || 8080;

function ensureData() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, JSON.stringify({ kids: [] }, null, 2));
}
ensureData();

let writeChain = Promise.resolve();
function readState() {
  return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
}
function writeState(state) {
  writeChain = writeChain.then(() => fs.writeFileSync(DATA_FILE, JSON.stringify(state, null, 2)));
  return writeChain;
}
function uid() {
  return Math.random().toString(36).slice(2, 10);
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript",
  ".css": "text/css",
  ".apk": "application/vnd.android.package-archive",
  ".json": "application/json",
};

function sendJSON(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(data);
}

function readJsonBody(req, maxBytes, cb) {
  let chunks = [];
  let size = 0;
  let aborted = false;
  req.on("data", (d) => {
    size += d.length;
    if (size > maxBytes) {
      aborted = true;
      req.destroy();
    } else {
      chunks.push(d);
    }
  });
  req.on("end", () => {
    if (aborted) return;
    const raw = Buffer.concat(chunks).toString("utf8");
    let json = null;
    try {
      json = raw ? JSON.parse(raw) : {};
    } catch (e) {
      json = null;
    }
    cb(json);
  });
  req.on("error", () => {});
}

const server = http.createServer((req, res) => {
  let url;
  try {
    url = new URL(req.url, "http://internal");
  } catch (e) {
    res.writeHead(400);
    return res.end("Bad request");
  }
  const p = url.pathname;

  if (p === "/api/state" && req.method === "GET") {
    return sendJSON(res, 200, readState());
  }

  if (p === "/api/kids" && req.method === "POST") {
    return readJsonBody(req, 8 * 1024 * 1024, (body) => {
      if (!body || !body.name || !String(body.name).trim()) {
        return sendJSON(res, 400, { error: "name required" });
      }
      const state = readState();
      const kid = {
        id: uid(),
        name: String(body.name).trim().slice(0, 40),
        color: typeof body.color === "string" ? body.color : "--avatar-1",
        photo: typeof body.photo === "string" ? body.photo : null,
        log: [],
      };
      state.kids.push(kid);
      writeState(state).then(() => sendJSON(res, 200, state));
    });
  }

  const kidMatch = p.match(/^\/api\/kids\/([a-z0-9]+)$/);
  if (kidMatch && req.method === "DELETE") {
    const state = readState();
    state.kids = state.kids.filter((k) => k.id !== kidMatch[1]);
    return writeState(state).then(() => sendJSON(res, 200, state));
  }
  if (kidMatch && req.method === "PATCH") {
    return readJsonBody(req, 8 * 1024 * 1024, (body) => {
      const state = readState();
      const kid = state.kids.find((k) => k.id === kidMatch[1]);
      if (!kid) return sendJSON(res, 404, { error: "not found" });
      if (!body || !("photo" in body)) {
        return sendJSON(res, 400, { error: "photo required" });
      }
      kid.photo = typeof body.photo === "string" ? body.photo : null;
      writeState(state).then(() => sendJSON(res, 200, state));
    });
  }

  const pointMatch = p.match(/^\/api\/kids\/([a-z0-9]+)\/points$/);
  if (pointMatch && req.method === "POST") {
    return readJsonBody(req, 4 * 1024, (body) => {
      const state = readState();
      const kid = state.kids.find((k) => k.id === pointMatch[1]);
      if (!kid) return sendJSON(res, 404, { error: "not found" });
      if (!body || !body.catId || typeof body.delta !== "number") {
        return sendJSON(res, 400, { error: "catId and delta required" });
      }
      const catId = String(body.catId).slice(0, 40);
      const delta = body.delta > 0 ? 1 : -1;
      const current = kid.log.reduce((sum, e) => (e.catId === catId ? sum + e.delta : sum), 0);
      if (delta < 0 && current <= 0) {
        return sendJSON(res, 200, state);
      }
      kid.log.push({ catId, delta, ts: Date.now() });
      if (kid.log.length > 500) kid.log = kid.log.slice(-500);
      writeState(state).then(() => sendJSON(res, 200, state));
    });
  }

  if (p === "/api/team/points" && req.method === "POST") {
    return readJsonBody(req, 4 * 1024, (body) => {
      if (!body || !body.catId || typeof body.delta !== "number") {
        return sendJSON(res, 400, { error: "catId and delta required" });
      }
      const catId = String(body.catId).slice(0, 40);
      const delta = body.delta > 0 ? 1 : -1;
      const state = readState();
      const ts = Date.now();
      state.kids.forEach((kid) => {
        const current = kid.log.reduce((sum, e) => (e.catId === catId ? sum + e.delta : sum), 0);
        if (delta < 0 && current <= 0) return;
        kid.log.push({ catId, delta, ts });
        if (kid.log.length > 500) kid.log = kid.log.slice(-500);
      });
      writeState(state).then(() => sendJSON(res, 200, state));
    });
  }

  if (p.startsWith("/api/")) {
    return sendJSON(res, 404, { error: "not found" });
  }

  // static files
  let filePath = p === "/" ? "/index.html" : p;
  filePath = path.normalize(filePath).replace(/^(\.\.[/\\])+/, "");
  const full = path.join(PUBLIC_DIR, filePath);
  if (!full.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    return res.end("Forbidden");
  }
  fs.readFile(full, (err, data) => {
    if (err) {
      res.writeHead(404);
      return res.end("Not found");
    }
    const ext = path.extname(full);
    res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream" });
    res.end(data);
  });
});

server.listen(PORT, () => console.log("Dojoy listening on " + PORT));
