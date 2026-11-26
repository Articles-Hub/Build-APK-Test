/* Trình kết xuất Markdown / AsciiDoc (tập con) và trang mẫu cho CSS. Chạy offline trong WebView. */
(function (global) {
  'use strict';

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Chặn các URL có thể chạy mã (đầu vào đã được esc nên chỉ cần kiểm tra scheme).
  function safeUrl(u) {
    var t = u.replace(/&amp;/g, '&').trim();
    if (/^(javascript|vbscript|data):/i.test(t)) return '#';
    return u;
  }

  function inline(raw) {
    var codes = [];
    var s = raw.replace(/`([^`]+)`/g, function (m, c) {
      codes.push(c);
      return '\u0000' + (codes.length - 1) + '\u0000';
    });
    s = esc(s);
    s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+&quot;[^&]*&quot;)?\)/g, function (m, alt, u) {
      return '<img alt="' + alt + '" src="' + safeUrl(u) + '">';
    });
    s = s.replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+&quot;[^&]*&quot;)?\)/g, function (m, t, u) {
      return '<a href="' + safeUrl(u) + '">' + t + '</a>';
    });
    s = s.replace(/&lt;(https?:\/\/[^\s&]+)&gt;/g, '<a href="$1">$1</a>');
    s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(^|[^\w])__(.+?)__(?=[^\w]|$)/g, '$1<strong>$2</strong>');
    s = s.replace(/(^|[^\w*])\*(?!\s)(.+?)\*(?=[^\w*]|$)/g, '$1<em>$2</em>');
    s = s.replace(/(^|[^\w])_(?!\s)(.+?)_(?=[^\w]|$)/g, '$1<em>$2</em>');
    s = s.replace(/~~(.+?)~~/g, '<del>$1</del>');
    s = s.replace(/\u0000(\d+)\u0000/g, function (m, i) {
      return '<code>' + esc(codes[+i]) + '</code>';
    });
    return s;
  }

  function isTableSep(l) {
    return /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/.test(l) && l.indexOf('-') >= 0;
  }

  function splitRow(l) {
    var t = l.trim();
    if (t.charAt(0) === '|') t = t.slice(1);
    if (t.charAt(t.length - 1) === '|') t = t.slice(0, -1);
    return t.split('|').map(function (c) { return c.trim(); });
  }

  var LIST_RE = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;

  // Dựng danh sách lồng nhau theo mức thụt lề.
  function renderList(items) {
    var html = '';
    var stack = []; // {indent, tag}
    items.forEach(function (it) {
      while (stack.length && it.indent < stack[stack.length - 1].indent) {
        html += '</li></' + stack.pop().tag + '>';
      }
      if (!stack.length || it.indent > stack[stack.length - 1].indent) {
        html += '<' + it.tag + '>';
        stack.push({ indent: it.indent, tag: it.tag });
      } else {
        html += '</li>';
      }
      html += '<li>' + inline(it.text);
    });
    while (stack.length) html += '</li></' + stack.pop().tag + '>';
    return html;
  }

  function mdToHtml(text) {
    var lines = text.replace(/\r\n?/g, '\n').split('\n');
    var out = [];
    var i = 0;
    while (i < lines.length) {
      var l = lines[i];
      var m;

      if (/^\s*$/.test(l)) { i++; continue; }

      if ((m = /^\s*(```|~~~)\s*([\w+-]*)/.exec(l))) {
        var fence = m[1];
        var buf = [];
        i++;
        while (i < lines.length && lines[i].trim().indexOf(fence) !== 0) { buf.push(lines[i]); i++; }
        i++;
        out.push('<pre><code>' + esc(buf.join('\n')) + '</code></pre>');
        continue;
      }

      if ((m = /^(#{1,6})\s+(.*?)\s*#*\s*$/.exec(l))) {
        out.push('<h' + m[1].length + '>' + inline(m[2]) + '</h' + m[1].length + '>');
        i++;
        continue;
      }

      if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(l)) { out.push('<hr>'); i++; continue; }

      if (/^\s*>/.test(l)) {
        var q = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) { q.push(lines[i].replace(/^\s*>\s?/, '')); i++; }
        out.push('<blockquote>' + mdToHtml(q.join('\n')) + '</blockquote>');
        continue;
      }

      if (l.indexOf('|') >= 0 && i + 1 < lines.length && isTableSep(lines[i + 1])) {
        var head = splitRow(l);
        i += 2;
        var rows = [];
        while (i < lines.length && lines[i].indexOf('|') >= 0 && !/^\s*$/.test(lines[i])) { rows.push(splitRow(lines[i])); i++; }
        var t = '<table><thead><tr>' + head.map(function (c) { return '<th>' + inline(c) + '</th>'; }).join('') + '</tr></thead><tbody>';
        rows.forEach(function (r) { t += '<tr>' + r.map(function (c) { return '<td>' + inline(c) + '</td>'; }).join('') + '</tr>'; });
        out.push(t + '</tbody></table>');
        continue;
      }

      if (LIST_RE.test(l)) {
        var items = [];
        while (i < lines.length) {
          var lm = LIST_RE.exec(lines[i]);
          if (lm) {
            items.push({ indent: lm[1].replace(/\t/g, '    ').length, tag: /^\d/.test(lm[2]) ? 'ol' : 'ul', text: lm[3] });
            i++;
          } else if (/^\s+\S/.test(lines[i]) && items.length) {
            items[items.length - 1].text += ' ' + lines[i].trim();
            i++;
          } else {
            break;
          }
        }
        out.push(renderList(items));
        continue;
      }

      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^\s*(```|~~~|#{1,6}\s|>)/.test(lines[i])
             && !LIST_RE.test(lines[i]) && !/^\s*([-*_])(\s*\1){2,}\s*$/.test(lines[i])) {
        para.push(lines[i]);
        i++;
      }
      if (!para.length) { para.push(lines[i]); i++; }
      out.push('<p>' + para.map(function (x, k) {
        var br = / {2,}$/.test(x) && k < para.length - 1;
        return inline(x.trim()) + (br ? '<br>' : '');
      }).join(' ') + '</p>');
    }
    return out.join('\n');
  }

  // ---------- AsciiDoc (tập con) ----------

  function adocInline(raw) {
    var codes = [];
    var s = raw.replace(/`([^`]+)`/g, function (m, c) { codes.push(c); return '\u0000' + (codes.length - 1) + '\u0000'; });
    s = esc(s);
    s = s.replace(/link:(\S+?)\[([^\]]*)\]/g, function (m, u, t) { return '<a href="' + safeUrl(u) + '">' + (t || u) + '</a>'; });
    s = s.replace(/(https?:\/\/[^\s\[<]+)\[([^\]]*)\]/g, function (m, u, t) { return '<a href="' + safeUrl(u) + '">' + (t || u) + '</a>'; });
    s = s.replace(/(^|[^\w*])\*(?!\s)([^*]+?)\*(?=[^\w*]|$)/g, '$1<strong>$2</strong>');
    s = s.replace(/(^|[^\w])_(?!\s)([^_]+?)_(?=[^\w]|$)/g, '$1<em>$2</em>');
    s = s.replace(/\u0000(\d+)\u0000/g, function (m, i) { return '<code>' + esc(codes[+i]) + '</code>'; });
    return s;
  }

  function adocToHtml(text) {
    var lines = text.replace(/\r\n?/g, '\n').split('\n');
    var out = [];
    var i = 0;
    var m;
    while (i < lines.length) {
      var l = lines[i];
      if (/^\s*$/.test(l)) { i++; continue; }
      if (/^\/\/(?!\/)/.test(l)) { i++; continue; }                       // chú thích
      if (/^\[.*\]\s*$/.test(l) && !/^\[\[/.test(l)) { i++; continue; }    // thuộc tính khối

      if ((m = /^(={1,6})\s+(.*)$/.exec(l))) {
        out.push('<h' + m[1].length + '>' + adocInline(m[2]) + '</h' + m[1].length + '>');
        i++;
        continue;
      }
      if (/^(-{4,}|\.{4,})\s*$/.test(l)) {
        var delim = l.trim();
        var buf = [];
        i++;
        while (i < lines.length && lines[i].trim() !== delim) { buf.push(lines[i]); i++; }
        i++;
        out.push('<pre><code>' + esc(buf.join('\n')) + '</code></pre>');
        continue;
      }
      if (/^_{4,}\s*$/.test(l)) {
        var qb = [];
        i++;
        while (i < lines.length && !/^_{4,}\s*$/.test(lines[i])) { qb.push(lines[i]); i++; }
        i++;
        out.push('<blockquote>' + adocToHtml(qb.join('\n')) + '</blockquote>');
        continue;
      }
      if (/^={4,}\s*$/.test(l)) {
        var eb = [];
        i++;
        while (i < lines.length && !/^={4,}\s*$/.test(lines[i])) { eb.push(lines[i]); i++; }
        i++;
        out.push('<div class="block">' + adocToHtml(eb.join('\n')) + '</div>');
        continue;
      }
      if (/^\|={3,}\s*$/.test(l)) {
        var rows = [];
        i++;
        while (i < lines.length && !/^\|={3,}\s*$/.test(lines[i])) {
          if (/^\s*\|/.test(lines[i])) rows.push(lines[i].trim().slice(1).split('|').map(function (c) { return c.trim(); }));
          i++;
        }
        i++;
        var tb = '<table>';
        rows.forEach(function (r, k) {
          tb += '<tr>' + r.map(function (c) { return (k === 0 ? '<th>' : '<td>') + adocInline(c) + (k === 0 ? '</th>' : '</td>'); }).join('') + '</tr>';
        });
        out.push(tb + '</table>');
        continue;
      }
      if ((m = /^image::?(\S+?)\[([^\]]*)\]\s*$/.exec(l))) {
        out.push('<p><img alt="' + esc(m[2]) + '" src="' + safeUrl(esc(m[1])) + '"></p>');
        i++;
        continue;
      }
      if (/^(\*+|-|\.+)\s+\S/.test(l)) {
        var items = [];
        while (i < lines.length && (m = /^(\*+|-|\.+)\s+(.*)$/.exec(lines[i]))) {
          var mark = m[1];
          items.push({ indent: (mark === '-' ? 1 : mark.length) * 2, tag: mark.charAt(0) === '.' ? 'ol' : 'ul', text: m[2] });
          i++;
        }
        out.push(renderList(items));
        continue;
      }
      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^(={1,6}\s|-{4,}|\.{4,}|\|={3,}|\*+\s|\.+\s)/.test(lines[i])) {
        para.push(lines[i]);
        i++;
      }
      if (!para.length) { para.push(lines[i]); i++; }
      var joined = para.join(' ');
      if ((m = /^(NOTE|TIP|IMPORTANT|WARNING|CAUTION):\s+(.*)$/.exec(joined))) {
        out.push('<div class="adm ' + m[1].toLowerCase() + '"><b>' + m[1] + '</b> ' + adocInline(m[2]) + '</div>');
      } else {
        out.push('<p>' + adocInline(joined) + '</p>');
      }
    }
    return out.join('\n');
  }

  var CSS_SAMPLE = '<h1>Heading 1</h1><h2>Heading 2</h2><h3>Heading 3</h3>' +
    '<p>A paragraph with <a href="#">a link</a>, <strong>bold</strong>, <em>italic</em> and <code>code</code>.</p>' +
    '<div class="box container card">div.box.container.card</div>' +
    '<ul><li>List item one</li><li>List item two</li></ul>' +
    '<ol><li>First</li><li>Second</li></ol>' +
    '<button>Button</button> <input type="text" placeholder="Input"> <select><option>Select</option></select>' +
    '<blockquote>Blockquote</blockquote>' +
    '<table><tr><th>Head</th><th>Head</th></tr><tr><td>Cell</td><td>Cell</td></tr></table>' +
    '<pre>pre block</pre><hr><img alt="img" width="64" height="40">';

  function render(kind, text, dark) {
    var out = document.getElementById('out');
    document.documentElement.setAttribute('data-dark', dark ? 'true' : 'false');
    var user = document.getElementById('user');
    user.textContent = '';
    if (kind === 'md') out.innerHTML = mdToHtml(text);
    else if (kind === 'adoc') out.innerHTML = adocToHtml(text);
    else if (kind === 'css') { user.textContent = text; out.innerHTML = CSS_SAMPLE; }
  }

  global.mdToHtml = mdToHtml;
  global.adocToHtml = adocToHtml;
  global.render = render;
  if (typeof module !== 'undefined' && module.exports) module.exports = { mdToHtml: mdToHtml, adocToHtml: adocToHtml };
})(typeof window !== 'undefined' ? window : globalThis);
