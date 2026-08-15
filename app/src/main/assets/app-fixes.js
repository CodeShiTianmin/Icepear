(function () {
  'use strict';

  const v2El = id => document.getElementById(id);
  const v2Now = () => Date.now();
  const v2Role = key => data.roles[key || data.activeRole];
  const v2Amount = message => Number(message.gift ? message.price : message.amount) || 0;
  const v2IsVisible = element => element && getComputedStyle(element).display !== 'none';
  const v2ReplyTimers = Object.create(null);
  let v2LastPokeAt = 0;

  function v2Uid(prefix) {
    return (prefix || 'id') + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
  }

  function v2Lines(value) {
    return String(value || '').split('\n').map(item => item.trim()).filter(Boolean);
  }

  function v2ClampNumber(value, fallback, min, max) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.min(max, Math.max(min, number));
  }

  function v2SafeImage(value) {
    const source = String(value || '');
    if (/^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=\s]+$/i.test(source)) return source;
    if (/^https:\/\//i.test(source)) {
      try {
        const url = new URL(source);
        return url.protocol === 'https:' ? url.href : '';
      } catch (_) {
        return '';
      }
    }
    return '';
  }

  function v2StarterCards() {
    return {
      '日常': [
        '我在，慢慢说。', '今天过得怎么样？', '先喝口水，再继续忙。', '刚刚突然想到你。',
        '你愿意讲的话，我会认真听。', '别急，我们一件一件来。', '有我陪你呢。', '今天也辛苦啦。'
      ],
      '关心': [
        '不舒服就休息一下，好吗？', '你已经做得很好了。', '不用一直坚强。', '我更在意你的感受。',
        '先照顾好自己，其他事情可以晚一点。', '抱抱你。', '别把所有情绪都藏起来。', '我没有走开。'
      ],
      '晚安': [
        '晚安，祝你做一个轻轻的梦。', '把今天放下吧，明天再继续。', '盖好被子，别着凉。', '睡醒记得来找我。',
        '今天的你也值得被好好珍惜。', '去休息吧，我在这里。'
      ],
      '小情绪': [
        '哼，那你要哄哄我。', '好吧，只原谅你一点点。', '想和你多待一会儿。', '你是不是忘了想我？',
        '不许偷偷难过。', '再靠近一点。'
      ]
    };
  }

  function v2Migrate() {
    const keys = Object.keys(data.roles || {});
    const active = v2Role();
    const fresh = keys.length === 1 && active && (!active.chat || active.chat.length === 0) &&
      Object.values(active.cards || {}).every(list => !Array.isArray(list) || list.length === 0);

    keys.forEach(key => {
      const role = data.roles[key];
      role.id = role.id || key;
      role.chat = Array.isArray(role.chat) ? role.chat : [];
      role.cards = role.cards && typeof role.cards === 'object' ? role.cards : {'默认': []};
      role.shop = Array.isArray(role.shop) ? role.shop : [];
      role.letters = Array.isArray(role.letters) ? role.letters : [];
      role.bill = Array.isArray(role.bill) ? role.bill : [];
      role.moments = Array.isArray(role.moments) ? role.moments : [];
      role.weekly = role.weekly && typeof role.weekly === 'object' ? role.weekly : {key: '', date: '', stats: null};
      role.chat.forEach(message => {
        message.id = message.id || v2Uid('msg');
        if (message.type === 'sys') {
          const roleName = role.nickname || role.name || '他';
          (role.pokes || []).forEach(poke => {
            const legacyText = '你' + poke + '了' + roleName;
            if (message.text === legacyText) {
              message.text = '你' + String(poke).split('他').join(roleName);
            }
          });
        }
        if ((message.type === 'red' || message.type === 'zhuan' || message.type === 'gift') && message.handled && !message.txStatus) {
          message.txStatus = 'accepted';
        }
      });
      role.chat = role.chat.filter((message, index, list) => {
        const previous = list[index - 1];
        if (!previous || message.type !== 'sys' || previous.type !== 'sys') return true;
        const roleName = role.nickname || role.name || '他';
        const isPoke = (role.pokes || []).some(poke => message.text === '你' + String(poke).split('他').join(roleName));
        return !(isPoke && message.text === previous.text && Math.abs(Number(message.t) - Number(previous.t)) < 1200);
      });
    });

    if (active && Array.isArray(data.bill) && data.bill.length && !active.bill.length) active.bill = data.bill;
    if (active && Array.isArray(data.moments) && data.moments.length && !active.moments.length) active.moments = data.moments;
    if (active && data.weekly && data.weekly.stats && !active.weekly.stats) active.weekly = data.weekly;

    if (fresh) {
      active.cards = v2StarterCards();
      active.shop = [
        {name: '草莓奶油蛋糕', price: 28, cat: 'food', wm: true},
        {name: '热可可', price: 16, cat: 'drink', wm: true},
        {name: '香薰蜡烛', price: 39, cat: 'daily', wm: false}
      ];
      active.chat.push({
        id: v2Uid('msg'), side: 'other', text: '嗨，我在这里。今天想聊点什么？', t: v2Now(), read: true
      });
      data.reply.delayMin = 2;
      data.reply.delayMax = 7;
      data.reply.replyMin = 1;
      data.reply.replyMax = 2;
      data.reply.gap = 2;
      data.theme = {myBg: '#6d3b58', myText: '#ffffff', hisBg: '#fffaf7', hisText: '#2d252a'};
      data.font.radius = 18;
      data.font.size = 16;
      try { storeRemove('milkPatches'); } catch (_) {}
    }

    data.schemaVersion = 2.1;
    save();
  }

  function v2RemovePatchConsole() {
    const patchArea = v2El('patchArea');
    if (!patchArea) return;
    const parent = patchArea.parentElement;
    const heading = Array.from(parent.querySelectorAll('h3')).find(node => node.textContent.includes('永久补丁'));
    if (heading) heading.remove();
    const hint = Array.from(parent.querySelectorAll('.hint')).find(node => node.textContent.includes('粘贴代码'));
    if (hint) hint.remove();
    ['patchArea', 'btnApplyPatch', 'btnClearPatches', 'patchList'].forEach(id => v2El(id)?.remove());
  }

  const v2NavItems = [
    ['pageChat', '聊天', '<path d="M5 17.5 3.5 21l4.2-1.6A9 9 0 1 0 5 17.5Z"/><path d="M8 11.5h8M8 8.5h5"/>'],
    ['pageMoments', '动态', '<rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m4 17 5-5 3 3 3-3 5 5"/>'],
    ['pageMenu', '发现', '<path d="M12 3 9.5 9.5 3 12l6.5 2.5L12 21l2.5-6.5L21 12l-6.5-2.5Z"/>'],
    ['pageLetter', '信箱', '<path d="M4 5h16v14H4z"/><path d="m4 7 8 6 8-6"/>'],
    ['pageSet', '我的', '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>']
  ];

  function v2CreateNav() {
    if (v2El('bottomNav')) return;
    const nav = document.createElement('nav');
    nav.id = 'bottomNav';
    nav.setAttribute('aria-label', '主导航');
    v2NavItems.forEach(([page, label, icon]) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'nav-btn';
      button.dataset.page = page;
      button.setAttribute('aria-label', label);
      button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' + icon + '</svg><span>' + label + '</span>';
      button.addEventListener('click', () => goPage(page));
      nav.appendChild(button);
    });
    document.body.appendChild(nav);
  }

  function v2StripLeadingEmoji(node) {
    if (!node || node.querySelector?.('svg')) return;
    const cleaned = node.textContent.replace(/^[\p{Extended_Pictographic}\uFE0F\u200D\s]+/u, '').trim();
    if (cleaned) node.textContent = cleaned;
  }

  function v2EnhanceStructureIcons() {
    const menuIcons = [
      '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
      '<path d="M4 8h16l-1 12H5Z"/><path d="M8 8a4 4 0 0 1 8 0"/>',
      '<rect x="5" y="4" width="14" height="16" rx="3"/><path d="M9 8h6M9 12h6M9 16h4"/>',
      '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m4 7 8 6 8-6"/>',
      '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
      '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m4 17 5-5 3 3 3-3 5 5"/>',
      '<circle cx="8" cy="8" r="3"/><path d="M8 2v2M8 12v2M2 8h2M12 8h2"/><path d="M8 19h10a3 3 0 0 0 .4-6A5 5 0 0 0 9 15"/>',
      '<path d="M7 18h10a4 4 0 0 0 .6-8A6 6 0 0 0 6.3 12 3 3 0 0 0 7 18Z"/>'
    ];
    document.querySelectorAll('.pagebar span, h3, .btn, .cat-btn').forEach(v2StripLeadingEmoji);
    document.querySelectorAll('#pageMenu .group-tab').forEach((button, index) => {
      const label = button.textContent.replace(/^[\p{Extended_Pictographic}\uFE0F\u200D\s]+/u, '').trim();
      button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' + menuIcons[index] + '</svg><span>' + esc(label) + '</span>';
    });
    document.querySelectorAll('button[onclick^="pickHisAvatar"],button[onclick^="pickMyAvatar"]').forEach(button => {
      button.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h3l2-2h6l2 2h3v12H4Z"/><circle cx="12" cy="13" r="3"/></svg>';
      button.setAttribute('aria-label', '从相册选择头像');
    });
    const sendButton = v2El('btnSend');
    if (sendButton) {
      sendButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 17 8-17 8 3-8Z"/><path d="M7 12h14"/></svg>';
      sendButton.setAttribute('aria-label', '发送');
    }
  }

  function v2SyncNavigation(pageName) {
    const page = pageName || document.querySelector('.page.active')?.id || 'pageChat';
    document.body.classList.toggle('view-chat', page === 'pageChat');
    document.querySelectorAll('#bottomNav .nav-btn').forEach(button => {
      const active = button.dataset.page === page;
      button.classList.toggle('on', active);
      button.setAttribute('aria-current', active ? 'page' : 'false');
    });
  }

  const v2OriginalGoPage = goPage;
  goPage = function (pageName) {
    v2OriginalGoPage(pageName);
    v2SyncNavigation(pageName);
    if (pageName === 'pageShop') renderBill();
  };

  const v2OriginalApplyTheme = applyTheme;
  applyTheme = function () {
    v2OriginalApplyTheme();
    const radius = v2ClampNumber(data.font?.radius, 18, 0, 28);
    document.documentElement.style.setProperty('--bubble-radius', radius + 'px');
    const darkMode = document.body.classList.contains('dark');
    if (typeof applyWallpaper === 'function') applyWallpaper();
    try { window.AndroidBridge?.setSystemBars(darkMode); } catch (_) {}
  };

  applyWallpaper = function () {
    const area = v2El('chatArea');
    const darkMode = document.body.classList.contains('dark');
    const wallpaper = data.wallpaper || {preset: '默认', image: ''};
    const source = v2SafeImage(RS(wallpaper.image || ''));
    if (wallpaper.preset === '图片' && source) {
      area.style.background = 'linear-gradient(rgba(20,15,18,.08),rgba(20,15,18,.08)),url("' + source.replace(/"/g, '%22') + '") center/cover no-repeat';
    } else if (wallpaper.preset === '默认') {
      const art = darkMode ? 'art/chat-paper-night.webp' : 'art/chat-paper-day.webp';
      const veil = darkMode ? 'rgba(23,19,22,.22)' : 'rgba(248,243,239,.14)';
      area.style.background = 'linear-gradient(' + veil + ',' + veil + '),url("' + art + '") center/cover no-repeat';
    } else if (darkMode) {
      area.style.background = 'radial-gradient(circle at 100% 0%,rgba(215,154,191,.07),transparent 32%),#171316';
    } else {
      const backgrounds = {
        '默认': 'radial-gradient(circle at 100% 0%,rgba(231,125,115,.08),transparent 30%),linear-gradient(180deg,#f9f5f2,#f5efeb)',
        '粉色': 'linear-gradient(160deg,#fff4f4,#f8e7ec)',
        '蓝色': 'linear-gradient(160deg,#f4f7fb,#e8f0f4)',
        '绿色': 'linear-gradient(160deg,#f4f8f5,#e8f1eb)',
        '星空': 'radial-gradient(circle at 20% 20%,rgba(255,255,255,.14),transparent 2%),linear-gradient(160deg,#29243c,#151323)'
      };
      area.style.background = backgrounds[wallpaper.preset] || backgrounds['默认'];
    }
    const row = v2El('wallRow');
    if (row) {
      row.innerHTML = WALLS.map(name => '<button class="wall-btn ' + (wallpaper.preset === name ? 'on' : '') + '" onclick="setWall(\'' + name + '\')">' + name + '</button>').join('');
    }
  };

  window.handleAndroidBack = function () {
    if (v2IsVisible(v2El('cardPop'))) { closeCard(); return true; }
    if (v2IsVisible(v2El('callScreen'))) { v2El('callScreen').style.display = 'none'; return true; }
    if (v2IsVisible(v2El('videoScreen'))) { minimizeVideo(); return true; }
    if (v2IsVisible(v2El('videoMini'))) { hangUp(); return true; }
    if (v2IsVisible(v2El('cloudPanel'))) { closeCloud(); return true; }
    if (v2IsVisible(v2El('actionPanel'))) { closeAction(); return true; }
    if (v2IsVisible(v2El('panel'))) { closePanel(); return true; }
    if (v2IsVisible(v2El('plusPanel'))) { closePlus(); return true; }
    if (document.querySelector('.page.active')?.id !== 'pageChat') { backChat(); return true; }
    return false;
  };

  cardBtn = function (index) {
    const callback = window._cardCb?.[index];
    closeCard();
    if (typeof callback === 'function') callback();
  };

  function v2MarkReadLater(roleKey, messageId) {
    setTimeout(() => {
      const role = v2Role(roleKey);
      const message = role?.chat.find(item => item.id === messageId);
      if (!message || message.recall || message.read) return;
      message.read = true;
      save();
      if (data.activeRole === roleKey) renderChat(false);
    }, rand(4, 9) * 1000);
  }

  function v2AddMessage(roleKey, side, content, extra) {
    const role = v2Role(roleKey);
    if (!role) return null;
    let message;
    if (typeof content === 'object') {
      message = Object.assign({id: v2Uid('msg'), side, t: v2Now()}, content);
    } else {
      message = Object.assign({id: v2Uid('msg'), side, text: content, t: v2Now()}, extra || {});
    }
    role.chat.push(message);
    save();
    if (data.activeRole === roleKey) renderChat(side === 'me');
    if (side === 'other' && data.sound.onRecv) playSound(data.sound.type);
    if (side === 'me' && !message.recall) v2MarkReadLater(roleKey, message.id);
    return message;
  }

  addMsg = function (side, content, extra) {
    return v2AddMessage(data.activeRole, side, content, extra);
  };

  function v2AllCards(role) {
    return Object.values(role?.cards || {}).flatMap(list => Array.isArray(list) ? list : []);
  }

  function v2LastReadMessage(role) {
    return [...(role?.chat || [])].reverse().find(message =>
      message.side === 'me' && !message.recall && !message.type && message.text && message.read
    ) || null;
  }

  scheduleReply = function (requestedRoleKey) {
    const roleKey = requestedRoleKey || data.activeRole;
    const role = v2Role(roleKey);
    if (!role) return;
    clearTimeout(v2ReplyTimers[roleKey]);
    if (data.chatOpt.hisIgnore && Math.random() * 100 < (data.reply.ignoreRate || 20)) return;

    if (data.activeRole === roleKey) setTyping(true);
    let wait = rand(data.reply.delayMin, data.reply.delayMax) * 1000;
    const hour = new Date().getHours();
    if (data.sim.bioClock && (hour >= 23 || hour < 7)) wait *= 2.5;

    v2ReplyTimers[roleKey] = setTimeout(() => {
      if (data.activeRole === roleKey) setTyping(false);
      const currentRole = v2Role(roleKey);
      if (!currentRole) return;
      const festival = festivalToday();
      const memo = memoToday();
      if (data.sim.festival && festival && Math.random() < 0.4) {
        v2AddMessage(roleKey, 'other', '今天是' + festival + '，' + ['想你', '陪你过节', '节日快乐'][rand(0, 2)]);
        return;
      }
      if (data.sim.festival && memo && Math.random() < 0.4) {
        v2AddMessage(roleKey, 'other', '今天是「' + memo + '」，' + ['一直在心里', '记得呢', '陪你一起'][rand(0, 2)]);
        return;
      }
      const pool = v2AllCards(currentRole);
      if (!pool.length) return;
      const count = Math.min(pool.length, rand(data.reply.replyMin, data.reply.replyMax));
      const picked = shuffle(pool).slice(0, count);
      const quoted = v2LastReadMessage(currentRole);
      picked.forEach((line, index) => {
        setTimeout(() => {
          const extra = index === 0 && quoted && Math.random() < 0.4 ? {quote: quoted.text} : {};
          v2AddMessage(roleKey, 'other', line, extra);
        }, index * data.reply.gap * 1000);
      });
    }, wait);
  };

  function v2TransactionStatus(message) {
    if (message.txStatus === 'returned') return '已退还';
    if (message.txStatus === 'accepted' || message.handled) return '已接收';
    return '待处理';
  }

  function v2TransactionIcon(type, configuredIcon) {
    const legacyDefaults = ['🧧', '💸', '🎁', '¥'];
    if (configuredIcon && !legacyDefaults.includes(configuredIcon)) return esc(configuredIcon);
    const paths = {
      red: '<rect x="4" y="3" width="16" height="18" rx="3"/><path d="M4 8h16M9 12h6M12 9v6"/>',
      zhuan: '<path d="M4 8h13l-3-3M20 16H7l3 3"/>',
      gift: '<rect x="3" y="9" width="18" height="12" rx="2"/><path d="M12 9v12M3 13h18M12 9H7.5a2.5 2.5 0 1 1 4.5-1.5ZM12 9h4.5A2.5 2.5 0 1 0 12 7.5Z"/>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + paths[type] + '</svg>';
  }

  renderChat = function (forceScroll) {
    const area = v2El('chatArea');
    const role = v2Role();
    const shouldScroll = Boolean(forceScroll) || nearBottom();
    const keyword = searchMode ? (v2El('searchChat')?.value || '').trim().toLowerCase() : '';
    let indexed = role.chat.map((message, index) => ({message, index}));
    if (keyword) {
      indexed = indexed.filter(({message}) => {
        if (message.type === 'sys') return String(message.text || '').toLowerCase().includes(keyword);
        if (message.recall) return false;
        return ((message.text || '') + ' ' + (message.gift || '') + ' ' + (message.note || '') + ' ' + (message.amount || ''))
          .toLowerCase().includes(keyword);
      });
    }

    if (!forceScroll) area.classList.add('noanim');
    area.innerHTML = indexed.map(({message: m, index}) => {
      if (m.type === 'sys') return '<div class="sysmsg" data-i="' + index + '">' + esc(m.text) + '</div>';
      if (m.recall) return '<div class="sysmsg" data-i="' + index + '">' + (m.side === 'me' ? '你' : esc(displayName())) + '撤回了一条消息</div>';
      const mine = m.side === 'me';
      const time = new Date(m.t);
      const timeText = String(time.getHours()).padStart(2, '0') + ':' + String(time.getMinutes()).padStart(2, '0');
      const showTime = data.chatOpt.timeMode === 'all' || data.chatOpt.timeMode === (mine ? 'me' : 'his');
      const showRead = data.chatOpt.readMode === 'all' || data.chatOpt.readMode === (mine ? 'me' : 'his');
      const meta = '<div class="meta">' +
        (showTime ? '<span class="time">' + timeText + '</span>' : '') +
        (showRead ? '<span class="read">' + (mine ? (m.read ? '已读' : '未读') : '已读') + '</span>' : '') +
        '</div>';
      const quote = m.quote ? '<div class="quote">' + esc(m.quote) + '</div>' : '';
      const head = '<div class="head">' + headHTML(m.side) + '</div>';
      const root = '<div class="msg ' + (mine ? 'me' : 'other') + '" data-i="' + index + '">';

      if (m.type === 'red' || m.type === 'zhuan' || m.type === 'gift') {
        const typeKey = m.type === 'red' && !m.gift ? 'red' : m.type === 'zhuan' ? 'zhuan' : 'gift';
        const ui = cdVal(typeKey);
        const title = m.type === 'zhuan' ? '转账 ¥' + m.amount : m.gift ? '礼物：' + esc(m.gift) : (ui.t || '红包');
        const subtitle = v2TransactionStatus(m);
        return root + head + '<div class="col"><div class="redpack" onclick="openRed(' + index + ')"><span class="icon">' +
          v2TransactionIcon(typeKey, ui.icon) + '</span><div><div class="t">' + title + '</div><div class="s">' + subtitle +
          '</div></div></div>' + meta + '</div></div>';
      }
      if (m.type === 'loc') {
        return root + head + '<div class="col"><div class="bubble" style="padding:4px"><div style="background:#fff;border-radius:14px;padding:10px 12px;min-width:150px"><div style="font-size:12px;color:#6d3b58;font-weight:700">位置</div><div style="font-size:13px;color:#2d252a">' + esc(m.text) + '</div></div></div>' + meta + '</div></div>';
      }
      if (m.type === 'img') {
        const source = v2SafeImage(RS(m.src));
        const image = source ? '<img src="' + esc(source) + '" alt="聊天图片">' : '<div class="hint">图片暂时无法显示</div>';
        return root + head + '<div class="col"><div class="bubble" style="padding:4px;background:none;border:0;box-shadow:none">' + quote + image + '</div>' + meta + '</div></div>';
      }
      return root + head + '<div class="col"><div class="bubble">' + quote + esc(m.text) + '</div>' + meta + '</div></div>';
    }).join('');

    document.querySelectorAll('#chatArea .msg .head').forEach(bindHead);
    requestAnimationFrame(() => {
      if (shouldScroll) area.scrollTop = area.scrollHeight;
      area.classList.remove('noanim');
    });
  };

  sendPoke = function (index) {
    const now = v2Now();
    if (now - v2LastPokeAt < 800) return;
    const role = v2Role();
    const poke = String(role.pokes[index] || '').trim();
    if (!poke) return;
    v2LastPokeAt = now;
    data.lastPoke = index;
    save();
    addSys('你' + poke.split('他').join(displayName()));
    closePanel();
    scheduleReply();
  };

  renderGroups = function () {
    const role = v2Role();
    const box = v2El('groups');
    box.replaceChildren();
    Object.keys(role.cards).forEach(name => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'group-tab' + (name === currentGroup ? ' on' : '');
      button.textContent = name + '(' + role.cards[name].length + ')';
      button.addEventListener('click', () => switchGroup(name));
      box.appendChild(button);
    });
    const add = document.createElement('button');
    add.type = 'button';
    add.className = 'group-tab';
    add.textContent = '+';
    add.setAttribute('aria-label', '新建字卡分组');
    add.addEventListener('click', addGroup);
    box.appendChild(add);
  };

  renderCardList = function () {
    const role = v2Role();
    const keyword = (v2El('searchCard').value || '').trim();
    const source = role.cards[currentGroup] || [];
    const items = source.map((text, index) => ({text, index})).filter(item => !keyword || item.text.includes(keyword));
    const box = v2El('cardList');
    if (!items.length) {
      box.innerHTML = '<div class="hint">暂无字卡</div>';
      return;
    }
    box.replaceChildren();
    items.forEach(item => {
      const row = document.createElement('div');
      row.className = 'card-item';
      const text = document.createElement('span');
      text.className = 'ct';
      text.textContent = item.text;
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = '删除';
      button.addEventListener('click', () => {
        role.cards[currentGroup].splice(item.index, 1);
        save();
        renderGroups();
        renderCardList();
      });
      row.append(text, button);
      box.appendChild(row);
    });
  };

  function v2AddBill(roleKey, entry) {
    const role = v2Role(roleKey);
    role.bill = Array.isArray(role.bill) ? role.bill : [];
    role.bill.push(Object.assign({t: v2Now()}, entry));
    if (role.bill.length > 500) role.bill.shift();
  }

  window.billAdd = function (entry) {
    v2AddBill(data.activeRole, entry);
    save();
  };

  updateWallet = function () {
    const role = v2Role();
    if (v2El('wMyNote')) v2El('wMyNote').textContent = '我的余额 ¥' + Number(role.wallet.mine).toFixed(2).replace(/\.00$/, '');
    if (v2El('wHisNote')) v2El('wHisNote').textContent = '他的余额 ¥' + Number(role.wallet.his).toFixed(2).replace(/\.00$/, '');
    if (v2El('setMyMoney')) v2El('setMyMoney').value = role.wallet.mine;
    if (v2El('setHisMoney')) v2El('setHisMoney').value = role.wallet.his;
  };

  renderBill = function () {
    const box = v2El('billBox');
    if (!box) return;
    const bills = v2Role().bill || [];
    if (!bills.length) {
      box.innerHTML = '<div class="hint">暂无账单</div>';
      return;
    }
    box.innerHTML = bills.slice().reverse().map(item => {
      const time = new Date(item.t);
      const stamp = String(time.getHours()).padStart(2, '0') + ':' + String(time.getMinutes()).padStart(2, '0');
      return '<div class="card-item"><span class="ct">' + (item.from === 'me' ? '我' : '他') + ' → ' +
        (item.to === 'me' ? '我' : '他') + ' · ' + esc(item.type) + (item.status ? '（' + esc(item.status) + '）' : '') +
        '</span><span style="color:#e77d73;font-weight:700">¥' + Number(item.amount || 0).toFixed(2).replace(/\.00$/, '') +
        '</span><small style="color:#a99ca2">' + stamp + '</small></div>';
    }).join('');
  };

  function v2FinishTransaction(roleKey, message, status) {
    const role = v2Role(roleKey);
    if (!role || !message || message.txStatus) return;
    const amount = v2Amount(message);
    const legacy = message.txVersion !== 2;
    if (message.side === 'me') {
      if (status === 'accepted') {
        if (!legacy) role.wallet.his += amount;
      } else if (legacy) {
        role.wallet.his = Math.max(0, role.wallet.his - amount);
        role.wallet.mine += amount;
      } else {
        role.wallet.mine += amount;
      }
    } else if (status === 'accepted') {
      role.wallet.mine += amount;
    } else if (!legacy) {
      role.wallet.his += amount;
    }
    message.txStatus = status;
    message.handled = true;
    message.read = true;
    const type = message.type === 'zhuan' ? '转账' : message.gift ? '礼物：' + message.gift : '红包';
    v2AddBill(roleKey, {
      from: message.side,
      to: message.side === 'me' ? 'his' : 'me',
      type,
      amount,
      status: status === 'accepted' ? '已接收' : '已退还'
    });
    save();
    if (data.activeRole === roleKey) {
      updateWallet();
      renderChat(false);
      renderBill();
    }
  }

  function v2ScheduleHisDecision(roleKey, messageId) {
    setTimeout(() => {
      const role = v2Role(roleKey);
      const message = role?.chat.find(item => item.id === messageId);
      if (!message || message.txStatus || message.handled) return;
      const accepted = Math.random() < 0.7;
      v2FinishTransaction(roleKey, message, accepted ? 'accepted' : 'returned');
      const kind = message.type === 'zhuan' ? '转账' : message.gift ? '礼物' : '红包';
      const roleRef = v2Role(roleKey);
      const roleName = roleRef.nickname || roleRef.name || '他';
      const text = roleName + (accepted ? '已接收' : '已退还') + kind;
      roleRef.chat.push({id: v2Uid('msg'), type: 'sys', text, t: v2Now()});
      const pool = data.recv[message.type === 'zhuan' ? 'zhuan' : message.gift ? 'gift' : 'red'].mine;
      if (accepted && pool.length) {
        setTimeout(() => v2AddMessage(roleKey, 'other', pool[rand(0, pool.length - 1)]), rand(2, 4) * 1000);
      }
      save();
      if (data.activeRole === roleKey) renderChat(false);
    }, rand(3, 6) * 1000);
  }

  hisAuto = function (index) {
    const roleKey = data.activeRole;
    const message = v2Role(roleKey)?.chat[index];
    if (message) v2ScheduleHisDecision(roleKey, message.id);
  };

  function v2SendTransaction(type, amount, details) {
    const roleKey = data.activeRole;
    const role = v2Role(roleKey);
    if (!Number.isFinite(amount) || amount <= 0) { alert('请输入有效金额'); return false; }
    if (amount > role.wallet.mine) { alert('余额不足'); return false; }
    role.wallet.mine -= amount;
    const message = Object.assign({
      id: v2Uid('msg'), side: 'me', type, amount, t: v2Now(), read: false,
      txVersion: 2, txStatus: ''
    }, details || {});
    role.chat.push(message);
    save();
    renderChat(true);
    updateWallet();
    v2ScheduleHisDecision(roleKey, message.id);
    return true;
  }

  giftFromShop = function (category, index) {
    const role = v2Role();
    const gift = role.shop.filter(item => item.cat === category)[index];
    if (!gift) return;
    if (v2SendTransaction('gift', Number(gift.price), {gift: gift.name, price: Number(gift.price)})) {
      toast('礼物已送出');
    }
  };

  plusAction = function (kind) {
    closePlus();
    const role = v2Role();
    if (kind === 'album') {
      pickImage(source => { addMsg('me', {type: 'img', src: source}); scheduleReply(); }, 1000);
      return;
    }
    if (kind === 'loc') {
      showCard({
        av: headHTML('me'), title: '发送位置', sub: '',
        mid: '<input id="cdInput" value="' + esc((data.hisLocs || ['家里'])[0] || '家里') + '" placeholder="位置">',
        buttons: [
          {text: '取消', cls: 'no', fn: function () {}},
          {text: '发送', cls: 'ok', fn: function () {
            const value = v2El('cdInput')?.value.trim();
            if (!value) { alert('请输入位置'); return; }
            addMsg('me', {type: 'loc', text: value});
            scheduleReply();
          }}
        ]
      });
      return;
    }
    const isTransfer = kind === 'zhuan';
    const ui = cdVal(isTransfer ? 'zhuan' : 'red');
    const blessing = data.bless?.length ? data.bless[rand(0, data.bless.length - 1)] : '';
    showCard({
      ci: ui.color,
      av: headHTML('me'),
      title: isTransfer ? '转账' : '发红包',
      sub: isTransfer ? '对方可见金额' : '金额在接收后揭晓',
      mid: '<input id="cdInput" type="number" inputmode="decimal" placeholder="金额（元）">' +
        (isTransfer ? '' : '<input id="cdBless" placeholder="祝福语（可不填）" value="' + esc(blessing) + '">'),
      buttons: [
        {text: '取消', cls: 'no', fn: function () {}},
        {text: '发送', cls: 'ok', fn: function () {
          const amount = Number(v2El('cdInput')?.value);
          const note = isTransfer ? '' : (v2El('cdBless')?.value.trim() || '');
          v2SendTransaction(isTransfer ? 'zhuan' : 'red', amount, {note});
        }}
      ]
    });
  };

  openRed = function (index) {
    if (v2Now() - lastAction < 700) return;
    const roleKey = data.activeRole;
    const role = v2Role(roleKey);
    const message = role.chat[index];
    if (!message || !['red', 'zhuan', 'gift'].includes(message.type)) return;
    const amount = v2Amount(message);
    const key = message.type === 'zhuan' ? 'zhuan' : message.gift ? 'gift' : 'red';
    const ui = cdVal(key);
    const kind = message.type === 'zhuan' ? '转账' : message.gift ? '礼物：' + message.gift : '红包';
    if (message.side === 'me' || message.txStatus || message.handled) {
      showCard({
        ci: ui.color, av: headHTML(message.side), title: (ui.icon || '') + ' ' + kind,
        sub: message.side === 'me' ? '我发出的' : displayName() + '发来的',
        mid: '<div class="c-msg">¥' + amount.toFixed(2).replace(/\.00$/, '') + '</div><div class="c-note">' + v2TransactionStatus(message) + '</div>',
        buttons: [{text: '关闭', cls: 'no', fn: function () {}}]
      });
      return;
    }
    const responsePool = data.recv[key].his || [];
    showCard({
      ci: ui.color, av: headHTML('other'), title: (ui.icon || '') + ' ' + kind,
      sub: displayName() + '发来',
      mid: '<div class="c-msg">' + (message.type === 'red' && !message.gift ? '待揭晓' : '¥' + amount.toFixed(2).replace(/\.00$/, '')) +
        '</div><div class="c-note">接收后将计入你的余额</div>',
      buttons: [
        {text: '退还', cls: 'no', fn: function () {
          v2FinishTransaction(roleKey, message, 'returned');
          const current = v2Role(roleKey);
          current.chat.push({id: v2Uid('msg'), type: 'sys', text: '我已退还' + kind, t: v2Now()});
          save(); renderChat(false);
        }},
        {text: '接收', cls: 'ok', fn: function () {
          v2FinishTransaction(roleKey, message, 'accepted');
          const current = v2Role(roleKey);
          current.chat.push({id: v2Uid('msg'), type: 'sys', text: '我已接收' + kind, t: v2Now()});
          save(); renderChat(false);
          if (responsePool.length) {
            showCard({
              av: headHTML('me'), title: '回复' + displayName(), sub: '',
              mid: '<input id="cdInput" placeholder="写一句回复…" value="' + esc(responsePool[0]) + '">',
              buttons: [
                {text: '跳过', cls: 'no', fn: function () {}},
                {text: '发送', cls: 'ok', fn: function () {
                  const value = v2El('cdInput')?.value.trim();
                  if (value) { addMsg('me', value); scheduleReply(); }
                }}
              ]
            });
          }
        }}
      ]
    });
  };

  heSendRandom = function () {
    const roleKey = data.activeRole;
    const role = v2Role(roleKey);
    const sendConfiguredCard = () => {
      const pool = v2AllCards(role).map(item => String(item || '').trim()).filter(Boolean);
      if (!pool.length) return false;
      v2AddMessage(roleKey, 'other', pool[rand(0, pool.length - 1)]);
      return true;
    };
    const sendBalanceFallback = type => {
      const text = String(role.balanceFallback?.[type] || '').trim();
      if (!text) return false;
      v2AddMessage(roleKey, 'other', text);
      return true;
    };
    const kind = rand(0, 6);
    if (kind === 0) {
      sendConfiguredCard();
      return;
    }
    if (kind === 1) {
      if (data.emoji?.length) v2AddMessage(roleKey, 'other', data.emoji[rand(0, data.emoji.length - 1)]);
      else sendConfiguredCard();
      return;
    }
    if (kind === 2) {
      if (data.stickers?.length) v2AddMessage(roleKey, 'other', {type: 'img', src: data.stickers[rand(0, data.stickers.length - 1)]});
      else sendConfiguredCard();
      return;
    }
    if (kind === 6) {
      const locations = (data.hisLocs || []).filter(Boolean);
      if (locations.length) v2AddMessage(roleKey, 'other', {type: 'loc', text: locations[rand(0, locations.length - 1)]});
      else sendConfiguredCard();
      return;
    }
    const type = kind === 3 ? 'red' : kind === 5 ? 'zhuan' : 'gift';
    if (role.wallet.his <= 0) {
      if (type === 'red' || type === 'zhuan') sendBalanceFallback(type);
      else sendConfiguredCard();
      return;
    }
    let details = {};
    let amount;
    if (type === 'gift') {
      const affordable = role.shop.filter(item => Number(item.price) > 0 && Number(item.price) <= role.wallet.his);
      if (!affordable.length) { sendConfiguredCard(); return; }
      const gift = affordable[rand(0, affordable.length - 1)];
      amount = Number(gift.price);
      details = {gift: gift.name, price: amount};
    } else {
      amount = Math.min(role.wallet.his, rand(type === 'red' ? 5 : 10, type === 'red' ? 200 : 500));
      amount = Math.max(0.01, Math.round(amount * 100) / 100);
    }
    role.wallet.his -= amount;
    role.chat.push(Object.assign({
      id: v2Uid('msg'), side: 'other', type, amount, t: v2Now(), read: true,
      txVersion: 2, txStatus: ''
    }, details));
    save();
    renderChat(false);
    updateWallet();
    if (data.sound.onRecv) playSound(data.sound.type);
  };

  renderCardTheme = function () {
    const row = v2El('cardThemeRow');
    if (!row) return;
    const selected = Number(data.cardUi?.red?.color || 0);
    row.innerHTML = cardColors().map((colors, index) => '<button class="theme-btn ' + (index === selected ? 'on' : '') +
      '" style="background:linear-gradient(135deg,' + colors[0] + ',' + colors[1] + ');color:#fff;border:none" onclick="setCardTheme(' +
      index + ',this)">配色 ' + (index + 1) + '</button>').join('');
  };

  function v2SaveSettings() {
    const role = v2Role();
    role.name = v2El('setName').value.trim() || '他';
    role.nickname = v2El('setNick').value.trim();
    role.avatar = v2El('setAvatar').value.trim() || '🧑';
    role.myName = v2El('setMyName').value.trim() || '我';
    role.myAvatar = v2El('setMyAvatar').value.trim() || '我';

    let delayMin = v2ClampNumber(v2El('setDelayMin').value, 2, 1, 3600);
    let delayMax = v2ClampNumber(v2El('setDelayMax').value, 7, 1, 3600);
    let replyMin = v2ClampNumber(v2El('setReplyMin').value, 1, 1, 20);
    let replyMax = v2ClampNumber(v2El('setReplyMax').value, 2, 1, 20);
    let activeMin = v2ClampNumber(v2El('setActiveMin').value, 300, 10, 86400);
    let activeMax = v2ClampNumber(v2El('setActiveMax').value, 1800, 10, 86400);
    if (delayMin > delayMax) [delayMin, delayMax] = [delayMax, delayMin];
    if (replyMin > replyMax) [replyMin, replyMax] = [replyMax, replyMin];
    if (activeMin > activeMax) [activeMin, activeMax] = [activeMax, activeMin];
    data.reply = {
      delayMin, delayMax, replyMin, replyMax,
      gap: v2ClampNumber(v2El('setGap').value, 2, 1, 60),
      ignoreRate: v2ClampNumber(data.reply.ignoreRate, 20, 0, 100),
      active: v2El('setActive').checked,
      activeMin, activeMax,
      statusGap: 600,
      enterSend: v2El('setEnterSend').checked
    };
    data.chatOpt = {
      timeMode: v2El('setTimeMode').value,
      readMode: v2El('setReadMode').value,
      hisIgnore: v2El('setHisIgnore').checked
    };
    data.sim = {
      bioClock: v2El('setBioClock').checked,
      recall: v2El('setRecall').checked,
      festival: v2El('setFestival').checked,
      autoNight: v2El('setAutoNight').checked,
      recallReact: v2El('setRecallReact').checked,
      weekly: v2El('setWeekly').checked
    };
    data.theme = {
      myBg: v2El('setMyBg').value,
      myText: v2El('setMyText').value,
      hisBg: v2El('setHisBg').value,
      hisText: v2El('setHisText').value
    };
    data.font = {
      name: v2El('setFont').value,
      size: v2ClampNumber(v2El('setFontSize').value, 15, 12, 24),
      radius: v2ClampNumber(v2El('setRadius').value, 18, 0, 28),
      topSkin: v2El('setTopSkin').checked
    };
    data.sound = {
      type: data.sound.type,
      volume: v2ClampNumber(v2El('setVolume').value, 50, 0, 100),
      onRecv: v2El('setSoundRecv').checked,
      onSend: v2El('setSoundSend').checked
    };
    save();
    applyAll();
    toast('设置已保存');
  }

  v2El('btnSave').onclick = v2SaveSettings;
  v2El('btnAddRole').onclick = () => {
    const name = prompt('新陪伴角色的名字：');
    if (!name?.trim()) return;
    const id = 'r' + Date.now();
    data.roles[id] = {
      id,
      name: name.trim(), nickname: '', avatar: '🧑', myName: '我', myAvatar: '我',
      wallet: {his: 100, mine: 100}, cards: v2StarterCards(), statuses: ['想你'], statusNow: '想你',
      pokes: ['拍了拍他的头'], shop: [], letters: [], chat: [], bill: [], moments: [],
      weekly: {key: '', date: '', stats: null}
    };
    data.activeRole = id;
    save();
    applyAll();
    toast('新角色已创建');
  };

  startVideo = function () {
    if (callActive()) return;
    closePlus();
    playSound('soft');
    v2El('videoScreen').style.display = 'flex';
    v2El('vAvatar').innerHTML = headHTML('other');
    const background = v2SafeImage(RS(data.videoBg));
    v2El('videoScreen').style.background = background ? 'url("' + background.replace(/"/g, '%22') + '") center/cover no-repeat' : '';
    videoSec = 0;
    v2El('videoTime').textContent = '00:00:00';
    clearInterval(videoTimer);
    videoTimer = setInterval(() => {
      videoSec++;
      const text = String(Math.floor(videoSec / 3600)).padStart(2, '0') + ':' +
        String(Math.floor(videoSec / 60) % 60).padStart(2, '0') + ':' + String(videoSec % 60).padStart(2, '0');
      v2El('videoTime').textContent = text;
      v2El('mTime').textContent = text;
    }, 1000);
  };

  v2El('videoMini').onclick = event => {
    if (!event.target.closest('#btnMiniHang')) restoreVideo();
  };
  v2El('btnMiniHang').onclick = event => {
    event.stopPropagation();
    hangUp();
  };

  function v2Download(name, content, type) {
    const mime = type || 'text/plain;charset=utf-8';
    const blob = content instanceof Blob ? content : new Blob([content], {type: mime});
    if (window.AndroidBridge?.saveFile) {
      toast('正在保存…');
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = String(reader.result || '').split(',')[1] || '';
        window.AndroidBridge.saveFile(name, base64, mime);
      };
      reader.readAsDataURL(blob);
      return;
    }
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  download = v2Download;
  window.notifyNativeSave = success => toast(success ? '文件已保存' : '保存失败');

  function v2PortableClone(value) {
    if (typeof value === 'string' && value.startsWith('idb:')) return IDB_CACHE[value.slice(4)] || '';
    if (Array.isArray(value)) return value.map(v2PortableClone);
    if (value && typeof value === 'object') {
      const copy = {};
      Object.keys(value).forEach(key => { copy[key] = v2PortableClone(value[key]); });
      return copy;
    }
    return value;
  }

  v2El('btnBackup').onclick = () => {
    const portable = v2PortableClone(data);
    v2Download('Icepear-数据备份-' + new Date().toISOString().slice(0, 10) + '.json', JSON.stringify(portable, null, 2), 'application/json;charset=utf-8');
  };
  v2El('btnExportChat').onclick = () => {
    const text = v2Role().chat.map(message => {
      if (message.type === 'sys') return '[系统] ' + message.text;
      if (message.type === 'img') return (message.side === 'me' ? '我' : displayName()) + '：[图片]';
      if (message.type === 'loc') return (message.side === 'me' ? '我' : displayName()) + '：[位置] ' + message.text;
      return (message.side === 'me' ? '我' : displayName()) + '：' + (message.text || '[' + message.type + ']');
    }).join('\n');
    v2Download('Icepear-聊天记录.txt', text, 'text/plain;charset=utf-8');
  };
  v2El('btnSaveCloud').onclick = () => {
    const canvas = v2El('wordcloud');
    canvas.toBlob(blob => {
      if (blob) v2Download('Icepear-聊天词云.png', blob, 'image/png');
    }, 'image/png');
  };

  function v2StickerImage(source, index, removable) {
    const safe = v2SafeImage(RS(source));
    if (!safe) return null;
    const image = document.createElement('img');
    image.src = safe;
    image.alt = '表情图片';
    image.loading = 'lazy';
    if (!removable) image.addEventListener('click', () => sendSticker(index));
    return image;
  }

  renderStickers = function () {
    const box = v2El('stickerTab');
    if (!box) return;
    box.replaceChildren();
    const frequencies = {};
    Object.entries(data.stFreq || {}).forEach(([key, value]) => {
      const index = Number(key);
      if (data.stickers[index]) frequencies[index] = Number(value) || 0;
    });
    const common = Object.keys(frequencies).map(Number).sort((a, b) => frequencies[b] - frequencies[a]).slice(0, 6);
    const sections = [
      ['常用', common, '点过、发过的表情会显示在这里'],
      ['全部', data.stickers.map((_, index) => index), '暂无表情包']
    ];
    sections.forEach(([title, indexes, empty]) => {
      const heading = document.createElement('h4');
      heading.textContent = title;
      heading.style.cssText = 'font-size:13px;color:var(--muted-ink);margin:10px 0 8px';
      const grid = document.createElement('div');
      grid.className = 'emoji-grid';
      indexes.forEach(index => {
        const image = v2StickerImage(data.stickers[index], index, false);
        if (image) grid.appendChild(image);
      });
      if (!grid.childElementCount) {
        const note = document.createElement('div');
        note.className = 'hint';
        note.style.width = '100%';
        note.textContent = empty;
        grid.appendChild(note);
      }
      box.append(heading, grid);
    });
  };

  renderStickerManage = function () {
    const box = v2El('stickerManage');
    if (!box) return;
    box.replaceChildren();
    data.stickers.forEach((source, index) => {
      const image = v2StickerImage(source, index, true);
      if (!image) return;
      const wrapper = document.createElement('div');
      wrapper.style.cssText = 'position:relative;display:inline-block;margin:4px';
      image.style.cssText = 'width:58px;height:58px;object-fit:cover;border-radius:16px;border:1px solid var(--line)';
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = '×';
      remove.setAttribute('aria-label', '删除表情');
      remove.style.cssText = 'position:absolute;top:-7px;right:-7px;background:#b84242;color:#fff;border:0;border-radius:50%;width:24px;height:24px;min-height:24px;padding:0';
      remove.addEventListener('click', () => delSticker(index));
      wrapper.append(image, remove);
      box.appendChild(wrapper);
    });
    if (!box.childElementCount) box.innerHTML = '<div class="hint">暂无表情包</div>';
  };

  delSticker = function (index) {
    if (!confirm('删除这个表情？')) return;
    data.stickers.splice(index, 1);
    const shifted = {};
    Object.keys(data.stFreq || {}).forEach(key => {
      const old = Number(key);
      if (old < index) shifted[old] = data.stFreq[key];
      if (old > index) shifted[old - 1] = data.stFreq[key];
    });
    data.stFreq = shifted;
    data.freq = Object.fromEntries(Object.entries(data.freq || {}).filter(([key]) => !key.startsWith('st')));
    save();
    renderStickers();
    renderStickerManage();
  };

  renderMemos = function () {
    const box = v2El('memoList');
    if (!data.memos.length) { box.innerHTML = '<div class="hint">暂无纪念日</div>'; return; }
    box.innerHTML = data.memos.map((memo, index) => {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const target = new Date(memo.date); target.setHours(0, 0, 0, 0);
      const days = Number.isFinite(target.getTime()) ? Math.round((today - target) / 86400000) : 0;
      const label = memo.type === 'countup'
        ? (days >= 0 ? '已经 ' + days + ' 天' : '还有 ' + (-days) + ' 天开始')
        : (days < 0 ? '还有 ' + (-days) + ' 天' : '已经过去 ' + days + ' 天');
      const background = v2SafeImage(RS(memo.bg));
      const style = background ? 'background-image:linear-gradient(rgba(255,250,247,.72),rgba(255,250,247,.72)),url(&quot;' + esc(background) + '&quot;)' : '';
      return '<div class="memo-item" style="' + style + '"><div class="mt">' + esc(memo.name) + '</div><div class="md">' +
        esc(memo.date) + ' · ' + label + '</div><div class="btns"><button style="background:#6d3b58;color:#fff" onclick="pickMemoBg(' + index + ')">换背景</button>' +
        '<button style="background:#feeaea;color:#b84242" onclick="delMemo(' + index + ')">删除</button></div></div>';
    }).join('');
  };

  updateWeekly = function () {
    const role = v2Role();
    const week = Math.floor(v2Now() / 6048e5);
    if (role.weekly?.key === week && role.weekly.stats) return;
    const stats = {meMsgs: 0, hisMsgs: 0, red: 0, zhuan: 0, gift: 0, loc: 0, pokes: 0};
    const start = v2Now() - 6048e5;
    role.chat.forEach(message => {
      if (message.t < start) return;
      if (message.type === 'sys') {
        if (String(message.text).includes('拍了拍')) stats.pokes++;
        return;
      }
      if (message.side === 'me') stats.meMsgs++; else stats.hisMsgs++;
      if (Object.prototype.hasOwnProperty.call(stats, message.type)) stats[message.type]++;
    });
    role.weekly = {key: week, date: new Date().toLocaleDateString(), stats};
    save();
  };

  renderWeekly = function () {
    updateWeekly();
    const weekly = v2Role().weekly;
    const stats = weekly.stats;
    const cards = [
      ['我发消息', stats.meMsgs], ['他发消息', stats.hisMsgs], ['红包', stats.red], ['转账', stats.zhuan],
      ['礼物', stats.gift], ['定位', stats.loc], ['拍一拍', stats.pokes]
    ];
    v2El('weeklyBody').innerHTML = '<div class="rpt"><h3>本周互动</h3><div class="rdate">' + esc(weekly.date) + ' 更新</div><div class="rpt-grid">' +
      cards.map(([label, value]) => '<div class="rpt-cell"><b>' + value + '</b><span>' + label + '</span></div>').join('') +
      '</div><div class="rpt-note">数据只统计当前陪伴角色最近 7 天的互动。</div></div>';
  };

  renderMoments = function () {
    const role = v2Role();
    const pool = v2AllCards(role);
    if (!pool.length) { v2El('momentsBody').innerHTML = '<div class="hint">先添加字卡，就能生成动态内容。</div>'; return; }
    if (!role.moments.length) {
      [1, 3, 8, 14, 26, 40].forEach(hours => role.moments.push({
        id: v2Uid('moment'), text: pool[rand(0, pool.length - 1)], t: v2Now() - hours * 3600000,
        likes: rand(0, 12), comments: []
      }));
      save();
    }
    v2El('momentsBody').innerHTML = role.moments.map((moment, index) => {
      const date = new Date(moment.t);
      const stamp = (date.getMonth() + 1) + '-' + date.getDate() + ' ' + String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
      return '<div class="card-item" style="align-items:flex-start"><div style="width:42px;height:42px;border-radius:14px;background:#ead7df;display:flex;align-items:center;justify-content:center;overflow:hidden;flex-shrink:0">' +
        headHTML('other') + '</div><div style="flex:1;min-width:0"><div style="font-weight:750">' + esc(displayName()) +
        '</div><div style="font-size:14px;margin:8px 0 10px;line-height:1.65">' + esc(moment.text) +
        '</div><div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;font-size:11px;color:#8d7d85"><span>' + stamp + '</span><span>喜欢 ' +
        moment.likes + '</span><button onclick="likeMoment(' + index + ')">喜欢</button><button onclick="commentMoment(' + index + ')">评论</button></div>' +
        (moment.comments.length ? '<div style="background:#f2eae6;border-radius:12px;padding:8px 10px;margin-top:8px;font-size:12px">' +
          moment.comments.map(comment => '<div><b>我：</b>' + esc(comment) + '</div>').join('') + '</div>' : '') + '</div></div>';
    }).join('');
  };

  likeMoment = function (index) {
    const roleKey = data.activeRole;
    const role = v2Role(roleKey);
    if (!role.moments[index]) return;
    role.moments[index].likes++;
    save();
    renderMoments();
    toast('已喜欢');
  };

  commentMoment = function (index) {
    const text = prompt('写下评论：');
    if (!text?.trim()) return;
    const role = v2Role();
    if (!role.moments[index]) return;
    role.moments[index].comments.push(text.trim());
    save();
    renderMoments();
  };

  v2Migrate();
  v2CreateNav();
  clearTimeout(replyTimer);
  v2El('topbar')?.setAttribute('role', 'banner');
  v2El('chatArea')?.setAttribute('aria-live', 'polite');
  v2El('textInput')?.setAttribute('aria-label', '消息内容');
  v2El('btnMenu')?.setAttribute('aria-label', '功能中心');
  v2El('btnTopSkin')?.setAttribute('aria-label', '切换深色模式');
  v2El('btnEmoji')?.setAttribute('aria-label', '表情');
  v2El('btnPlus')?.setAttribute('aria-label', '更多功能');

  applyAll();
  v2EnhanceStructureIcons();
  v2SyncNavigation('pageChat');
})();
