/**
 * 个人行程管理工具 —— 本地数据服务
 * 零依赖：仅使用 Node 原生模块（http / fs / path），可直接拷贝到 Mac 使用。
 *
 * 启动：node server.js   （默认端口 7788，可用环境变量 PORT 修改）
 * 访问：http://localhost:7788
 *
 * API：
 *   GET /api/trips    读取全部行程数据
 *   PUT /api/trips    整体保存行程数据（原子写入，防损坏）
 * 其余路径：返回 app.html（静态前端）
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const DATA_DIR = path.join(ROOT, 'data');
const DATA_FILE = path.join(DATA_DIR, 'trips.json');
const PORT = process.env.PORT || 7788;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

// ---------- 数据初始化 ----------
function ensureDataFile() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) {
    // 预置一条示例行程，方便首次打开即可看到效果
    const seed = {
      trips: [
        {
          id: 'demo-2026',
          name: '示例行程：上海 → 杭州 → 千岛湖',
          emoji: '🏝️',
          startDate: '2026-08-15',
          endDate: '2026-08-17',
          travelers: ['我'],
          budget: 3000,
          color: '#FF6B6B',
          notes: '这是一条示例数据，可在此页直接编辑或删除。',
          days: {
            '2026-08-15': {
              notes: '下午到达杭州，先逛西湖。',
              lodging: { type: '酒店', name: '西湖边青年旅舍', location: '杭州西湖区', pricePerNight: 180, notes: '' },
              travelers: [],
              expenses: [
                { category: '交通', item: '高铁 上海→杭州', amount: 73, when: '09:00', source: { type: 'seg', idx: 0 } },
                { category: '餐饮', item: '西湖边午餐', amount: 68, when: '12:30', source: { type: 'custom', label: '西湖边小馆' } },
                { category: '住宿', item: '青旅一晚', amount: 180, when: '20:00', source: { type: 'lodge' } },
              ],
              segments: [
                { from: '上海虹桥站', to: '杭州东站', time: '09:00-10:30', transport: '高铁', notes: '', departTime: '09:00', arriveTime: '10:30', vehicleNo: 'G1345', seat: '3车5D', passengers: ['我'] },
                { from: '杭州东站', to: '西湖景区', time: '10:30-11:30', transport: '地铁', notes: '1号线转4号线' },
                { from: '西湖景区', to: '灵隐寺', time: '14:00-14:40', transport: '公交', notes: '' },
              ],
              pins: [
                { name: '杭州东站', lat: 30.2894, lng: 120.2107 },
                { name: '西湖', lat: 30.2480, lng: 120.1527 },
                { name: '灵隐寺', lat: 30.2400, lng: 120.1040 },
              ],
            },
            '2026-08-16': {
              notes: '',
              lodging: { type: '民宿', name: '千岛湖临湖民宿', location: '杭州淳安县', pricePerNight: 420, notes: '含早餐' },
              travelers: [],
              expenses: [
                { category: '交通', item: '打车 杭州→千岛湖', amount: 220, when: '09:00', source: { type: 'seg', idx: 0 } },
                { category: '门票', item: '千岛湖游船', amount: 150, when: '11:00', source: { type: 'seg', idx: 1 } },
              ],
              segments: [
                { from: '杭州西湖', to: '千岛湖景区', time: '09:00-11:30', transport: '打车', notes: '约150km' },
                { from: '千岛湖景区', to: '梅峰岛', time: '13:00-17:00', transport: '游船', notes: '' },
              ],
              pins: [
                { name: '千岛湖景区', lat: 29.6053, lng: 119.0175 },
                { name: '梅峰岛', lat: 29.5515, lng: 119.0834 },
              ],
            },
            '2026-08-17': {
              notes: '返程。',
              lodging: { type: '酒店', name: '', location: '', pricePerNight: 0, notes: '' },
              travelers: [],
              expenses: [
                { category: '交通', item: '高铁 千岛湖→上海', amount: 201, when: '14:00', source: { type: 'seg', idx: 1 } },
              ],
              segments: [
                { from: '千岛湖', to: '杭州东站', time: '11:00-13:00', transport: '高铁', notes: '', departTime: '11:00', arriveTime: '13:00', vehicleNo: 'G1890', seat: '2车8C', passengers: ['我'] },
                { from: '杭州东站', to: '上海虹桥站', time: '13:30-15:00', transport: '高铁', notes: '', departTime: '13:30', arriveTime: '15:00', vehicleNo: 'G158', seat: '4车2F', passengers: ['我'] },
              ],
              pins: [],
            },
          },
        },
      ],
    };
    fs.writeFileSync(DATA_FILE, JSON.stringify(seed, null, 2), 'utf-8');
  }
}

// ---------- 数据读写（原子写入） ----------
function readData() {
  ensureDataFile();
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf-8'));
  } catch (e) {
    console.error('数据文件解析失败，已重置为空数据：', e.message);
    const empty = { trips: [] };
    fs.writeFileSync(DATA_FILE, JSON.stringify(empty, null, 2), 'utf-8');
    return empty;
  }
}

function writeData(data) {
  ensureDataFile();
  const tmp = DATA_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf-8');
  fs.renameSync(tmp, DATA_FILE); // 原子替换，避免写入中途崩溃损坏数据
}

// ---------- 请求处理 ----------
function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (c) => {
      raw += c;
      if (raw.length > 50 * 1024 * 1024) { reject(new Error('请求体过大')); req.destroy(); }
    });
    req.on('end', () => resolve(raw));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = decodeURIComponent(url.pathname);

  try {
    // ---- API ----
    if (pathname === '/api/trips') {
      if (req.method === 'GET') {
        sendJson(res, 200, readData());
        return;
      }
      if (req.method === 'PUT') {
        const raw = await readBody(req);
        let data;
        try {
          data = JSON.parse(raw);
        } catch (e) {
          sendJson(res, 400, { error: 'JSON 格式错误' });
          return;
        }
        if (!data || !Array.isArray(data.trips)) {
          sendJson(res, 400, { error: '数据结构错误：缺少 trips 数组' });
          return;
        }
        writeData(data);
        sendJson(res, 200, { ok: true });
        return;
      }
      sendJson(res, 405, { error: '方法不允许' });
      return;
    }

    if (pathname === '/api/health') {
      sendJson(res, 200, { ok: true, port: PORT });
      return;
    }

    // 数据位置信息（供前端「数据管理」面板展示真实路径）
    if (pathname === '/api/info') {
      sendJson(res, 200, {
        dataFile: DATA_FILE,
        dataDir: DATA_DIR,
        mode: 'server',
        trips: readData().trips.length,
      });
      return;
    }

    // ---- 静态文件 ----
    let filePath;
    if (pathname === '/' || pathname === '/index.html') {
      filePath = path.join(ROOT, 'app.html');
    } else {
      // 防止路径穿越
      filePath = path.join(ROOT, pathname.replace(/^\/+/, ''));
      if (!filePath.startsWith(ROOT)) {
        sendJson(res, 403, { error: '禁止访问' });
        return;
      }
    }

    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Cache-Control': 'no-cache, no-store, must-revalidate' });
      fs.createReadStream(filePath).pipe(res);
      return;
    }

    sendJson(res, 404, { error: '未找到' });
  } catch (e) {
    console.error('服务器错误：', e);
    sendJson(res, 500, { error: '服务器内部错误' });
  }
});

ensureDataFile();
server.listen(PORT, () => {
  console.log('============================================');
  console.log('  🧳 个人行程管理工具已启动');
  console.log(`  浏览器打开：http://localhost:${PORT}`);
  console.log(`  数据文件：${DATA_FILE}`);
  console.log('  停止服务：Ctrl + C');
  console.log('============================================');
});
