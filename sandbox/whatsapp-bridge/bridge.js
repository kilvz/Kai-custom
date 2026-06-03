import { makeWASocket, useMultiFileAuthState, fetchLatestBaileysVersion, DisconnectReason, makeInMemoryStore } from '@whiskeysockets/baileys';
import express from 'express';
import pino from 'pino';
import QRCode from 'qrcode';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const AUTH_DIR = path.join(__dirname, 'auth_info');
const CONFIG_FILE = path.join(__dirname, 'config.json');
const QR_FILE = '/tmp/whatsapp-qr.png';
const QR_BASE64_FILE = '/tmp/whatsapp-qr.txt';
const UNREAD_FILE = '/tmp/whatsapp-unread.json';
const PORT = parseInt(process.env.WA_BRIDGE_PORT || '8317', 10);

let sock = null;
let store = null;
let currentQr = null;
let connected = false;

const logger = pino({ level: 'silent' });

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
  const { version } = await fetchLatestBaileysVersion();
  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
  store = makeInMemoryStore({ logger });

  const cfg = loadConfig();
  const browser = cfg.browser || ['Kai-custom', 'Chrome', '3.8.0'];
  const markOnline = cfg.markOnlineOnConnect !== undefined ? cfg.markOnlineOnConnect : true;
  const syncHistory = cfg.syncFullHistory !== undefined ? cfg.syncFullHistory : false;
  const linkPreviews = cfg.generateHighQualityLinkPreview !== undefined ? cfg.generateHighQualityLinkPreview : true;
  const shouldSync = cfg.shouldSyncHistoryMsg !== undefined ? cfg.shouldSyncHistoryMsg : false;

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
    }
    if (connection === 'open') {
      connected = true;
      currentQr = null;
      try { fs.unlinkSync(QR_FILE); } catch (e) {}
      try { fs.unlinkSync(QR_BASE64_FILE); } catch (e) {}
    }
    if (connection === 'close') {
      connected = false;
      const statusCode = lastDisconnect?.error?.output?.statusCode;
      const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
      if (shouldReconnect) setTimeout(() => initBaileys(), 5000);
    }
  });

  sock.ev.on('messages.upsert', async (m) => {
    const msgs = m.messages.filter(msg => msg.key.fromMe === false && msg.message);
    if (msgs.length > 0) {
      const unread = loadUnread();
      for (const msg of msgs) {
        const remoteJid = msg.key.remoteJid;
        const text = msg.message.conversation || msg.message.extendedTextMessage?.text || '';
        if (text) {
          unread.push({
            chatId: remoteJid,
            messageId: msg.key.id,
            text,
            fromName: msg.pushName || remoteJid.split('@')[0],
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

const app = express();
app.use(express.json());

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
            inputSchema: { type: 'object', properties: {} },
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
          await sock.sendMessage(jid, { text: args.text });
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
          const msgs = store?.messages?.[args.chat_id]?.all() || [];
          const sorted = msgs.sort((a, b) => (b.messageTimestamp || 0) - (a.messageTimestamp || 0)).slice(0, args?.limit || 30).map(m => ({
            fromMe: m.key.fromMe,
            text: m.message?.conversation || m.message?.extendedTextMessage?.text || m.message?.imageMessage?.caption || '[non-text message]',
            timestamp: m.messageTimestamp,
            id: m.key.id,
          }));
          content = [{ type: 'text', text: JSON.stringify(sorted) }];
          break;
        }

        case 'get_qr_code': {
          if (connected) {
            content = [{ type: 'text', text: JSON.stringify({ authenticated: true, qr: null }) }];
          } else {
            try {
              if (fs.existsSync(QR_BASE64_FILE)) {
                const qr = fs.readFileSync(QR_BASE64_FILE, 'utf-8').trim();
                content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr }) }];
              } else {
                content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr: currentQr || '' }) }];
              }
            } catch (e) {
              content = [{ type: 'text', text: JSON.stringify({ authenticated: false, qr: currentQr || '' }) }];
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
  await initBaileys();
  app.listen(PORT, '127.0.0.1', () => {
    logger.info(`WhatsApp bridge listening on http://127.0.0.1:${PORT}/mcp`);
  });
}

main().catch((e) => {
  logger.error('Fatal:', e);
  process.exit(1);
});
