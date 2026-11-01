const fs = require('fs');

const path = 'D:/A-Code/LiteBrawl/src/main/resources/web/index.html';
const html = fs.readFileSync(path, 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
const src = scripts[scripts.length - 1];

// 1) 语法检查
try {
  new Function(src);
  console.log('JS 语法: OK');
} catch (e) {
  console.log('JS 语法错误:', e.message);
  process.exit(1);
}

// 2) 高亮函数功能验证：去掉标签后必须和原文（转义后）完全一致
const start = src.indexOf('function escapeHtml');
const end = src.indexOf('async function renderRaw');
const funcs = src.slice(start, end);
const factory = new Function(funcs + '\nreturn { highlightLine, colorValue, escapeHtml };');
const { highlightLine, escapeHtml } = factory();

const samples = [
  'prefix: "<gold>LiteBrawl</gold> "',
  '  coins: 5',
  '  enabled: true',
  '  growth: 1.35',
  '  # 这是注释',
  '    - BAMBOO_MOSAIC',
  '  lethal-blocks:',
  'kit:',
  '',
];

let failed = 0;
for (const line of samples) {
  const out = [];
  highlightLine(line, out);
  const rendered = out.join('');
  const plain = rendered.replace(/<[^>]+>/g, '');
  const expected = escapeHtml(line);
  const ok = plain === expected;
  if (!ok) failed++;
  console.log(
    (ok ? 'OK  ' : 'FAIL') + ' ' + JSON.stringify(line) +
    ' | 高亮:' + (rendered.includes('<span') ? '是' : '否') +
    (ok ? '' : ' | 得到 ' + JSON.stringify(plain))
  );
}

console.log(failed === 0 ? '全部通过' : failed + ' 条失败');
process.exit(failed === 0 ? 0 : 1);
