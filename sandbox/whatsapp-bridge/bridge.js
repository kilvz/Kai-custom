import { makeWASocket, useMultiFileAuthState, fetchLatestBaileysVersion, DisconnectReason, Browsers } from '@whiskeysockets/baileys';
import express from 'express';
import pino from 'pino';
import QRCode from 'qrcode';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import net from 'node:net';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_DIR = path.join(__dirname, 'auth_info');
const CONFIG_FILE = path.join(__dirname, 'config.json');
const QR_FILE = '/tmp/whatsapp-qr.png';
const QR_BASE64_FILE = '/tmp/whatsapp-qr.txt';
const UNREAD_FILE = '/tmp/whatsapp-unread.json';
const PORT = parseInt(process.env.WA_BRIDGE_PORT || '8317', 10);

const logger = pino({ level: 'error' });

// ── Minimal in-memory store (replacement for removed makeInMemoryStore) ──
const _chats = new Map();
const _msgs = new Map();
const store = {
  bind(ev) {
    ev.on('chats.upsert', (chats) => {
      console.log('[STORE-DEBUG] chats.upsert count=' + chats.length);
      for (const c of chats) _chats.set(c.id, c);
    });
    ev.on('chats.update', (updates) => {
      console.log('[STORE-DEBUG] chats.update count=' + updates.length);
      for (const u of updates) {
        const existing = _chats.get(u.id);
        if (existing) Object.assign(existing, u);
      }
    });
    ev.on('chats.delete', (ids) => {
      console.log('[STORE-DEBUG] chats.delete count=' + ids.length);
      for (const id of ids) _chats.delete(id);
    });
    ev.on('messages.upsert', ({ messages }) => {
      for (const m of messages) {
        const jid = m.key.remoteJid;
        if (!jid) continue;
        if (!_msgs.has(jid)) _msgs.set(jid, []);
        _msgs.get(jid).push(m);
      }
    });
    ev.on('messages.update', (updates) => {
      for (const u of updates) {
        const jid = u.key.remoteJid;
        if (!jid || !_msgs.has(jid)) continue;
        const arr = _msgs.get(jid);
        const idx = arr.findIndex(m => m.key.id === u.key.id);
        if (idx >= 0) Object.assign(arr[idx], u);
      }
    });
    ev.on('messages.delete', (data) => {
      if (data.keys) {
        for (const key of data.keys) {
          const jid = key.remoteJid;
          if (!jid || !_msgs.has(jid)) continue;
          _msgs.set(jid, _msgs.get(jid).filter(m => m.key.id !== key.id));
        }
      }
    });
    ev.on('messaging-history.set', ({ chats, messages }) => {
      console.log('[STORE-DEBUG] messaging-history.set chats=' + (chats ? chats.length : 0) + ' messages=' + (messages ? messages.length : 0));
      if (chats) { for (const c of chats) _chats.set(c.id, c); }
      if (messages) {
        for (const m of messages) {
          const jid = m.key.remoteJid;
          if (!jid) continue;
          if (!_msgs.has(jid)) _msgs.set(jid, []);
          _msgs.get(jid).push(m);
        }
      }
    });
  },
  chats: {
    all() { return Array.from(_chats.values()); },
  },
  messages: {
    get(jid) {
      const arr = _msgs.get(jid) || [];
      return { all: () => arr };
    },
  },
};

setInterval(() => {
  console.log('[STORE-STATUS] chats=' + _chats.size + ' messages=' + _msgs.size);
}, 15000);

let sock = null;
let currentQr = null;
let connected = false;
let pairingMode = false;
let consecutiveFailures = 0;
const recentlySent = new Set();

// Hardcoded fallback — fetchLatestBaileysVersion() often returns versions
// that WhatsApp servers have already deprecated, causing 405 rejections.
const FALLBACK_VERSION = [2, 3000, 1035194821];

async function resolveVersion() {
  try {
    const { version } = await Promise.race([
      fetchLatestBaileysVersion(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 8000)),
    ]);
    if (version && Array.isArray(version) && version.length >= 3) return version;
  } catch (e) {
    console.log('[BRIDGE] fetchLatestBaileysVersion failed (' + e.message + '), using fallback');
  }
  return FALLBACK_VERSION;
}

function loadConfig() {
  try {
    if (fs.existsSync(CONFIG_FILE)) {
      return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    }
  } catch (e) {}
  return {};
}

function saveUnread(messages) {
  try { fs.writeFileSync(UNREAD_FILE, JSON.stringify(messages, null, 2)); } catch (e) {}
}

function loadUnread() {
  try {
    if (fs.existsSync(UNREAD_FILE)) return JSON.parse(fs.readFileSync(UNREAD_FILE, 'utf-8'));
  } catch (e) {}
  return [];
}

function clearUnread() {
  try { if (fs.existsSync(UNREAD_FILE)) fs.unlinkSync(UNREAD_FILE); } catch (e) {}
}

async function initBaileys() {
  const version = await resolveVersion();
  console.log('[BRIDGE] Using WA version: ' + JSON.stringify(version));
  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
  const cfg = loadConfig();
  const browser = cfg.browser || Browsers.windows('Chrome');
  const markOnline = cfg.markOnlineOnConnect !== undefined ? cfg.markOnlineOnConnect : true;
  const syncHistory = cfg.syncFullHistory !== undefined ? cfg.syncFullHistory : true;
  const linkPreviews = cfg.generateHighQualityLinkPreview !== undefined ? cfg.generateHighQualityLinkPreview : true;
  const shouldSync = cfg.shouldSyncHistoryMsg !== undefined ? cfg.shouldSyncHistoryMsg : true;

  sock = makeWASocket({
    version,
    logger,
    printQRInTerminal: false,
    auth: state,
    browser: browser,
    markOnlineOnConnect: markOnline,
    generateHighQualityLinkPreview: linkPreviews,
    syncFullHistory: syncHistory,
    shouldSyncHistoryMsg: () => shouldSync,
    connectTimeoutMs: 120_000,
    keepAliveIntervalMs: 30_000,
    qrTimeout: 120_000,
  });

  store.bind(sock.ev);
  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;
    if (qr) {
      currentQr = qr;
      connected = false;
      try {
        const png = await QRCode.toBuffer(qr);
        fs.writeFileSync(QR_FILE, png);
        fs.writeFileSync(QR_BASE64_FILE, png.toString('base64'));
      } catch (e) {}
      console.log('[BRIDGE] QR received');
    }
    if (connection === 'open') {
      connected = true;
      pairingMode = false;
      consecutiveFailures = 0;
      currentQr = null;
      try { fs.unlinkSync(QR_FILE); } catch (e) {}
      try { fs.unlinkSync(QR_BASE64_FILE); } catch (e) {}
      console.log('[BRIDGE] Connection open');
    }
    if (connection === 'close') {
      connected = false;
      consecutiveFailures++;
      const statusCode = lastDisconnect?.error?.output?.statusCode;
      const statusMsg = lastDisconnect?.error?.output?.payload?.error || (lastDisconnect?.error?.message) || '';
      const reason = lastDisconnect?.error?.reason || '';
      console.log('[BRIDGE] Connection closed. statusCode=' + statusCode + ' reason=' + reason + ' msg=' + statusMsg + ' failures=' + consecutiveFailures);
      if (statusCode === DisconnectReason.loggedOut || statusCode === 401) {
        console.log('[BRIDGE] Logged out / auth rejected by server. Clearing auth and restarting...');
        try { fs.rmSync(AUTH_DIR, { recursive: true, force: true }); } catch (e) {}
        consecutiveFailures = 0;
        setTimeout(() => initBaileys(), 3000);
      } else if (statusCode === 405 || statusCode === 403 || statusCode === 411 || statusCode === 440) {
        console.log('[BRIDGE] Version/handshake rejected (HTTP ' + statusCode + '). Retrying in 5s...');
        setTimeout(() => initBaileys(), 5000);
      } else {
        // Exponential backoff: 5s, 10s, 20s, 30s (capped at 30s)
        const delay = Math.min(5000 * Math.pow(2, Math.min(consecutiveFailures - 1, 3)), 30000);
        console.log('[BRIDGE] Reconnecting in ' + (delay / 1000) + 's...');
        setTimeout(() => initBaileys(), delay);
      }
    }
  });

  sock.ev.on('messages.upsert', async (m) => {
    const msgs = m.messages.filter(msg => msg.message && !recentlySent.has(msg.key.id));
    if (msgs.length > 0) {
      const unread = loadUnread();
      for (const msg of msgs) {
        const remoteJid = msg.key.remoteJid;
        const text = msg.message.conversation || msg.message.extendedTextMessage?.text || msg.message.imageMessage?.caption || '';
        if (text) {
          unread.push({
            chatId: remoteJid,
            messageId: msg.key.id,
            text,
            fromName: msg.pushName || remoteJid.split('@')[0],
            fromMe: msg.key.fromMe || false,
            sender: msg.key.participant || remoteJid,
            timestamp: msg.messageTimestamp,
          });
        }
      }
      saveUnread(unread);
    }
  });

  return sock;
}

function formatJid(input) {
  if (input.includes('@')) return input;
  if (input.includes('-')) return input + '@g.us';
  return input + '@s.whatsapp.net';
}

// ── Direct group fetch (bypasses store/history sync) ──
async function fetchAllGroups(sock) {
  try {
    const groups = await sock.groupFetchAllParticipating();
    return Object.entries(groups || {}).map(([id, g]) => ({
      id, jid: id,
      name: g.subject || '(Unnamed Group)',
      type: 'group',
      participants: g.participants?.length || 0,
      unreadCount: g.unreadCount || 0,
    }));
  } catch (e) {
    console.log('[BRIDGE] groupFetchAllParticipating failed: ' + e.message);
    return [];
  }
}

function mergeChats(storeChats, groupChats) {
  const map = new Map();
  for (const c of storeChats) map.set(c.id, c);
  for (const c of groupChats) {
    const existing = map.get(c.id);
    if (existing) {
      existing.name = c.name || existing.name;
      existing.participants = c.participants || existing.participants;
      existing.type = c.type || existing.type;
    } else {
      map.set(c.id, c);
    }
  }
  return Array.from(map.values());
}

const app = express();
app.use(express.json());

// Direct HTTP endpoint — always works, no history sync needed
app.post('/list-chats-direct', async (req, res) => {
  try {
    const storeChats = _chats.size > 0 ? Array.from(_chats.values()) : [];
    const groupChats = await fetchAllGroups(sock);
    const merged = mergeChats(storeChats, groupChats);
    res.json({ success: true, storeSize: _chats.size, groupCount: groupChats.length, total: merged.length, chats: merged });
  } catch (e) {
    res.json({ success: false, error: e.message });
  }
});

// MCP SSE endpoint
app.get('/mcp', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });

  // Send server info
  res.write(`data: ${JSON.stringify({ jsonrpc: '2.0', method: 'server/info', params: { name: 'whatsapp-bridge', version: '1.0.0' } })}\n\n`);

  const keepAlive = setInterval(() => {
    res.write(': keepalive\n\n');
  }, 15000);

  req.on('close', () => {
    clearInterval(keepAlive);
  });
});

app.post('/mcp', async (req, res) => {
  const body = req.body;
  const { id, method, params } = body;

  if (method === 'initialize') {
    return res.json({
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: { name: 'whatsapp-bridge', version: '1.0.0' },
      },
    });
  }

  if (method === 'notifications/initialized') {
    return res.status(200).end();
  }

  if (method === 'tools/list') {
    return res.json({
      jsonrpc: '2.0',
      id,
      result: {
        tools: [
          {
            name: 'send_message',
            description: 'Send a WhatsApp text message to a phone number or chat ID',
            inputSchema: {
              type: 'object',
              properties: {
                phone: { type: 'string', description: 'Phone number with country code (e.g. 628123456789) or chat JID' },
                text: { type: 'string', description: 'Message text' },
              },
              required: ['phone', 'text'],
            },
          },
          {
            name: 'send_image',
            description: 'Send an image from the sandbox filesystem to a WhatsApp chat',
            inputSchema: {
              type: 'object',
              properties: {
                phone: { type: 'string', description: 'Phone number or chat JID' },
                image_path: { type: 'string', description: 'Absolute path to the image file in sandbox' },
                caption: { type: 'string', description: 'Optional image caption' },
              },
              required: ['phone', 'image_path'],
            },
          },
          {
            name: 'list_chats',
            description: 'List recent WhatsApp chats',
            inputSchema: { type: 'object', properties: { limit: { type: 'number', description: 'Max chats to return (default 20)' } } },
          },
          {
            name: 'get_messages',
            description: 'Get message history for a chat',
            inputSchema: {
              type: 'object',
              properties: {
                chat_id: { type: 'string', description: 'Chat JID (e.g. 628123456789@s.whatsapp.net)' },
                limit: { type: 'number', description: 'Max messages to return (default 30)' },
              },
              required: ['chat_id'],
            },
          },
          {
            name: 'request_pairing_code',
            description: 'Request a pairing code for phone-number-based WhatsApp authentication (alternative to QR code)',
            inputSchema: {
              type: 'object',
              properties: {
                phone: { type: 'string', description: 'Phone number with country code, no + or spaces (e.g. 628123456789)' },
              },
              required: ['phone'],
            },
          },
          {
            name: 'get_qr_code',
            description: 'Get the current WhatsApp QR code as base64 PNG for first-time authentication',
            inputSchema: { type: 'object', properties: {} },
          },
          {
            name: 'is_authenticated',
            description: 'Check if WhatsApp is authenticated and connected',
            inputSchema: { type: 'object', properties: {} },
          },
          {
            name: 'get_unread_messages',
            description: 'Get unread incoming WhatsApp messages since last poll',
            inputSchema: { type: 'object', properties: {} },
          },
          {
            name: 'clear_unread_messages',
            description: 'Clear the unread messages queue (call after processing)',
            inputSchema: {
              type: 'object',
              properties: {
                markRead: { type: 'boolean', description: 'Whether to mark messages as read (send read receipts)' },
              },
            },
          },
        ],
      },
    });
  }

  if (method === 'tools/call') {
    const { name, arguments: args } = params;

    if (!sock) {
      return res.json({ jsonrpc: '2.0', id, error: { code: -32000, message: 'WhatsApp not initialized' } });
    }

    try {
      let content;

      switch (name) {
        case 'send_message': {
          const jid = formatJid(args.phone);
          const sent = await sock.sendMessage(jid, { text: args.text });
          if (sent?.key?.id) {
            recentlySent.add(sent.key.id);
            setTimeout(() => recentlySent.delete(sent.key.id), 10000);
          }
          content = [{ type: 'text', text: JSON.stringify({ success: true, to: jid }) }];
          break;
        }

        case 'send_image': {
          const jid = formatJid(args.phone);
          if (!fs.existsSync(args.image_path)) {
            return res.json({ jsonrpc: '2.0', id, error: { code: -32000, message: 'Image file not found: ' + args.image_path } });
          }
          const imgData = fs.readFileSync(args.image_path);
          await sock.sendMessage(jid, { image: imgData, caption: args.caption || '' });
          content = [{ type: 'text', text: JSON.stringify({ success: true, to: jid, file: args.image_path }) }];
          break;
        }

        case 'list_chats': {
          const limit = args?.limit || 20;
          const chats = store?.chats?.all() || [];
          const sorted = chats.sort((a, b) => (b.conversationTimestamp || 0) - (a.conversationTimestamp || 0)).slice(0, limit).map(c => ({
            id: c.id,
            name: c.name || c.id.split('@')[0],
            unreadCount: c.unreadCount || 0,
            lastMessageTimestamp: c.conversationTimestamp,
          }));
          content = [{ type: 'text', text: JSON.stringify(sorted) }];
          break;
        }

        case 'get_messages': {
          const msgs = store?.messages?.get(args.chat_id)?.all() || [];
          const sorted = msgs.sort((a, b) => (b.messageTimestamp || 0) - (a.messageTimestamp || 0)).slice(0, args?.limit || 30).map(m => ({
            fromMe: m.key.fromMe,
            text: m.message?.conversation || m.message?.extendedTextMessage?.text || m.message?.imageMessage?.caption || '[non-text message]',
            timestamp: m.messageTimestamp,
            id: m.key.id,
          }));
          content = [{ type: 'text', text: JSON.stringify(sorted) }];
          break;
        }

        case 'request_pairing_code': {
          if (connected) {
            return res.json({ jsonrpc: '2.0', id, error: { code: -32000, message: 'Already authenticated. Use restart first.' } });
          }
          const phone = args.phone || '';
          if (!phone) {
            return res.json({ jsonrpc: '2.0', id, error: { code: -32000, message: 'Phone number required' } });
          }
          pairingMode = true;
          currentQr = null;
          try { fs.unlinkSync(QR_FILE); } catch (e) {}
          try { fs.unlinkSync(QR_BASE64_FILE); } catch (e) {}
          // WebSocket state check not needed — sock.ws doesn't exist in Baileys v7 RC13
          const code = await sock.requestPairingCode(phone);
          const formatted = code.match(/.{1,4}/g)?.join('-') || code;
          content = [{ type: 'text', text: JSON.stringify({ code, formatted }) }];
          break;
        }

        case 'get_qr_code': {
          if (connected) {
            content = [{ type: 'text', text: JSON.stringify({ authenticated: true, qr: null }) }];
          } else {
            try {
              if (fs.existsSync(QR_BASE64_FILE)) {
                const qr = fs.readFileSync(QR_BASE64_FILE, 'utf-8').trim();
                content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr, pairingMode }) }];
              } else if (currentQr) {
                content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr: currentQr, pairingMode }) }];
              } else {
                content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr: '', pairingMode }) }];
              }
            } catch (e) {
              content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr: '', pairingMode }) }];
            }
          }
          break;
        }

        case 'is_authenticated': {
          content = [{ type: 'text', text: JSON.stringify({ connected, hasAuth: fs.existsSync(path.join(AUTH_DIR, 'creds.json')) }) }];
          break;
        }

        case 'get_unread_messages': {
          content = [{ type: 'text', text: JSON.stringify(loadUnread()) }];
          break;
        }

        case 'clear_unread_messages': {
          const markRead = args && args.markRead === true;
          if (markRead && sock) {
            const unread = loadUnread();
            const keys = unread
              .filter(u => !u.fromMe)
              .map(u => ({ remoteJid: u.chatId, id: u.messageId, fromMe: false }));
            if (keys.length > 0) {
              sock.readMessages(keys).catch(() => {});
            }
          }
          clearUnread();
          content = [{ type: 'text', text: JSON.stringify({ success: true }) }];
          break;
        }

        default:
          return res.json({ jsonrpc: '2.0', id, error: { code: -32601, message: 'Unknown tool: ' + name } });
      }

      res.json({ jsonrpc: '2.0', id, result: { content } });
    } catch (e) {
      res.json({ jsonrpc: '2.0', id, error: { code: -32000, message: e.message || String(e) } });
    }
    return;
  }

  res.json({ jsonrpc: '2.0', id, error: { code: -32601, message: 'Method not found: ' + method } });
});

async function main() {
  // Duplicate detection: check if port is already in use
  try {
    const testConn = await new Promise((resolve, reject) => {
      const test = net.createConnection(PORT, '127.0.0.1', () => {
        test.end();
        resolve(true);
      });
      test.on('error', () => resolve(false));
      setTimeout(() => resolve(false), 2000);
    });
    if (testConn) {
      console.log('[BRIDGE] Port ' + PORT + ' already in use — another instance is running. Exiting.');
      process.exit(0);
    }
  } catch (_) {}

  await initBaileys();
  app.listen(PORT, '127.0.0.1', () => {
    logger.info(`WhatsApp bridge listening on http://127.0.0.1:${PORT}/mcp`);
  });
}

main().catch((e) => {
  logger.error('Fatal:', e);
  process.exit(1);
});
