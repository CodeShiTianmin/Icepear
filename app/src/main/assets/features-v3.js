(function () {
  'use strict';
  document.documentElement.dataset.icepearVersion = '2.3.0-loading';

  const el = id => document.getElementById(id);
  const nowId = prefix => (prefix || 'id') + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
  const safeMedia = value => {
    const source = String(value || '');
    if (/^data:(?:audio|image)\/[a-z0-9.+-]+;base64,/i.test(source)) return source;
    if (/^blob:/i.test(source)) return source;
    if (/^https:\/\//i.test(source)) return source;
    return '';
  };

  data.icepearFeatures = data.icepearFeatures && typeof data.icepearFeatures === 'object' ? data.icepearFeatures : {};
  if (!data.icepearFeatures.messageMetaEnabled) {
    data.chatOpt = data.chatOpt || {};
    data.chatOpt.timeMode = 'all';
    data.chatOpt.readMode = 'all';
    data.icepearFeatures.messageMetaEnabled = true;
    save();
  }

  /* ---------- 应用内弹窗 ---------- */
  let dialogCancel = null;

  function ensureDialog() {
    let root = el('appDialog');
    if (root) return root;
    root = document.createElement('div');
    root.id = 'appDialog';
    root.setAttribute('role', 'dialog');
    root.setAttribute('aria-modal', 'true');
    root.innerHTML =
      '<section class="app-dialog-card">' +
        '<header class="app-dialog-head"><div class="app-dialog-icon" aria-hidden="true"></div><div><div class="app-dialog-title"></div><div class="app-dialog-subtitle"></div></div></header>' +
        '<div class="app-dialog-body"></div><div class="app-dialog-actions"></div>' +
      '</section>';
    root.addEventListener('click', event => {
      if (event.target === root && dialogCancel) dialogCancel();
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && root.classList.contains('open') && dialogCancel) dialogCancel();
    });
    document.body.appendChild(root);
    return root;
  }

  function closeDialog() {
    const root = el('appDialog');
    if (!root) return;
    root.classList.remove('open');
    dialogCancel = null;
  }

  function openDialog(options) {
    const config = options || {};
    const root = ensureDialog();
    const icon = root.querySelector('.app-dialog-icon');
    const title = root.querySelector('.app-dialog-title');
    const subtitle = root.querySelector('.app-dialog-subtitle');
    const body = root.querySelector('.app-dialog-body');
    const actions = root.querySelector('.app-dialog-actions');
    icon.classList.toggle('has-svg', Boolean(config.iconSvg));
    if (config.iconSvg) icon.innerHTML = config.iconSvg;
    else icon.textContent = config.icon || '✦';
    title.textContent = config.title || '提示';
    subtitle.textContent = config.subtitle || '';
    subtitle.style.display = config.subtitle ? '' : 'none';
    body.replaceChildren();
    actions.replaceChildren();

    if (config.message) {
      const message = document.createElement('div');
      message.style.cssText = 'color:var(--muted-ink);font-size:14px;line-height:1.75;white-space:pre-wrap';
      message.textContent = config.message;
      body.appendChild(message);
    }

    const fields = Array.isArray(config.fields) ? config.fields : [];
    fields.forEach(field => {
      const wrap = document.createElement('div');
      wrap.className = 'app-dialog-field';
      const label = document.createElement('label');
      label.textContent = field.label || '';
      label.htmlFor = 'app-field-' + field.name;
      let input;
      if (field.type === 'textarea') input = document.createElement('textarea');
      else if (field.type === 'select') {
        input = document.createElement('select');
        (field.options || []).forEach(option => {
          const node = document.createElement('option');
          node.value = option.value;
          node.textContent = option.label;
          input.appendChild(node);
        });
      } else {
        input = document.createElement('input');
        input.type = field.type || 'text';
      }
      input.id = 'app-field-' + field.name;
      input.dataset.field = field.name;
      input.value = field.value == null ? '' : String(field.value);
      if (field.placeholder) input.placeholder = field.placeholder;
      if (field.maxLength) input.maxLength = field.maxLength;
      if (field.inputMode) input.inputMode = field.inputMode;
      wrap.append(label, input);
      body.appendChild(wrap);
    });

    const error = document.createElement('div');
    error.className = 'app-dialog-error';
    body.appendChild(error);

    const cancel = () => {
      closeDialog();
      if (typeof config.onCancel === 'function') config.onCancel();
    };
    dialogCancel = config.cancelable === false ? null : cancel;

    if (config.cancelable !== false) {
      const cancelButton = document.createElement('button');
      cancelButton.type = 'button';
      cancelButton.className = 'app-dialog-cancel';
      cancelButton.textContent = config.cancelText || '取消';
      cancelButton.addEventListener('click', cancel);
      actions.appendChild(cancelButton);
    }

    const confirmButton = document.createElement('button');
    confirmButton.type = 'button';
    confirmButton.className = 'app-dialog-confirm' + (config.danger ? ' danger' : '');
    confirmButton.textContent = config.confirmText || '确定';
    confirmButton.addEventListener('click', () => {
      const values = {};
      for (const field of fields) {
        const input = el('app-field-' + field.name);
        values[field.name] = input ? input.value : '';
        if (field.required && !String(values[field.name]).trim()) {
          error.textContent = (field.label || '此项') + '不能为空';
          input?.focus();
          return;
        }
      }
      if (typeof config.validate === 'function') {
        const validation = config.validate(values);
        if (validation) {
          error.textContent = validation;
          return;
        }
      }
      const result = typeof config.onConfirm === 'function' ? config.onConfirm(values) : undefined;
      if (result !== false) closeDialog();
    });
    actions.appendChild(confirmButton);
    actions.classList.toggle('single', config.cancelable === false);
    root.classList.add('open');
    setTimeout(() => root.querySelector('input,textarea,select,.app-dialog-confirm')?.focus(), 80);
  }

  window.appNotice = options => openDialog(Object.assign({cancelable: false, confirmText: '知道了'}, options));
  window.appConfirm = options => openDialog(Object.assign({icon: '？', danger: false}, options));
  window.appPrompt = options => {
    const config = Object.assign({}, options || {});
    const onConfirm = config.onConfirm;
    config.fields = [{
      name: 'value', label: config.label || config.title || '内容', value: config.value || '',
      placeholder: config.placeholder || '', required: config.required !== false, maxLength: config.maxLength
    }];
    config.onConfirm = values => onConfirm?.(values.value);
    openDialog(config);
  };
  window.appForm = openDialog;

  const priorAndroidBack = window.handleAndroidBack;
  window.handleAndroidBack = function () {
    if (el('appDialog')?.classList.contains('open')) {
      closeDialog();
      return true;
    }
    if (el('messageContextMenu')) {
      closeMessageMenu();
      return true;
    }
    if (messageSelection.active) {
      exitMessageSelection();
      return true;
    }
    return typeof priorAndroidBack === 'function' ? priorAndroidBack() : false;
  };

  /* ---------- 微信式消息浮动菜单 ---------- */
  const messageSelection = {active: false, selected: new Set()};
  const messageIcons = {
    copy: '<svg viewBox="0 0 24 24"><rect x="8" y="8" width="11" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h2"/></svg>',
    forward: '<svg viewBox="0 0 24 24"><path d="m14 5 6 6-6 6"/><path d="M20 11H9a5 5 0 0 0-5 5v2"/></svg>',
    favorite: '<svg viewBox="0 0 24 24"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z"/></svg>',
    delete: '<svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3m3 0-1 14H7L6 7m4 4v6m4-6v6"/></svg>',
    multi: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="5" height="5" rx="1"/><path d="m4.5 6.5 1.2 1.2L8 5.5M11 6.5h10"/><rect x="3" y="15" width="5" height="5" rx="1"/><path d="m4.5 17.5 1.2 1.2L8 16.5M11 17.5h10"/></svg>',
    quote: '<svg viewBox="0 0 24 24"><path d="M9 10H5a4 4 0 0 1 4-4v8H5v-4m14 0h-4a4 4 0 0 1 4-4v8h-4v-4"/></svg>',
    remind: '<svg viewBox="0 0 24 24"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>',
    translate: '<svg viewBox="0 0 24 24"><path d="M4 5h8M8 3v2m3 0c-.8 3.5-3.1 6.4-7 8m2-5c1.1 2 2.7 3.6 5 5m3-7h3l4 14m-6-4h5"/></svg>',
    search: '<svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/></svg>',
    read: '<svg viewBox="0 0 24 24"><path d="M5 9v6h4l5 4V5L9 9zM17 9a4 4 0 0 1 0 6m2.5-8.5a7.5 7.5 0 0 1 0 11"/></svg>',
    recall: '<svg viewBox="0 0 24 24"><path d="M9 7H4V2"/><path d="M4.6 7.1A9 9 0 1 1 3 15"/></svg>'
  };

  function messageText(message) {
    if (!message) return '';
    if (message.recall) return '已撤回的消息';
    if (message.type === 'img') return '[图片]';
    if (message.type === 'loc') return '[位置] ' + (message.text || '');
    if (message.type === 'red') return message.gift ? '[礼物] ' + message.gift : '[红包] ¥' + (message.amount || 0);
    if (message.type === 'zhuan') return '[转账] ¥' + (message.amount || 0);
    if (message.type === 'gift') return '[礼物] ' + (message.gift || '');
    return String(message.text || '');
  }

  function closeMessageMenu() {
    el('messageContextMenu')?.remove();
    el('messageContextBackdrop')?.remove();
    el('actionPanel').style.display = 'none';
    el('overlay').style.display = 'none';
    actionIdx = null;
  }

  async function copyMessageText(text, successText) {
    const value = String(text || '');
    if (!value) return false;
    if (window.AndroidBridge?.copyText) {
      window.AndroidBridge.copyText(value);
      toast(successText || '已复制');
      return true;
    }
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(value);
      else throw new Error('clipboard unavailable');
    } catch (_) {
      const area = document.createElement('textarea');
      area.value = value;
      area.style.cssText = 'position:fixed;opacity:0;pointer-events:none';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
    }
    toast(successText || '已复制');
    return true;
  }

  async function forwardMessage(message) {
    const text = messageText(message);
    closeMessageMenu();
    if (!text) return;
    if (window.AndroidBridge?.shareText) {
      window.AndroidBridge.shareText(text);
      return;
    }
    try {
      if (navigator.share) {
        await navigator.share({title: 'Icepear 消息', text});
        return;
      }
    } catch (error) {
      if (error?.name === 'AbortError') return;
    }
    await copyMessageText(text, '已复制，可粘贴转发');
  }

  function toggleFavorite(message) {
    message.favorite = !message.favorite;
    save();
    toast(message.favorite ? '已收藏这条消息' : '已取消收藏');
    closeMessageMenu();
  }

  function deleteMessage(index) {
    const message = R().chat[index];
    closeMessageMenu();
    if (!message) return;
    appConfirm({
      icon: '⌫', title: '删除这条消息？', subtitle: '删除后无法恢复', confirmText: '删除', danger: true,
      onConfirm: () => {
        R().chat.splice(index, 1);
        save();
        renderChat(false);
      }
    });
  }

  function scheduleStoredReminder(reminder) {
    const wait = Math.max(0, reminder.at - Date.now());
    if (wait > 2147483000 || reminder.done) return;
    clearTimeout(reminder._timer);
    reminder._timer = setTimeout(() => {
      if (reminder.done) return;
      const role = data.roles[reminder.roleId];
      if (!role) return;
      reminder.done = true;
      role.chat.push({type: 'sys', text: '提醒：' + reminder.text, t: Date.now()});
      save();
      if (data.activeRole === reminder.roleId) {
        renderChat(true);
        toast('消息提醒已到时间');
      }
    }, wait);
  }

  function remindMessage(message) {
    const text = messageText(message);
    closeMessageMenu();
    appForm({
      icon: '⏱', title: '提醒我', subtitle: text.slice(0, 46), confirmText: '设定提醒',
      fields: [{
        name: 'minutes', label: '提醒时间', type: 'select', value: '30',
        options: [
          {value: '10', label: '10 分钟后'}, {value: '30', label: '30 分钟后'},
          {value: '60', label: '1 小时后'}, {value: '1440', label: '明天此时'}
        ]
      }],
      onConfirm: values => {
        data.messageReminders = Array.isArray(data.messageReminders) ? data.messageReminders : [];
        const reminder = {
          id: nowId('reminder'), roleId: data.activeRole, text: text || '查看这条消息',
          at: Date.now() + Number(values.minutes || 30) * 60000, done: false
        };
        data.messageReminders.push(reminder);
        save();
        scheduleStoredReminder(reminder);
        toast('提醒已设定');
      }
    });
  }

  function translateMessage(message) {
    const text = messageText(message);
    closeMessageMenu();
    if (!text || text.startsWith('[')) {
      appNotice({icon: '译', title: '这类消息无法翻译', message: '请选择一条文字消息。'});
      return;
    }
    appConfirm({
      icon: '译', title: '使用在线翻译？',
      subtitle: '将在系统浏览器中打开翻译服务', confirmText: '继续',
      onConfirm: () => {
        const url = 'https://translate.google.com/?sl=auto&tl=zh-CN&text=' + encodeURIComponent(text) + '&op=translate';
        window.location.href = url;
      }
    });
  }

  function searchMessage(message) {
    const text = messageText(message).replace(/^\[[^\]]+\]\s*/, '').trim();
    closeMessageMenu();
    goPage('pageSearch');
    const input = el('searchChat');
    if (input) {
      input.value = text;
      renderSearch();
      input.focus();
    }
  }

  function readMessagesFrom(index) {
    closeMessageMenu();
    if (!('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
      appNotice({icon: '声', title: '当前设备不支持朗读', message: '请更新 Android System WebView 后重试。'});
      return;
    }
    const lines = R().chat.slice(index).filter(message => !message.recall).map(messageText).filter(Boolean).slice(0, 20);
    if (!lines.length) return;
    speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(lines.join('。'));
    utterance.lang = 'zh-CN';
    utterance.rate = 0.96;
    speechSynthesis.speak(utterance);
    toast('开始连续朗读');
  }

  function ensureSelectionBar() {
    let bar = el('messageMultiBar');
    if (bar) return bar;
    bar = document.createElement('div');
    bar.id = 'messageMultiBar';
    bar.setAttribute('role', 'toolbar');
    bar.setAttribute('aria-label', '多选消息操作');
    bar.innerHTML =
      '<button type="button" data-multi="cancel">取消</button>' +
      '<span class="message-multi-count">已选 0 条</span>' +
      '<button type="button" data-multi="forward">转发</button>' +
      '<button type="button" class="danger" data-multi="delete">删除</button>';
    el('pageChat').insertBefore(bar, el('inputBar'));
    bar.addEventListener('click', event => {
      const action = event.target.closest('button')?.dataset.multi;
      if (action === 'cancel') exitMessageSelection();
      if (action === 'forward') forwardSelectedMessages();
      if (action === 'delete') deleteSelectedMessages();
    });
    return bar;
  }

  function syncMessageSelection() {
    if (!messageSelection.active) return;
    const count = ensureSelectionBar().querySelector('.message-multi-count');
    count.textContent = '已选 ' + messageSelection.selected.size + ' 条';
    document.querySelectorAll('#chatArea [data-i]').forEach(node => {
      const index = Number(node.dataset.i);
      node.classList.add('message-selectable');
      node.classList.toggle('selected', messageSelection.selected.has(index));
      node.setAttribute('aria-selected', String(messageSelection.selected.has(index)));
      if (!node.querySelector('.message-select-check')) {
        const check = document.createElement('button');
        check.type = 'button';
        check.className = 'message-select-check';
        check.setAttribute('aria-label', '选择这条消息');
        check.innerHTML = '<svg viewBox="0 0 24 24"><path d="m6 12 4 4 8-8"/></svg>';
        node.appendChild(check);
      }
    });
  }

  function enterMessageSelection(index) {
    closeMessageMenu();
    messageSelection.active = true;
    messageSelection.selected.clear();
    messageSelection.selected.add(index);
    document.body.classList.add('message-selecting');
    el('pageChat').classList.add('message-selecting');
    ensureSelectionBar();
    syncMessageSelection();
  }

  function exitMessageSelection() {
    messageSelection.active = false;
    messageSelection.selected.clear();
    document.body.classList.remove('message-selecting');
    el('pageChat').classList.remove('message-selecting');
    el('messageMultiBar')?.remove();
    document.querySelectorAll('#chatArea .message-selectable').forEach(node => {
      node.classList.remove('message-selectable', 'selected');
      node.removeAttribute('aria-selected');
      node.querySelector('.message-select-check')?.remove();
    });
  }

  async function forwardSelectedMessages() {
    const text = [...messageSelection.selected].sort((a, b) => a - b).map(index => messageText(R().chat[index])).filter(Boolean).join('\n');
    if (!text) return;
    if (window.AndroidBridge?.shareText) {
      window.AndroidBridge.shareText(text);
      return;
    }
    try {
      if (navigator.share) {
        await navigator.share({title: 'Icepear 聊天消息', text});
        return;
      }
    } catch (error) {
      if (error?.name === 'AbortError') return;
    }
    await copyMessageText(text, '已复制，可粘贴转发');
  }

  function deleteSelectedMessages() {
    const indexes = [...messageSelection.selected].sort((a, b) => b - a);
    if (!indexes.length) return;
    appConfirm({
      icon: '⌫', title: '删除选中的消息？', subtitle: '共 ' + indexes.length + ' 条，删除后无法恢复',
      confirmText: '全部删除', danger: true,
      onConfirm: () => {
        indexes.forEach(index => R().chat.splice(index, 1));
        save();
        exitMessageSelection();
        renderChat(false);
      }
    });
  }

  el('chatArea').addEventListener('click', event => {
    if (!messageSelection.active) return;
    const node = event.target.closest('[data-i]');
    if (!node) return;
    event.preventDefault();
    event.stopPropagation();
    const index = Number(node.dataset.i);
    if (messageSelection.selected.has(index)) messageSelection.selected.delete(index);
    else messageSelection.selected.add(index);
    lastTap = {i: -1, t: 0};
    syncMessageSelection();
  }, true);

  function messageMenuActions(message, index) {
    const actions = [];
    const text = messageText(message);
    if (text) actions.push({id: 'copy', label: '复制', run: () => { closeMessageMenu(); copyMessageText(text); }});
    if (text) actions.push({id: 'forward', label: '转发', run: () => forwardMessage(message)});
    actions.push({id: 'favorite', label: message.favorite ? '取消收藏' : '收藏', active: message.favorite, run: () => toggleFavorite(message)});
    if (message.side === 'me' && !message.recall) actions.push({id: 'recall', label: '撤回', run: () => doRecall()});
    else actions.push({id: 'delete', label: '删除', danger: true, run: () => deleteMessage(index)});
    actions.push({id: 'multi', label: '多选', run: () => enterMessageSelection(index)});
    if (!message.recall && message.type !== 'sys') actions.push({id: 'quote', label: '引用', run: () => { beginMessageQuote(message, index); closeMessageMenu(); }});
    actions.push({id: 'remind', label: '提醒', run: () => remindMessage(message)});
    if (message.side !== 'me') actions.push({id: 'translate', label: '翻译', run: () => translateMessage(message)});
    else actions.push({id: 'delete', label: '删除', danger: true, run: () => deleteMessage(index)});
    if (text && !text.startsWith('[')) actions.push({id: 'search', label: '搜一搜', run: () => searchMessage(message)});
    if (text) actions.push({id: 'read', label: '连续朗读', run: () => readMessagesFrom(index)});
    return actions.slice(0, 10);
  }

  function positionMessageMenu(menu, target) {
    const targetRect = target.getBoundingClientRect();
    const menuWidth = menu.offsetWidth;
    const menuHeight = menu.offsetHeight;
    const gap = 10;
    const center = targetRect.left + targetRect.width / 2;
    const left = Math.max(12, Math.min(window.innerWidth - menuWidth - 12, center - menuWidth / 2));
    const canPlaceAbove = targetRect.top >= menuHeight + gap + 8;
    let top = canPlaceAbove ? targetRect.top - menuHeight - gap : targetRect.bottom + gap;
    top = Math.max(8, Math.min(window.innerHeight - menuHeight - 8, top));
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
    menu.classList.toggle('anchor-above', canPlaceAbove);
    menu.classList.toggle('anchor-below', !canPlaceAbove);
    menu.style.setProperty('--message-arrow-left', Math.max(22, Math.min(menuWidth - 22, center - left)) + 'px');
  }

  function openMessageContextMenu(index) {
    lastAction = Date.now();
    const message = R().chat[index];
    const target = document.querySelector('#chatArea [data-i="' + index + '"]');
    if (!message || !target || messageSelection.active) return;
    closeMessageMenu();
    actionIdx = index;
    const backdrop = document.createElement('div');
    backdrop.id = 'messageContextBackdrop';
    backdrop.addEventListener('click', closeMessageMenu);
    const menu = document.createElement('div');
    menu.id = 'messageContextMenu';
    menu.setAttribute('role', 'menu');
    menu.setAttribute('aria-label', '消息操作');
    messageMenuActions(message, index).forEach(action => {
      const button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('role', 'menuitem');
      button.className = 'message-context-action' + (action.danger ? ' danger' : '') + (action.active ? ' active' : '');
      button.innerHTML = '<span class="message-context-icon">' + messageIcons[action.id] + '</span><span>' + action.label + '</span>';
      button.addEventListener('click', event => {
        event.stopPropagation();
        action.run();
      });
      menu.appendChild(button);
    });
    document.body.append(backdrop, menu);
    requestAnimationFrame(() => positionMessageMenu(menu, target.querySelector('.bubble,.redpack') || target));
    menu.querySelector('button')?.focus({preventScroll: true});
  }

  openAction = openMessageContextMenu;
  closeAction = closeMessageMenu;
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && el('messageContextMenu')) closeMessageMenu();
  });
  window.addEventListener('resize', closeMessageMenu);
  window.addEventListener('scroll', closeMessageMenu, true);

  const messageMenuRenderChat = renderChat;
  renderChat = function (forceScroll) {
    messageMenuRenderChat(forceScroll);
    if (messageSelection.active) requestAnimationFrame(syncMessageSelection);
  };

  data.messageReminders = Array.isArray(data.messageReminders) ? data.messageReminders : [];
  data.messageReminders.filter(reminder => !reminder.done).forEach(scheduleStoredReminder);

  /* ---------- Emoji / 表情包删除交互 ---------- */
  function closePoolDeletes(except) {
    document.querySelectorAll('.pool-item.delete-open').forEach(node => {
      if (node !== except) node.classList.remove('delete-open');
    });
  }

  function makePoolItem(content, onDelete) {
    const wrapper = document.createElement('div');
    wrapper.className = 'pool-item';
    wrapper.tabIndex = 0;
    wrapper.setAttribute('role', 'button');
    wrapper.setAttribute('aria-label', '点按显示删除按钮');
    wrapper.appendChild(content);
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'pool-delete';
    remove.textContent = '×';
    remove.setAttribute('aria-label', '删除');
    remove.addEventListener('click', event => {
      event.stopPropagation();
      onDelete();
    });
    wrapper.appendChild(remove);
    const toggle = event => {
      event.stopPropagation();
      const open = !wrapper.classList.contains('delete-open');
      closePoolDeletes(wrapper);
      wrapper.classList.toggle('delete-open', open);
    };
    wrapper.addEventListener('click', toggle);
    wrapper.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') toggle(event);
    });
    return wrapper;
  }

  renderStickerManage = function () {
    const box = el('stickerManage');
    if (!box) return;
    box.replaceChildren();
    data.stickers.forEach((source, index) => {
      const resolved = safeMedia(RS(source));
      if (!resolved) return;
      const image = document.createElement('img');
      image.src = resolved;
      image.alt = '表情图片';
      image.loading = 'lazy';
      image.style.cssText = 'width:58px;height:58px;object-fit:cover;border-radius:15px;border:1px solid var(--line)';
      box.appendChild(makePoolItem(image, () => delSticker(index)));
    });
    if (!box.childElementCount) box.innerHTML = '<div class="hint">暂无表情包</div>';
  };

  renderEmojiManage = function () {
    const box = el('emojiManage');
    if (!box) return;
    box.replaceChildren();
    (data.emoji || []).forEach((emoji, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'pool-emoji';
      button.textContent = emoji;
      box.appendChild(makePoolItem(button, () => delEmoji(index)));
    });
    if (!box.childElementCount) box.innerHTML = '<div class="hint">暂无 Emoji</div>';
  };

  delSticker = function (index) {
    appConfirm({
      icon: '⌫', title: '删除这个表情？', subtitle: '删除后无法恢复', confirmText: '删除', danger: true,
      onConfirm: () => {
        data.stickers.splice(index, 1);
        const shifted = {};
        Object.keys(data.stFreq || {}).forEach(key => {
          const oldIndex = Number(key);
          if (oldIndex < index) shifted[oldIndex] = data.stFreq[key];
          if (oldIndex > index) shifted[oldIndex - 1] = data.stFreq[key];
        });
        data.stFreq = shifted;
        data.freq = Object.fromEntries(Object.entries(data.freq || {}).filter(([key]) => !key.startsWith('st')));
        save();
        renderStickers();
        renderStickerManage();
      }
    });
  };

  delEmoji = function (index) {
    appConfirm({
      icon: '⌫', title: '删除这个 Emoji？', subtitle: '聊天记录中的内容不会受影响', confirmText: '删除', danger: true,
      onConfirm: () => {
        data.emoji.splice(index, 1);
        data.freq = Object.fromEntries(Object.entries(data.freq || {}).filter(([key]) => !key.startsWith('e')));
        save();
        renderEmojiManage();
        renderEmojiPanel();
      }
    });
  };

  document.addEventListener('click', event => {
    if (!event.target.closest('.pool-item')) closePoolDeletes();
  });

  /* ---------- 常用输入改为应用内弹窗 ---------- */
  addGroup = function () {
    appPrompt({
      icon: '＋', title: '新建字卡分组', label: '分组名称', placeholder: '例如：晚安、安慰、撒娇', maxLength: 20,
      onConfirm: value => {
        const name = value.trim();
        if (R().cards[name]) {
          appNotice({icon: '!', title: '分组已存在', message: '换一个名称再试试。'});
          return;
        }
        R().cards[name] = [];
        currentGroup = name;
        save();
        renderGroups();
        renderCardList();
      }
    });
  };

  newLetter = function () {
    appForm({
      icon: '✉', title: '写一封信', subtitle: '可以先保存成草稿，之后再寄出。', confirmText: '保存草稿',
      fields: [
        {name: 'title', label: '信的标题', placeholder: '给这封信起个名字', required: true, maxLength: 40},
        {name: 'content', label: '信的内容', type: 'textarea', placeholder: '把想说的话写在这里…', required: true, maxLength: 3000}
      ],
      onConfirm: values => {
        R().letters.push({mine: true, title: values.title.trim(), content: values.content.trim(), date: new Date().toLocaleString(), status: 'draft'});
        save();
        renderLetters();
        toast('草稿已保存');
      }
    });
  };

  commentMoment = function (index) {
    appPrompt({
      icon: '☏', title: '写下评论', label: '评论内容', placeholder: '说点什么…', maxLength: 200,
      onConfirm: value => {
        const role = R();
        if (!role.moments[index]) return;
        role.moments[index].comments.push(value.trim());
        save();
        renderMoments();
      }
    });
  };

  el('btnNewLetter').onclick = newLetter;
  el('btnAddRole').onclick = () => {
    appPrompt({
      icon: '＋', title: '新建陪伴角色', label: '角色名字', placeholder: '例如：阿梨', maxLength: 20,
      onConfirm: value => {
        const id = 'r' + Date.now();
        data.roles[id] = {
          id, name: value.trim(), nickname: '', avatar: '🧑', myName: '我', myAvatar: '我',
          wallet: {his: 100, mine: 100}, cards: {'日常': [], '关心': [], '晚安': []},
          statuses: ['想你'], statusNow: '想你', pokes: ['拍了拍他的头'], shop: [], letters: [], chat: [], bill: [], moments: [],
          weekly: {key: '', date: '', stats: null}
        };
        data.activeRole = id;
        save();
        applyAll();
        toast('新角色已创建');
      }
    });
  };

  el('btnDelRole').onclick = () => {
    if (Object.keys(data.roles).length <= 1) {
      appNotice({icon: '!', title: '无法删除', message: '至少需要保留一个陪伴角色。'});
      return;
    }
    appConfirm({
      icon: '⌫', title: '删除当前角色？', subtitle: '该角色的聊天、字卡和信件都会一起删除', confirmText: '删除角色', danger: true,
      onConfirm: () => {
        delete data.roles[data.activeRole];
        data.activeRole = Object.keys(data.roles)[0];
        save();
        applyAll();
      }
    });
  };

  el('btnAddMemo').onclick = () => {
    appForm({
      icon: '◇', title: '添加纪念日', confirmText: '保存',
      fields: [
        {name: 'name', label: '纪念日名称', placeholder: '例如：第一次见面', required: true, maxLength: 30},
        {name: 'date', label: '日期', type: 'date', required: true},
        {name: 'type', label: '计时方式', type: 'select', value: 'countup', options: [{value: 'countup', label: '从这天开始累计'}, {value: 'countdown', label: '倒数到这一天'}]}
      ],
      onConfirm: values => {
        data.memos.push({name: values.name.trim(), date: values.date, type: values.type, bg: ''});
        save();
        renderMemos();
      }
    });
  };

  delMemo = function (index) {
    appConfirm({
      icon: '⌫', title: '删除这个纪念日？', confirmText: '删除', danger: true,
      onConfirm: () => { data.memos.splice(index, 1); save(); renderMemos(); }
    });
  };

  el('btnImport').onclick = () => {
    appForm({
      icon: '≡', title: '批量添加字卡', subtitle: '每行一句，会自动跳过重复内容。', confirmText: '添加',
      fields: [{name: 'content', label: '字卡内容', type: 'textarea', placeholder: '一句一行…', required: true, maxLength: 10000}],
      onConfirm: values => {
        const all = [];
        Object.values(R().cards).forEach(list => all.push(...list));
        const known = new Set(all);
        const incoming = values.content.split('\n').map(item => item.trim()).filter(Boolean);
        const fresh = incoming.filter(item => {
          if (known.has(item)) return false;
          known.add(item);
          return true;
        });
        R().cards[currentGroup] = (R().cards[currentGroup] || []).concat(fresh);
        save();
        renderGroups();
        renderCardList();
        toast('已添加 ' + fresh.length + ' 句');
      }
    });
  };

  /* ---------- 自定义音效 ---------- */
  data.customSounds = Array.isArray(data.customSounds) ? data.customSounds : [];
  let previewAudio = null;
  const originalPlaySound = playSound;
  const originalSoundRowHTML = soundRowHTML;

  function customSoundByType(type) {
    const id = String(type || '').replace(/^custom:/, '');
    return data.customSounds.find(item => item.id === id);
  }

  function playCustomSound(type) {
    const item = customSoundByType(type);
    const source = safeMedia(RS(item?.src || ''));
    if (!source) {
      toast('音效仍在加载，请稍后再试');
      return;
    }
    try {
      if (previewAudio) {
        previewAudio.pause();
        previewAudio.currentTime = 0;
      }
      previewAudio = new Audio(source);
      previewAudio.volume = Math.max(0, Math.min(1, Number(data.sound.volume || 50) / 100));
      previewAudio.play().catch(() => toast('点一下页面后再试听'));
    } catch (_) {
      toast('这个音频无法播放');
    }
  }

  playSound = function (type) {
    if (String(type || '').startsWith('custom:')) playCustomSound(type);
    else originalPlaySound(type);
  };

  function renderCustomSounds() {
    const box = el('customSoundList');
    if (!box) return;
    box.replaceChildren();
    if (!data.customSounds.length) {
      const empty = document.createElement('div');
      empty.className = 'custom-sound-empty';
      empty.textContent = '还没有添加音效';
      box.appendChild(empty);
      return;
    }
    data.customSounds.forEach(item => {
      const row = document.createElement('div');
      row.className = 'custom-sound-item' + (data.sound.type === 'custom:' + item.id ? ' selected' : '');
      const name = document.createElement('span');
      name.className = 'sound-name';
      name.textContent = item.name;
      const use = document.createElement('button');
      use.type = 'button';
      use.textContent = data.sound.type === 'custom:' + item.id ? '使用中' : '设为提示音';
      use.addEventListener('click', () => {
        data.sound.type = 'custom:' + item.id;
        save();
        renderCustomSounds();
        playCustomSound(data.sound.type);
      });
      const listen = document.createElement('button');
      listen.type = 'button';
      listen.textContent = '试听';
      listen.addEventListener('click', () => playCustomSound('custom:' + item.id));
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'sound-remove';
      remove.textContent = '删除';
      remove.addEventListener('click', () => {
        appConfirm({
          icon: '⌫', title: '删除这个音效？', subtitle: item.name, confirmText: '删除', danger: true,
          onConfirm: () => {
            const ref = String(item.src || '').replace(/^idb:/, '');
            if (String(item.src || '').startsWith('idb:')) {
              delete IDB_CACHE[ref];
              try { idb?.transaction(ST, 'readwrite').objectStore(ST).delete(ref); } catch (_) {}
            }
            data.customSounds = data.customSounds.filter(sound => sound.id !== item.id);
            if (data.sound.type === 'custom:' + item.id) data.sound.type = 'dingdong';
            save();
            soundRowHTML();
          }
        });
      });
      row.append(name, use, listen, remove);
      box.appendChild(row);
    });
  }

  soundRowHTML = function () {
    originalSoundRowHTML();
    renderCustomSounds();
  };

  el('btnAddCustomSound').onclick = () => el('customSoundInput').click();
  el('customSoundInput').onchange = async event => {
    const files = Array.from(event.target.files || []);
    if (!files.length) return;
    toast('正在导入音效…');
    let added = 0;
    let skipped = 0;
    for (const file of files) {
      if (!file.type.startsWith('audio/') || file.size > 5 * 1024 * 1024 || data.customSounds.length >= 12) {
        skipped++;
        continue;
      }
      const dataUrl = await new Promise(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = () => resolve('');
        reader.readAsDataURL(file);
      });
      if (!safeMedia(dataUrl)) {
        skipped++;
        continue;
      }
      const key = nowId('sound');
      IDB_CACHE[key] = dataUrl;
      try { idbPut(key, dataUrl); } catch (_) {}
      data.customSounds.push({id: key, name: file.name.replace(/\.[^.]+$/, '').slice(0, 40) || '自定义音效', src: 'idb:' + key, mime: file.type});
      added++;
    }
    event.target.value = '';
    save();
    renderCustomSounds();
    appNotice({
      icon: '♪', title: added ? '音效已添加' : '没有添加音效',
      message: added + ' 个音效添加成功' + (skipped ? '\n' + skipped + ' 个文件因格式、大小或数量限制被跳过。' : '')
    });
  };

  /* ---------- 全局美化 ---------- */
  const beautyDefaults = {
    pageBg: '#f8f3ef', surface: '#fffaf7', accent: '#6d3b58', topBg: '#f8f3ef', navBg: '#fffaf7', css: ''
  };
  data.beauty = Object.assign({}, beautyDefaults, data.beauty || {});

  function sanitizeCss(value) {
    return String(value || '')
      .replace(/@import[^;]+;?/gi, '')
      .replace(/url\(\s*(['"]?)(?:https?:|\/\/|file:|javascript:)[^)]+\)/gi, 'none')
      .replace(/expression\s*\([^)]*\)/gi, '')
      .replace(/behavior\s*:[^;]+;?/gi, '')
      .slice(0, 20000);
  }

  function applyBeauty() {
    const beauty = Object.assign({}, beautyDefaults, data.beauty || {});
    const root = document.documentElement;
    root.style.setProperty('--paper', beauty.pageBg);
    root.style.setProperty('--paper-deep', beauty.pageBg);
    root.style.setProperty('--surface', beauty.surface);
    root.style.setProperty('--surface-strong', beauty.surface);
    root.style.setProperty('--plum', beauty.accent);
    root.style.setProperty('--plum-deep', beauty.accent);
    let style = el('globalBeautyStyle');
    if (!style) {
      style = document.createElement('style');
      style.id = 'globalBeautyStyle';
      document.head.appendChild(style);
    }
    style.textContent =
      'body:not(.dark) #topbar,body:not(.dark) .pagebar{background:' + beauty.topBg + 'eF!important}' +
      'body:not(.dark) #bottomNav,body:not(.dark) #inputBar{background:' + beauty.navBg + 'ef!important}' +
      sanitizeCss(beauty.css);
    const values = {
      beautyPageBg: beauty.pageBg, beautySurface: beauty.surface, beautyAccent: beauty.accent,
      beautyTopBg: beauty.topBg, beautyNavBg: beauty.navBg, beautyCss: beauty.css
    };
    Object.entries(values).forEach(([id, value]) => { if (el(id)) el(id).value = value; });
  }

  el('beautySave').onclick = () => {
    data.beauty = {
      pageBg: el('beautyPageBg').value,
      surface: el('beautySurface').value,
      accent: el('beautyAccent').value,
      topBg: el('beautyTopBg').value,
      navBg: el('beautyNavBg').value,
      css: sanitizeCss(el('beautyCss').value)
    };
    save();
    applyBeauty();
    toast('全局美化已应用');
  };

  el('beautyReset').onclick = () => {
    appConfirm({
      icon: '↺', title: '恢复默认样式？', subtitle: '只会清除全局美化，不影响聊天壁纸和数据', confirmText: '恢复默认',
      onConfirm: () => {
        data.beauty = Object.assign({}, beautyDefaults);
        save();
        applyBeauty();
        toast('已恢复默认样式');
      }
    });
  };

  /* ---------- 永久补丁 ---------- */
  function runPatch(code) {
    return (0, eval)(String(code || ''));
  }

  replayPatches = function () {
    const patches = loadPatches();
    if (!patches.some(item => item.on !== false)) return;
    const previousBoot = localStorage.getItem('icepearPatchBoot');
    if (previousBoot === 'running') {
      localStorage.setItem('icepearPatchBoot', 'safe');
      setTimeout(() => appNotice({
        icon: '!', title: '已进入补丁安全模式',
        message: '上次启动时补丁没有正常完成，本次已暂时跳过。你可以在“设置 → 数据”中停用或删除有问题的补丁。'
      }), 400);
      return;
    }
    localStorage.setItem('icepearPatchBoot', 'running');
    let failed = 0;
    patches.forEach(patch => {
      if (patch.on === false) return;
      try {
        runPatch(patch.code);
        delete patch.error;
      } catch (error) {
        failed++;
        patch.error = String(error?.message || error);
      }
    });
    savePatches(patches);
    localStorage.setItem('icepearPatchBoot', 'ok');
    if (failed) setTimeout(() => toast(failed + ' 个补丁运行失败，请到数据页查看'), 500);
  };

  applyPatch = function () {
    const code = el('patchArea').value.trim();
    if (!code) {
      appNotice({icon: '{ }', title: '还没有补丁内容', message: '先把补丁代码粘贴到输入框。'});
      return;
    }
    try { snap(); } catch (_) {}
    try {
      runPatch(code);
      openAction = openMessageContextMenu;
      closeAction = closeMessageMenu;
      installVideoMiniInteractions(true);
    } catch (error) {
      appNotice({icon: '!', title: '补丁执行失败', message: String(error?.message || error)});
      return;
    }
    const patches = loadPatches();
    if (patches.length >= 99) patches.shift();
    patches.push({code, t: new Date().toLocaleString(), on: true});
    savePatches(patches);
    localStorage.setItem('icepearPatchBoot', 'ok');
    el('patchArea').value = '';
    renderPatchList();
    appNotice({icon: '{ }', title: '补丁已应用', message: '已经永久保存，重新打开软件后仍会生效。'});
  };

  togglePatch = function (index) {
    const patches = loadPatches();
    const patch = patches[index];
    if (!patch) return;
    if (patch.on === false) {
      try {
        runPatch(patch.code);
        openAction = openMessageContextMenu;
        closeAction = closeMessageMenu;
        installVideoMiniInteractions(true);
        patch.on = true;
        delete patch.error;
        toast('补丁已启用');
      } catch (error) {
        patch.error = String(error?.message || error);
        appNotice({icon: '!', title: '补丁启用失败', message: patch.error});
      }
    } else {
      patch.on = false;
      toast('补丁已停用，重启后完全生效');
    }
    savePatches(patches);
    renderPatchList();
  };

  delPatch = function (index) {
    appConfirm({
      icon: '⌫', title: '删除这个补丁？', subtitle: '删除后重启软件即可完全移除它的效果', confirmText: '删除', danger: true,
      onConfirm: () => {
        const patches = loadPatches();
        patches.splice(index, 1);
        savePatches(patches);
        renderPatchList();
      }
    });
  };

  clearAllPatches = function () {
    localStorage.removeItem('milkPatches');
    localStorage.setItem('icepearPatchBoot', 'ok');
    renderPatchList();
    toast('已清空全部补丁，重启后完全生效');
  };

  renderPatchList = function () {
    const box = el('patchList');
    if (!box) return;
    box.replaceChildren();
    const patches = loadPatches();
    if (!patches.length) {
      box.innerHTML = '<div class="hint">暂无永久补丁</div>';
      return;
    }
    patches.forEach((patch, index) => {
      const row = document.createElement('div');
      row.className = 'patch-item';
      row.style.gap = '8px';
      const info = document.createElement('div');
      info.style.cssText = 'flex:1;min-width:0';
      const title = document.createElement('b');
      title.style.cssText = 'display:block;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';
      title.textContent = '#' + (index + 1) + ' · ' + (patch.on === false ? '已停用' : patch.error ? '运行失败' : '已启用');
      const time = document.createElement('small');
      time.style.cssText = 'display:block;margin-top:2px;color:var(--faint-ink);font-size:10px';
      time.textContent = patch.error || patch.t || '';
      info.append(title, time);
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = patch.on === false ? '' : 'ok';
      toggle.textContent = patch.on === false ? '启用' : '停用';
      toggle.addEventListener('click', () => togglePatch(index));
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'del';
      remove.textContent = '删除';
      remove.addEventListener('click', () => delPatch(index));
      row.append(info, toggle, remove);
      box.appendChild(row);
    });
  };

  el('btnApplyPatch').onclick = applyPatch;
  el('btnClearPatches').onclick = () => appConfirm({
    icon: '⌫', title: '清空全部补丁？', subtitle: '这个操作无法撤销', confirmText: '全部清空', danger: true, onConfirm: clearAllPatches
  });
  el('btnExportPatches').onclick = () => {
    const patches = loadPatches();
    if (!patches.length) {
      appNotice({icon: '{ }', title: '暂无补丁', message: '还没有可以导出的补丁。'});
      return;
    }
    const content = patches.map((patch, index) =>
      '/* === 补丁 #' + (index + 1) + ' ' + (patch.on === false ? '（已停用） ' : '') + (patch.t || '') + ' === */\n' + (patch.code || '')
    ).join('\n\n');
    download('Icepear-patches-all.txt', content, 'text/plain;charset=utf-8');
  };

  /* ---------- 词云 ---------- */
  function roundRect(context, x, y, width, height, radius) {
    const r = Math.min(radius, width / 2, height / 2);
    context.beginPath();
    context.moveTo(x + r, y);
    context.arcTo(x + width, y, x + width, y + height, r);
    context.arcTo(x + width, y + height, x, y + height, r);
    context.arcTo(x, y + height, x, y, r);
    context.arcTo(x, y, x + width, y, r);
    context.closePath();
  }

  function hashWord(value) {
    let hash = 2166136261;
    for (let index = 0; index < value.length; index++) {
      hash ^= value.charCodeAt(index);
      hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
  }

  function cloudItems() {
    const stop = new Set(['我', '你', '他', '她', '的', '了', '是', '在', '也', '就', '都', '吗', '吧', '啊', '呢', '我们', '你们', '他们']);
    const counts = new Map();
    R().chat.filter(message => message.text && !message.recall && message.type !== 'sys').forEach(message => {
      String(message.text).split(/[\n，。！？!?；;、]+/).forEach(raw => {
        let phrase = raw.replace(/\s+/g, ' ').trim();
        if (!phrase || stop.has(phrase)) return;
        if (phrase.length > 18) phrase = phrase.slice(0, 17) + '…';
        if (phrase.length === 1 && /[\u4e00-\u9fff]/.test(phrase)) return;
        counts.set(phrase, (counts.get(phrase) || 0) + 1);
      });
    });
    return Array.from(counts.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'zh-CN')).slice(0, 46);
  }

  drawCloud = function () {
    const items = cloudItems();
    if (!items.length) {
      appNotice({icon: 'Aa', title: '聊天内容还不够', message: '多聊几句后再来生成词云。'});
      return;
    }
    const canvas = el('wordcloud');
    const width = 900;
    const height = 1120;
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    const background = context.createLinearGradient(0, 0, width, height);
    background.addColorStop(0, '#fffaf7');
    background.addColorStop(0.52, '#f8f3ef');
    background.addColorStop(1, '#efe7e9');
    context.fillStyle = background;
    context.fillRect(0, 0, width, height);

    context.fillStyle = 'rgba(109,59,88,.06)';
    context.beginPath(); context.arc(782, 118, 116, 0, Math.PI * 2); context.fill();
    context.fillStyle = 'rgba(231,125,115,.08)';
    context.beginPath(); context.arc(90, 1015, 150, 0, Math.PI * 2); context.fill();

    context.fillStyle = '#6d3b58';
    context.font = '700 28px "PingFang SC","Microsoft YaHei",sans-serif';
    context.fillText('ICEPEAR · OUR WORDS', 54, 66);
    context.fillStyle = '#2d252a';
    context.font = '800 50px "PingFang SC","Microsoft YaHei",sans-serif';
    context.fillText('我们聊过的词', 54, 130);
    context.fillStyle = '#8b7c84';
    context.font = '500 22px "PingFang SC","Microsoft YaHei",sans-serif';
    context.fillText(displayName() + ' · ' + new Date().toLocaleDateString('zh-CN'), 56, 170);

    roundRect(context, 38, 202, 824, 850, 42);
    context.fillStyle = 'rgba(255,255,255,.7)';
    context.fill();
    context.strokeStyle = 'rgba(109,59,88,.08)';
    context.lineWidth = 2;
    context.stroke();

    const colors = ['#6d3b58', '#bf625f', '#5f8475', '#a16f43', '#82679b', '#3c8990'];
    const boxes = [];
    const max = items[0][1];
    const centerX = width / 2;
    const centerY = 620;
    let placed = 0;
    items.forEach(([word, count], index) => {
      const weight = count / max;
      const rank = 1 - index / Math.max(1, items.length) * 0.48;
      let size = Math.round(27 + 52 * Math.max(weight, rank * 0.62));
      if (word.length > 10) size = Math.round(size * 0.78);
      context.font = '700 ' + size + 'px "PingFang SC","Microsoft YaHei",sans-serif';
      const textWidth = context.measureText(word).width;
      const boxWidth = textWidth + 22;
      const boxHeight = size * 1.18 + 12;
      const seed = hashWord(word);
      let chosen = null;
      for (let attempt = 0; attempt < 320; attempt++) {
        const angle = attempt * 0.57 + (seed % 31) / 10;
        const radius = 5.1 * Math.sqrt(attempt) * 5;
        const x = centerX + Math.cos(angle) * radius - boxWidth / 2;
        const y = centerY + Math.sin(angle) * radius * 0.72 - boxHeight / 2;
        const candidate = {x, y, w: boxWidth, h: boxHeight};
        if (x < 62 || x + boxWidth > 838 || y < 232 || y + boxHeight > 1024) continue;
        const collision = boxes.some(box =>
          candidate.x < box.x + box.w + 8 && candidate.x + candidate.w + 8 > box.x &&
          candidate.y < box.y + box.h + 6 && candidate.y + candidate.h + 6 > box.y
        );
        if (!collision) {
          chosen = candidate;
          break;
        }
      }
      if (!chosen) return;
      boxes.push(chosen);
      context.fillStyle = colors[seed % colors.length];
      context.font = '700 ' + size + 'px "PingFang SC","Microsoft YaHei",sans-serif';
      context.fillText(word, chosen.x + 11, chosen.y + size + 3);
      placed++;
    });

    context.fillStyle = '#8b7c84';
    context.font = '500 18px "PingFang SC","Microsoft YaHei",sans-serif';
    context.fillText('把想念留在词里，也留在今天。', 54, 1090);
    el('cloudCount').textContent = placed + ' 个关键词';
  };

  /* ---------- 视频小窗贴边缩进 ---------- */
  let mini = el('videoMini');
  const originalMinimizeVideo = minimizeVideo;
  const originalRestoreVideo = restoreVideo;
  const originalHangUp = hangUp;
  const drag = {active: false, moved: false, startX: 0, startY: 0, left: 0, top: 0, suppressClick: false};
  const miniHandle = (() => {
    const button = document.createElement('button');
    button.id = 'videoMiniHandle';
    button.type = 'button';
    button.setAttribute('aria-label', '展开视频通话悬浮窗');
    button.addEventListener('click', untuckMini);
    document.body.appendChild(button);
    return button;
  })();

  function clearEdgeTuck() {
    mini.classList.remove('edge-tucked', 'edge-left', 'edge-right');
    mini.removeAttribute('aria-hidden');
    miniHandle.style.display = 'none';
    miniHandle.classList.remove('left', 'right');
  }

  function positionMini() {
    clearEdgeTuck();
    mini.style.right = 'auto';
    mini.style.bottom = 'auto';
    const width = mini.offsetWidth || 112;
    const height = mini.offsetHeight || 180;
    mini.style.left = Math.max(10, window.innerWidth - width - 12) + 'px';
    mini.style.top = Math.max(12, window.innerHeight - height - 82) + 'px';
  }

  function tuckMini(side) {
    const width = mini.offsetWidth;
    const height = mini.offsetHeight;
    const top = parseFloat(mini.style.top) || mini.getBoundingClientRect().top || 80;
    clearEdgeTuck();
    mini.classList.add('edge-tucked', side === 'left' ? 'edge-left' : 'edge-right');
    mini.setAttribute('aria-hidden', 'true');
    mini.style.left = (side === 'left' ? -width - 8 : window.innerWidth + 8) + 'px';
    miniHandle.classList.add(side);
    miniHandle.style.top = Math.max(8, Math.min(window.innerHeight - 66, top + height / 2 - 29)) + 'px';
    miniHandle.style.display = 'flex';
    requestAnimationFrame(() => miniHandle.focus({preventScroll: true}));
  }

  function untuckMini() {
    const fromLeft = mini.classList.contains('edge-left');
    const width = mini.offsetWidth;
    clearEdgeTuck();
    mini.style.left = (fromLeft ? 10 : Math.max(10, window.innerWidth - width - 10)) + 'px';
  }

  function enhancedMinimizeVideo() {
    originalMinimizeVideo();
    requestAnimationFrame(positionMini);
  }
  function enhancedRestoreVideo() {
    clearEdgeTuck();
    originalRestoreVideo();
  }
  function enhancedHangUp() {
    clearEdgeTuck();
    originalHangUp();
  }

  function handleMiniPointerDown(event) {
    if (event.target.closest('#btnMiniHang')) return;
    const rect = mini.getBoundingClientRect();
    drag.active = true;
    drag.moved = false;
    drag.startX = event.clientX;
    drag.startY = event.clientY;
    drag.left = rect.left;
    drag.top = rect.top;
    mini.style.right = 'auto';
    mini.style.bottom = 'auto';
    mini.setPointerCapture?.(event.pointerId);
  }

  function handleMiniPointerMove(event) {
    if (!drag.active) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < 5) return;
    drag.moved = true;
    clearEdgeTuck();
    mini.classList.add('dragging');
    const width = mini.offsetWidth;
    const height = mini.offsetHeight;
    mini.style.left = Math.max(0, Math.min(window.innerWidth - width, drag.left + dx)) + 'px';
    mini.style.top = Math.max(0, Math.min(window.innerHeight - height, drag.top + dy)) + 'px';
    event.preventDefault();
  }

  function finishMiniDrag(event) {
    if (!drag.active) return;
    drag.active = false;
    mini.classList.remove('dragging');
    if (drag.moved) {
      drag.suppressClick = true;
      const rect = mini.getBoundingClientRect();
      if (rect.left < 42) tuckMini('left');
      else if (window.innerWidth - rect.right < 42) tuckMini('right');
      setTimeout(() => { drag.suppressClick = false; }, 80);
    }
    mini.releasePointerCapture?.(event.pointerId);
  }

  function handleMiniClick(event) {
    if (event.target.closest('#btnMiniHang') || drag.suppressClick) return;
    if (mini.classList.contains('edge-tucked')) untuckMini();
    else restoreVideo();
  }

  function installVideoMiniInteractions(resetNode) {
    if (resetNode) {
      const freshMini = mini.cloneNode(true);
      mini.replaceWith(freshMini);
      mini = freshMini;
    }
    minimizeVideo = enhancedMinimizeVideo;
    restoreVideo = enhancedRestoreVideo;
    hangUp = enhancedHangUp;
    mini.onpointerdown = handleMiniPointerDown;
    mini.onpointermove = handleMiniPointerMove;
    mini.onpointerup = finishMiniDrag;
    mini.onpointercancel = finishMiniDrag;
    mini.onclick = handleMiniClick;
    el('btnMin').onclick = enhancedMinimizeVideo;
    el('btnHang').onclick = enhancedHangUp;
    el('btnMiniHang').onclick = event => { event.stopPropagation(); enhancedHangUp(); };
  }

  installVideoMiniInteractions(false);
  window.addEventListener('resize', () => {
    if (mini.style.display !== 'block') return;
    if (mini.classList.contains('edge-left')) tuckMini('left');
    else if (mini.classList.contains('edge-right')) tuckMini('right');
    else {
      const rect = mini.getBoundingClientRect();
      mini.style.left = Math.max(0, Math.min(window.innerWidth - rect.width, rect.left)) + 'px';
      mini.style.top = Math.max(0, Math.min(window.innerHeight - rect.height, rect.top)) + 'px';
    }
  });

  /* ---------- Icepear 2.3：商品、动态、引用与手势 ---------- */
  const uiPaths = {
    plus: '<path d="M12 5v14M5 12h14"/>',
    pencil: '<path d="M4 20h4l11-11-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/>',
    trash: '<path d="M4 7h16M9 7V4h6v3m3 0-1 14H7L6 7m4 4v6m4-6v6"/>',
    box: '<path d="m4 7 8-4 8 4-8 4-8-4Z"/><path d="M4 7v10l8 4 8-4V7M12 11v10"/>',
    food: '<path d="M7 3v7m3-7v7M5 3v5a4 4 0 0 0 4 4v9M17 3v18M17 3c3 3 3 7 0 10"/>',
    drink: '<path d="M6 4h12l-1 17H7L6 4Z"/><path d="M8 8h8M14 4l3-2"/>',
    daily: '<path d="M5 9h14v11H5zM8 9V6a4 4 0 0 1 8 0v3"/>',
    chevron: '<path d="m8 10 4 4 4-4"/>',
    send: '<path d="m4 4 17 8-17 8 3-8Z"/><path d="M7 12h14"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.5" cy="9" r="1.5"/><path d="m4 17 5-5 3 3 3-3 5 5"/>',
    smile: '<circle cx="12" cy="12" r="9"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/>'
  };
  const uiSvg = name => '<svg viewBox="0 0 24 24" aria-hidden="true">' + uiPaths[name] + '</svg>';
  const builtInStickerManage = renderStickerManage;
  const builtInEmojiManage = renderEmojiManage;
  let icepearInstalled = false;
  let balanceFallbackSaveBound = false;
  let pendingQuoteRef = '';

  function icepearPrefs() {
    data.icepearUi = data.icepearUi && typeof data.icepearUi === 'object' ? data.icepearUi : {};
    data.icepearUi.shopActive = data.icepearUi.shopActive && typeof data.icepearUi.shopActive === 'object' ? data.icepearUi.shopActive : {};
    data.icepearUi.shopCollapsed = data.icepearUi.shopCollapsed && typeof data.icepearUi.shopCollapsed === 'object' ? data.icepearUi.shopCollapsed : {};
    data.icepearUi.stickerFolds = Object.assign({emoji: false, images: true, chatAll: false}, data.icepearUi.stickerFolds || {});
    return data.icepearUi;
  }

  function ensureMessageId(message) {
    if (message && !message.id) message.id = nowId('msg');
    return message?.id || '';
  }

  function ensureShopData(role, roleKey) {
    const defaults = [
      {id: 'food', name: '食物'}, {id: 'drink', name: '饮品'}, {id: 'daily', name: '日用'}
    ];
    role.shop = Array.isArray(role.shop) ? role.shop : [];
    if (!Array.isArray(role.shopCategories) || !role.shopCategories.length) role.shopCategories = defaults.map(item => Object.assign({}, item));
    const seen = new Set();
    role.shopCategories = role.shopCategories.map(category => {
      let id = String(category?.id || '').trim();
      if (!/^[a-z0-9_-]{1,48}$/i.test(id) || seen.has(id)) id = nowId('shopcat');
      seen.add(id);
      return {id, name: String(category?.name || '未命名分组').trim().slice(0, 20) || '未命名分组'};
    });
    role.shop.forEach(product => {
      product.id = product.id || nowId('product');
      product.cat = String(product.cat || role.shopCategories[0].id);
      delete product.wm;
      if (!seen.has(product.cat)) {
        const category = {id: product.cat, name: product.cat === 'food' ? '食物' : product.cat === 'drink' ? '饮品' : product.cat === 'daily' ? '日用' : '其他'};
        role.shopCategories.push(category);
        seen.add(category.id);
      }
    });
    const prefs = icepearPrefs();
    if (!seen.has(prefs.shopActive[roleKey])) prefs.shopActive[roleKey] = role.shopCategories[0].id;
    return role.shopCategories;
  }

  function ensureBalanceFallback(role) {
    const legacy = '今天先送你一个抱抱，别的下次补上。';
    role.balanceFallback = role.balanceFallback && typeof role.balanceFallback === 'object' ? role.balanceFallback : {};
    if (role.balanceFallback.red == null) role.balanceFallback.red = legacy;
    if (role.balanceFallback.zhuan == null) role.balanceFallback.zhuan = legacy;
    return role.balanceFallback;
  }

  function ensureBalanceFallbackSettings() {
    const base = el('setBase');
    if (!base) return;
    let section = el('balanceFallbackSettings');
    if (!section) {
      section = document.createElement('section');
      section.id = 'balanceFallbackSettings';
      section.className = 'balance-fallback-settings';
      section.innerHTML =
        '<h3>余额不足时</h3>' +
        '<p class="settings-note">当他的余额为 0、无法主动发红包或转账时，发送这里填写的替代文案。留空则不发送文字。</p>' +
        '<div class="setting-row"><label for="setBalanceFallbackRed">红包兜底文案</label><input id="setBalanceFallbackRed" maxlength="120" placeholder="留空则不发送"></div>' +
        '<div class="setting-row"><label for="setBalanceFallbackTransfer">转账兜底文案</label><input id="setBalanceFallbackTransfer" maxlength="120" placeholder="留空则不发送"></div>';
      const activeInterval = el('setActiveMax')?.closest('.setting-row');
      if (activeInterval) activeInterval.after(section);
      else base.appendChild(section);
    }
    const fallback = ensureBalanceFallback(R());
    el('setBalanceFallbackRed').value = fallback.red;
    el('setBalanceFallbackTransfer').value = fallback.zhuan;
    if (!balanceFallbackSaveBound) {
      const saveButton = el('btnSave');
      const legacySave = saveButton?.onclick;
      if (saveButton && typeof legacySave === 'function') {
        saveButton.onclick = function (event) {
          const current = ensureBalanceFallback(R());
          current.red = String(el('setBalanceFallbackRed')?.value || '').trim();
          current.zhuan = String(el('setBalanceFallbackTransfer')?.value || '').trim();
          return legacySave.call(this, event);
        };
        balanceFallbackSaveBound = true;
      }
    }
  }

  function ensureV230Data() {
    Object.entries(data.roles || {}).forEach(([key, role]) => {
      ensureShopData(role, key);
      ensureBalanceFallback(role);
      role.moments = Array.isArray(role.moments) ? role.moments : [];
      role.moments.forEach(moment => {
        moment.id = moment.id || nowId('moment');
        moment.comments = Array.isArray(moment.comments) ? moment.comments : [];
        moment.likes = Number(moment.likes) || 0;
      });
      role.chat = Array.isArray(role.chat) ? role.chat : [];
      role.chat.forEach(ensureMessageId);
    });
  }

  function activeShopCategory() {
    const role = R();
    const categories = ensureShopData(role, data.activeRole);
    const id = icepearPrefs().shopActive[data.activeRole];
    return categories.find(category => category.id === id) || categories[0];
  }

  function shopCategoryIcon(id) {
    return uiSvg(id === 'food' ? 'food' : id === 'drink' ? 'drink' : id === 'daily' ? 'daily' : 'box');
  }

  function prepareShopPage() {
    const page = el('pageShop');
    const body = page?.querySelector('.pagebody');
    const categories = el('shopCat');
    const list = el('shopList');
    const addProduct = el('btnAddShop');
    if (!body || !categories || !list || !addProduct) return;
    ['newShopName', 'newShopPrice', 'newShopCat'].forEach(id => {
      const row = el(id)?.closest('.setting-row');
      if (row) {
        row.hidden = true;
        row.style.setProperty('display', 'none', 'important');
      }
    });
    const takeaway = el('newShopWm')?.closest('.switch');
    if (takeaway) takeaway.remove();
    const takeawayReply = [...document.querySelectorAll('#recvTabs button')].find(button => button.textContent.includes('外卖'));
    if (takeawayReply) takeawayReply.remove();
    if (typeof recvNow !== 'undefined' && recvNow === 'wm') recvTab('red');
    const productHeading = [...body.querySelectorAll('h3')].find(node => node.textContent.includes('商品'));
    if (productHeading) productHeading.textContent = '商品分组';
    categories.className = 'shop-category-grid';
    list.className = 'shop-catalog';
    let tools = el('shopCategoryTools');
    if (!tools) {
      tools = document.createElement('div');
      tools.id = 'shopCategoryTools';
      tools.className = 'shop-category-tools';
      tools.innerHTML =
        '<button type="button" data-shop-tool="add">' + uiSvg('plus') + '<span>新建分组</span></button>' +
        '<button type="button" data-shop-tool="rename">' + uiSvg('pencil') + '<span>改名</span></button>' +
        '<button type="button" class="danger" data-shop-tool="delete">' + uiSvg('trash') + '<span>删除</span></button>';
      categories.after(tools);
      tools.addEventListener('click', event => {
        const action = event.target.closest('button')?.dataset.shopTool;
        if (action === 'add') addShopCategory();
        if (action === 'rename') renameShopCategory();
        if (action === 'delete') deleteShopCategory();
      });
    }
    addProduct.className = 'btn shop-add-product';
    addProduct.innerHTML = uiSvg('plus') + '<span>添加商品</span>';
    addProduct.onclick = openProductDialog;
    body.appendChild(addProduct);
  }

  function renderShopCategories() {
    prepareShopPage();
    const box = el('shopCat');
    if (!box) return;
    const role = R();
    const categories = ensureShopData(role, data.activeRole);
    const active = activeShopCategory();
    box.replaceChildren();
    categories.forEach(category => {
      const count = role.shop.filter(product => product.cat === category.id).length;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'shop-category-card' + (category.id === active.id ? ' on' : '');
      button.dataset.categoryId = category.id;
      button.setAttribute('aria-pressed', category.id === active.id ? 'true' : 'false');
      button.innerHTML = '<span class="shop-category-icon">' + shopCategoryIcon(category.id) + '</span><span class="shop-category-copy"><b>' + esc(category.name) + '</b><small>' + count + ' 件商品</small></span>';
      button.addEventListener('click', () => {
        icepearPrefs().shopActive[data.activeRole] = category.id;
        save();
        renderShopCategories();
        renderShopCatalog();
      });
      box.appendChild(button);
    });
  }

  function renderShopCatalog() {
    prepareShopPage();
    const box = el('shopList');
    if (!box) return;
    const role = R();
    const category = activeShopCategory();
    const products = role.shop.filter(product => product.cat === category.id);
    const key = data.activeRole + ':' + category.id;
    const collapsed = Boolean(icepearPrefs().shopCollapsed[key]);
    box.innerHTML =
      '<section class="shop-category-section' + (collapsed ? ' collapsed' : '') + '">' +
        '<button type="button" class="shop-category-toggle" aria-expanded="' + (!collapsed) + '">' +
          '<span><b>' + esc(category.name) + '</b><small>' + products.length + ' 件商品</small></span><i>' + uiSvg('chevron') + '</i>' +
        '</button>' +
        '<div class="shop-product-grid"></div>' +
      '</section>';
    box.querySelector('.shop-category-toggle').addEventListener('click', () => {
      icepearPrefs().shopCollapsed[key] = !collapsed;
      save();
      renderShopCatalog();
    });
    const grid = box.querySelector('.shop-product-grid');
    if (!products.length) {
      grid.innerHTML = '<div class="shop-empty"><span>' + uiSvg('box') + '</span><b>这个分组还没有商品</b><small>点击页面底部“添加商品”放入第一件商品。</small></div>';
      return;
    }
    products.forEach(product => {
      const card = document.createElement('article');
      card.className = 'shop-product-card';
      card.innerHTML =
        '<div class="shop-product-mark">' + shopCategoryIcon(category.id) + '</div>' +
        '<div class="shop-product-copy"><b>' + esc(product.name) + '</b><span>¥' + Number(product.price || 0).toFixed(2).replace(/\.00$/, '') + '</span></div>' +
        '<div class="shop-product-actions"><button type="button" class="primary" data-product-send>送给他</button><button type="button" class="icon-only danger" data-product-delete aria-label="删除' + esc(product.name) + '">' + uiSvg('trash') + '</button></div>';
      card.querySelector('[data-product-send]').addEventListener('click', () => sendShopProduct(product.id));
      card.querySelector('[data-product-delete]').addEventListener('click', () => deleteShopProduct(product.id));
      grid.appendChild(card);
    });
  }

  function renderShopExperience() {
    ensureV230Data();
    renderShopCategories();
    renderShopCatalog();
  }

  function addShopCategory() {
    appPrompt({
      iconSvg: uiSvg('plus'), title: '新建商品分组', subtitle: '分组可以随时改名或删除', label: '分组名称', placeholder: '例如：文具、零食、纪念品', maxLength: 20,
      onConfirm: value => {
        const name = value.trim();
        const role = R();
        if (role.shopCategories.some(category => category.name === name)) {
          appNotice({icon: '!', title: '分组名称重复', message: '换一个名称再试试。'});
          return false;
        }
        const category = {id: nowId('shopcat'), name};
        role.shopCategories.push(category);
        icepearPrefs().shopActive[data.activeRole] = category.id;
        save();
        renderShopExperience();
      }
    });
  }

  function renameShopCategory() {
    const category = activeShopCategory();
    appPrompt({
      iconSvg: uiSvg('pencil'), title: '修改分组名称', label: '新的名称', value: category.name, maxLength: 20,
      onConfirm: value => {
        const name = value.trim();
        if (R().shopCategories.some(item => item.id !== category.id && item.name === name)) {
          appNotice({icon: '!', title: '分组名称重复', message: '换一个名称再试试。'});
          return false;
        }
        category.name = name;
        save();
        renderShopExperience();
      }
    });
  }

  function deleteShopCategory() {
    const role = R();
    const category = activeShopCategory();
    if (role.shopCategories.length <= 1) {
      appNotice({icon: '!', title: '至少保留一个分组', message: '可以先新建分组，再删除当前分组。'});
      return;
    }
    const count = role.shop.filter(product => product.cat === category.id).length;
    appConfirm({
      iconSvg: uiSvg('trash'), title: '删除“' + category.name + '”？', subtitle: count ? '分组内的 ' + count + ' 件商品也会一起删除' : '删除后无法恢复', confirmText: '删除分组', danger: true,
      onConfirm: () => {
        role.shopCategories = role.shopCategories.filter(item => item.id !== category.id);
        role.shop = role.shop.filter(product => product.cat !== category.id);
        icepearPrefs().shopActive[data.activeRole] = role.shopCategories[0].id;
        save();
        renderShopExperience();
      }
    });
  }

  function openProductDialog() {
    const categories = ensureShopData(R(), data.activeRole);
    appForm({
      iconSvg: uiSvg('box'), title: '添加商品', subtitle: '商品会放进你选择的分组', confirmText: '添加商品',
      fields: [
        {name: 'name', label: '商品名称', placeholder: '例如：草莓奶油蛋糕', required: true, maxLength: 30},
        {name: 'price', label: '价格', type: 'number', placeholder: '0.00', inputMode: 'decimal', required: true},
        {name: 'category', label: '所属分组', type: 'select', value: activeShopCategory().id, options: categories.map(category => ({value: category.id, label: category.name}))}
      ],
      validate: values => {
        const price = Number(values.price);
        if (!Number.isFinite(price) || price < 0) return '价格应为不小于 0 的数字';
        return '';
      },
      onConfirm: values => {
        const product = {id: nowId('product'), name: values.name.trim(), price: Math.round(Number(values.price) * 100) / 100, cat: values.category};
        R().shop.push(product);
        icepearPrefs().shopActive[data.activeRole] = product.cat;
        icepearPrefs().shopCollapsed[data.activeRole + ':' + product.cat] = false;
        save();
        renderShopExperience();
        toast('商品已添加');
      }
    });
  }

  function commitShopGift(product) {
    const role = R();
    role.wallet.mine -= Number(product.price) || 0;
    role.wallet.his += Number(product.price) || 0;
    const message = addMsg('me', {type: 'gift', gift: product.name, price: Number(product.price) || 0, read: false});
    const index = role.chat.indexOf(message);
    if (index >= 0) hisAuto(index);
    updateWallet();
    try { billAdd({from: 'me', to: 'his', type: '礼物：' + product.name, amount: Number(product.price) || 0, status: '已送出', t: Date.now()}); } catch (_) {}
  }

  function sendShopProduct(productId) {
    const product = R().shop.find(item => item.id === productId);
    if (!product) return;
    if (Number(product.price) > Number(R().wallet.mine)) {
      appConfirm({icon: '¥', title: '余额不足', subtitle: '仍然送出后，余额会变成负数', confirmText: '继续送出', onConfirm: () => commitShopGift(product)});
      return;
    }
    commitShopGift(product);
  }

  function deleteShopProduct(productId) {
    const product = R().shop.find(item => item.id === productId);
    if (!product) return;
    appConfirm({
      iconSvg: uiSvg('trash'), title: '删除“' + product.name + '”？', confirmText: '删除', danger: true,
      onConfirm: () => { R().shop = R().shop.filter(item => item.id !== productId); save(); renderShopExperience(); }
    });
  }

  function momentCommentHtml(comment) {
    if (comment && typeof comment === 'object') return '<div><b>' + esc(comment.name || displayName()) + '：</b>' + esc(comment.text || '') + '</div>';
    return '<div><b>' + esc(displayName()) + '：</b>' + esc(comment || '') + '</div>';
  }

  function seedMomentsIfNeeded(role) {
    const pool = allCards().filter(Boolean);
    if (role.momentsSeededV3 || role.moments.length || !pool.length) return;
    [2, 7, 18, 31].forEach(hours => role.moments.push({id: nowId('moment'), text: pool[rand(0, pool.length - 1)], t: Date.now() - hours * 3600000, likes: rand(0, 8), comments: [], mine: false}));
    role.momentsSeededV3 = true;
    save();
  }

  function renderMomentsV230() {
    const role = R();
    role.moments = Array.isArray(role.moments) ? role.moments : [];
    seedMomentsIfNeeded(role);
    const box = el('momentsBody');
    if (!box) return;
    const composer =
      '<section class="moment-composer"><div class="moment-avatar">' + headHTML('me') + '</div><div class="moment-compose-main"><label for="momentPostInput">发布朋友圈</label><textarea id="momentPostInput" maxlength="500" placeholder="记录此刻想说的话…"></textarea><div><span id="momentPostCount">0 / 500</span><button type="button" id="momentPostButton">' + uiSvg('send') + '<span>发布</span></button></div></div></section>';
    const feed = role.moments.length ? role.moments.map((moment, index) => {
      moment.id = moment.id || nowId('moment');
      moment.comments = Array.isArray(moment.comments) ? moment.comments : [];
      const mine = Boolean(moment.mine);
      const date = new Date(moment.t || Date.now());
      const stamp = (date.getMonth() + 1) + '-' + date.getDate() + ' ' + String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
      const side = mine ? 'me' : 'other';
      return '<article class="moment-card" data-moment-id="' + moment.id + '">' +
        '<div class="moment-avatar">' + headHTML(side) + '</div><div class="moment-content"><header><b>' + esc(mine ? (role.myName || '我') : displayName()) + '</b>' +
        (mine ? '<button type="button" class="moment-delete" data-moment-delete="' + index + '" aria-label="删除这条朋友圈">' + uiSvg('trash') + '</button>' : '') + '</header>' +
        '<p>' + esc(moment.text || '') + '</p><footer><time>' + stamp + '</time><span>喜欢 ' + (Number(moment.likes) || 0) + '</span>' +
        (!mine ? '<button type="button" data-moment-like="' + index + '">' + (moment.likedByMe ? '已喜欢' : '喜欢') + '</button>' : '') +
        '<button type="button" data-moment-comment="' + index + '">评论</button></footer>' +
        (moment.comments.length ? '<div class="moment-comments">' + moment.comments.map(momentCommentHtml).join('') + '</div>' : '') + '</div></article>';
    }).join('') : '<div class="moment-empty"><span>' + uiSvg('image') + '</span><b>还没有朋友圈</b><small>先发布第一条动态，或者去字卡库添加内容。</small></div>';
    box.innerHTML = composer + '<div class="moment-feed">' + feed + '</div>';
    const input = el('momentPostInput');
    input.addEventListener('input', () => { el('momentPostCount').textContent = input.value.length + ' / 500'; });
    el('momentPostButton').addEventListener('click', publishMomentV230);
    box.querySelectorAll('[data-moment-like]').forEach(button => button.addEventListener('click', () => likeMomentV230(Number(button.dataset.momentLike))));
    box.querySelectorAll('[data-moment-comment]').forEach(button => button.addEventListener('click', () => commentMomentV230(Number(button.dataset.momentComment))));
    box.querySelectorAll('[data-moment-delete]').forEach(button => button.addEventListener('click', () => deleteMomentV230(Number(button.dataset.momentDelete))));
  }

  function publishMomentV230() {
    const input = el('momentPostInput');
    const text = input?.value.trim();
    if (!text) {
      appNotice({icon: '!', title: '还没有内容', message: '写下想分享的话再发布。'});
      return;
    }
    const roleKey = data.activeRole;
    const moment = {id: nowId('moment'), text, t: Date.now(), likes: 0, comments: [], mine: true};
    data.roles[roleKey].moments.unshift(moment);
    save();
    renderMomentsV230();
    toast('已发布');
    setTimeout(() => {
      const role = data.roles[roleKey];
      const target = role?.moments.find(item => item.id === moment.id);
      if (!target) return;
      target.likes++;
      const pool = Object.values(role.cards || {}).flat().filter(Boolean);
      if (pool.length) target.comments.push({name: role.nickname || role.name || '他', text: pool[rand(0, pool.length - 1)]});
      save();
      if (data.activeRole === roleKey && el('pageMoments').classList.contains('active')) renderMomentsV230();
    }, rand(8000, 18000));
  }

  function likeMomentV230(index) {
    const moment = R().moments[index];
    if (!moment || moment.mine) return;
    moment.likedByMe = !moment.likedByMe;
    moment.likes = Math.max(0, (Number(moment.likes) || 0) + (moment.likedByMe ? 1 : -1));
    save();
    renderMomentsV230();
  }

  function commentMomentV230(index) {
    const roleKey = data.activeRole;
    const moment = R().moments[index];
    if (!moment) return;
    appPrompt({
      icon: '···', title: '写下评论', label: '评论内容', placeholder: '说点什么…', maxLength: 200,
      onConfirm: value => {
        moment.comments.push({name: R().myName || '我', text: value.trim()});
        save();
        renderMomentsV230();
        if (!moment.mine) {
          setTimeout(() => {
            const role = data.roles[roleKey];
            const target = role?.moments.find(item => item.id === moment.id);
            const pool = Object.values(role?.cards || {}).flat().filter(Boolean);
            if (!target || !pool.length) return;
            target.comments.push({name: role.nickname || role.name || '他', text: pool[rand(0, pool.length - 1)]});
            save();
            if (data.activeRole === roleKey && el('pageMoments').classList.contains('active')) renderMomentsV230();
          }, rand(5000, 12000));
        }
      }
    });
  }

  function deleteMomentV230(index) {
    const moment = R().moments[index];
    if (!moment?.mine) return;
    appConfirm({iconSvg: uiSvg('trash'), title: '删除这条朋友圈？', confirmText: '删除', danger: true, onConfirm: () => { R().moments.splice(index, 1); save(); renderMomentsV230(); }});
  }

  function beginMessageQuote(message, index) {
    if (!message || message.recall || message.type === 'sys') return;
    const ref = ensureMessageId(message);
    if (!ref && Number.isInteger(index)) ensureMessageId(R().chat[index]);
    setQuote(messageText(message), ref || R().chat[index]?.id || '');
    save();
  }

  function setQuoteV230(text, ref) {
    quoteTarget = String(text || '');
    pendingQuoteRef = String(ref || '');
    el('quoteText').textContent = '引用：' + quoteTarget;
    el('quoteBar').style.display = 'flex';
  }

  function clearQuoteV230() {
    quoteTarget = null;
    pendingQuoteRef = '';
    el('quoteBar').style.display = 'none';
  }

  function sendUserV230() {
    const raw = multiMode ? el('textArea').value : el('textInput').value;
    if (!raw.trim()) return;
    if (data.sound.onSend) playSound(data.sound.type);
    const extra = quoteTarget ? {quote: quoteTarget, quoteRef: pendingQuoteRef} : {};
    clearQuoteV230();
    if (multiMode) {
      const lines = raw.split('\n').map(line => line.trim()).filter(Boolean);
      if (!lines.length) return;
      el('textArea').value = '';
      let index = 0;
      (function next() {
        if (index >= lines.length) { scheduleReply(); return; }
        addMsg('me', lines[index], index === 0 ? extra : {});
        index++;
        if (index < lines.length) setTimeout(next, 350); else scheduleReply();
      })();
    } else {
      addMsg('me', raw.trim(), extra);
      el('textInput').value = '';
      scheduleReply();
    }
  }

  function resolveQuoteReference(message, index, role) {
    if (message.quoteRef && role.chat.some(item => item.id === message.quoteRef)) return message.quoteRef;
    for (let cursor = index - 1; cursor >= 0; cursor--) {
      const candidate = role.chat[cursor];
      if (messageText(candidate) === String(message.quote || '')) {
        message.quoteRef = ensureMessageId(candidate);
        return message.quoteRef;
      }
    }
    return '';
  }

  function enhanceRenderedChat() {
    const role = R();
    let changed = false;
    document.querySelectorAll('#chatArea [data-i]').forEach(node => {
      const index = Number(node.dataset.i);
      const message = role.chat[index];
      if (!message) return;
      if (!message.id) { ensureMessageId(message); changed = true; }
      node.dataset.msgId = message.id;
      const head = node.querySelector('.head');
      if (head) head.dataset.avatarSide = message.side === 'me' ? 'me' : 'other';
      const quote = node.querySelector('.quote');
      if (quote && message.quote) {
        const before = message.quoteRef;
        const ref = resolveQuoteReference(message, index, role);
        if (ref) {
          quote.dataset.quoteRef = ref;
          quote.setAttribute('role', 'button');
          quote.setAttribute('tabindex', '0');
          quote.setAttribute('aria-label', '跳转到被引用的消息');
        }
        if (!before && message.quoteRef) changed = true;
      }
    });
    if (changed) save();
  }

  function jumpToQuotedMessage(ref) {
    let target = [...document.querySelectorAll('#chatArea [data-msg-id]')].find(node => node.dataset.msgId === ref);
    if (!target) {
      renderChat(false);
      requestAnimationFrame(() => jumpToQuotedMessage(ref));
      return;
    }
    target.scrollIntoView({behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center'});
    target.classList.remove('quote-target-flash');
    requestAnimationFrame(() => target.classList.add('quote-target-flash'));
    setTimeout(() => target.classList.remove('quote-target-flash'), 1600);
  }

  let managedPokeAt = 0;
  let avatarTap = {key: '', time: 0};
  function selfPoke(index) {
    const now = Date.now();
    if (now - managedPokeAt < 700) return;
    managedPokeAt = now;
    const poke = String(R().pokes[index] || R().pokes[0] || '拍了拍自己');
    const action = poke.replace(/他的/g, '自己的').replace(/他/g, '自己');
    addSys('你' + action);
  }

  function installGestureIsolation(baseSendPoke) {
    sendPoke = function (index) {
      const target = window.event?.target?.closest?.('#chatArea .head,#topAvatar');
      const mine = target?.closest?.('.msg.me');
      if (mine) { selfPoke(index); return; }
      managedPokeAt = Date.now();
      return baseSendPoke(index);
    };
    document.addEventListener('touchstart', event => {
      if (event.target.closest?.('#chatArea .head,#topAvatar')) event.stopImmediatePropagation();
    }, true);
    document.addEventListener('click', event => {
      const quote = event.target.closest?.('.quote[data-quote-ref]');
      if (quote) {
        event.preventDefault();
        event.stopImmediatePropagation();
        jumpToQuotedMessage(quote.dataset.quoteRef);
        return;
      }
      const head = event.target.closest?.('#chatArea .head,#topAvatar');
      if (!head) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      const side = head.id === 'topAvatar' ? 'other' : (head.closest('.msg.me') ? 'me' : 'other');
      const key = side + ':' + (head.closest('.msg')?.dataset.msgId || head.id || 'avatar');
      const now = Date.now();
      if (avatarTap.key === key && now - avatarTap.time < 360) {
        avatarTap = {key: '', time: 0};
        if (now - managedPokeAt < 180) return;
        if (side === 'me') selfPoke(data.lastPoke || 0);
        else { managedPokeAt = now; baseSendPoke(data.lastPoke || 0); }
      } else avatarTap = {key, time: now};
    }, true);
    document.addEventListener('keydown', event => {
      const quote = event.target.closest?.('.quote[data-quote-ref]');
      if (quote && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault();
        jumpToQuotedMessage(quote.dataset.quoteRef);
      }
    });
  }

  function rebuildStickerSettings() {
    const root = el('cardSticker');
    if (!root || root.dataset.foldReady === 'true') return;
    root.dataset.foldReady = 'true';
    root.innerHTML =
      '<section class="sticker-fold" data-sticker-fold="emoji"><button type="button" class="sticker-fold-head" aria-expanded="true"><span class="sticker-fold-icon">' + uiSvg('smile') + '</span><span><b>Emoji 库</b><small>聊天表情面板使用</small></span><i>' + uiSvg('chevron') + '</i></button><div class="sticker-fold-body">' +
        '<div class="emoji-grid" id="emojiManage"></div><div class="setting-row"><label for="newEmoji">单个添加</label><div class="sticker-inline"><input id="newEmoji" placeholder="例如：🥰"><button type="button" class="pickbtn" onclick="addEmoji()">添加</button></div></div>' +
        '<div class="setting-row"><label for="emojiBatch">批量添加，一行一个</label><textarea id="emojiBatch" placeholder="🥰\n(｡・ω・｡)\n❤️"></textarea><button type="button" class="btn blue" onclick="addEmojiBatch()">批量添加</button></div></div></section>' +
      '<section class="sticker-fold" data-sticker-fold="images"><button type="button" class="sticker-fold-head" aria-expanded="false"><span class="sticker-fold-icon">' + uiSvg('image') + '</span><span><b>图片表情包</b><small>相册图片与动图</small></span><i>' + uiSvg('chevron') + '</i></button><div class="sticker-fold-body">' +
        '<div class="emoji-grid" id="stickerManage"></div><button type="button" class="btn dark" id="btnAddSticker">从相册添加表情</button><div class="sticker-batch-actions"><button type="button" class="btn blue" onclick="batchStickerText()">批量粘贴添加</button><button type="button" class="btn dark" onclick="batchStickerImg()">批量选图添加</button></div></div></section>';
    root.querySelectorAll('.sticker-fold').forEach(section => {
      const key = section.dataset.stickerFold;
      const button = section.querySelector('.sticker-fold-head');
      const sync = () => {
        const collapsed = Boolean(icepearPrefs().stickerFolds[key]);
        section.classList.toggle('collapsed', collapsed);
        button.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      };
      button.addEventListener('click', () => { icepearPrefs().stickerFolds[key] = !icepearPrefs().stickerFolds[key]; save(); sync(); });
      sync();
    });
    el('btnAddSticker').onclick = addSticker;
    builtInEmojiManage();
    builtInStickerManage();
  }

  function renderChatStickersV230() {
    const box = el('stickerTab');
    if (!box) return;
    const frequencies = {};
    Object.entries(data.freq || {}).forEach(([key, value]) => {
      if (!key.startsWith('st')) return;
      const index = Number(key.slice(2));
      if (data.stickers[index]) frequencies[index] = (frequencies[index] || 0) + Number(value || 0);
    });
    Object.entries(data.stFreq || {}).forEach(([key, value]) => {
      const index = Number(key);
      if (data.stickers[index]) frequencies[index] = (frequencies[index] || 0) + Number(value || 0);
    });
    const common = Object.keys(frequencies).map(Number).sort((a, b) => frequencies[b] - frequencies[a]).slice(0, 6);
    const imageButton = index => {
      const source = safeMedia(RS(data.stickers[index]));
      return source ? '<button type="button" class="sticker-thumb" onclick="sendSticker(' + index + ')"><img src="' + esc(source) + '" alt="发送表情" loading="lazy"></button>' : '';
    };
    const collapsed = Boolean(icepearPrefs().stickerFolds.chatAll);
    box.innerHTML = '<div class="chat-sticker-section"><h4>常用</h4><div class="emoji-grid">' + (common.length ? common.map(imageButton).join('') : '<div class="hint">发送过的表情会显示在这里</div>') + '</div></div>' +
      '<section class="chat-sticker-all' + (collapsed ? ' collapsed' : '') + '"><button type="button" class="chat-sticker-toggle" aria-expanded="' + (!collapsed) + '"><span>全部表情 <small>' + data.stickers.length + '</small></span>' + uiSvg('chevron') + '</button><div class="emoji-grid">' + (data.stickers.length ? data.stickers.map((_, index) => imageButton(index)).join('') : '<div class="hint">暂无表情包</div>') + '</div></section>';
    box.querySelector('.chat-sticker-toggle').addEventListener('click', () => { icepearPrefs().stickerFolds.chatAll = !collapsed; save(); renderChatStickersV230(); });
  }

  function configuredCardFallback() {
    const pool = allCards().map(value => String(value || '').trim()).filter(Boolean);
    if (!pool.length) return false;
    addMsg('other', pool[rand(0, pool.length - 1)]);
    return true;
  }

  function heSendConfiguredOnly() {
    const role = R();
    const kind = rand(0, 6);
    if (kind === 0) { configuredCardFallback(); return; }
    if (kind === 1) {
      if (data.emoji?.length) addMsg('other', data.emoji[rand(0, data.emoji.length - 1)]);
      else configuredCardFallback();
      return;
    }
    if (kind === 2) {
      if (data.stickers?.length) addMsg('other', {type: 'img', src: data.stickers[rand(0, data.stickers.length - 1)]});
      else configuredCardFallback();
      return;
    }
    if (kind === 6) {
      const locations = (data.hisLocs || []).filter(Boolean);
      if (locations.length) addMsg('other', {type: 'loc', text: locations[rand(0, locations.length - 1)]});
      else configuredCardFallback();
      return;
    }
    const type = kind === 3 ? 'red' : kind === 5 ? 'zhuan' : 'gift';
    if (Number(role.wallet.his) <= 0) {
      if (type === 'red' || type === 'zhuan') {
        const fallback = String(ensureBalanceFallback(role)[type] || '').trim();
        if (fallback) addMsg('other', fallback);
      } else configuredCardFallback();
      return;
    }
    let amount = 0;
    const details = {};
    if (type === 'gift') {
      const affordable = role.shop.filter(item => Number(item.price) > 0 && Number(item.price) <= Number(role.wallet.his));
      if (!affordable.length) { configuredCardFallback(); return; }
      const gift = affordable[rand(0, affordable.length - 1)];
      amount = Number(gift.price);
      details.gift = gift.name;
      details.price = amount;
    } else {
      amount = Math.min(Number(role.wallet.his), rand(type === 'red' ? 5 : 10, type === 'red' ? 200 : 500));
      amount = Math.max(0.01, Math.round(amount * 100) / 100);
    }
    role.wallet.his -= amount;
    addMsg('other', Object.assign({type, amount, read: true, txVersion: 2, txStatus: ''}, details));
    updateWallet();
  }

  function installIcepearV230() {
    document.documentElement.dataset.icepearVersion = '2.3.0-installing';
    ensureV230Data();
    ensureBalanceFallbackSettings();
    renderStickerManage = builtInStickerManage;
    renderEmojiManage = builtInEmojiManage;
    renderShopList = renderShopExperience;
    setShopCat = categoryId => { icepearPrefs().shopActive[data.activeRole] = categoryId; save(); renderShopExperience(); };
    renderMoments = renderMomentsV230;
    likeMoment = likeMomentV230;
    commentMoment = commentMomentV230;
    window.myPost = publishMomentV230;
    renderStickers = renderChatStickersV230;
    heSendRandom = heSendConfiguredOnly;
    setQuote = setQuoteV230;
    clearQuote = clearQuoteV230;
    sendUser = sendUserV230;
    doQuote = function () { const message = R().chat[actionIdx]; if (message) beginMessageQuote(message, actionIdx); closeAction(); };
    el('btnSend').onclick = sendUserV230;
    const baseSendPoke = sendPoke;
    if (!icepearInstalled) installGestureIsolation(baseSendPoke);
    const previousRenderChat = renderChat;
    renderChat = function (forceScroll) {
      previousRenderChat(forceScroll);
      requestAnimationFrame(enhanceRenderedChat);
    };
    const previousCardTab = cardTab;
    cardTab = function (tab) { previousCardTab(tab); if (tab === 'sticker') { rebuildStickerSettings(); builtInEmojiManage(); builtInStickerManage(); } };
    const previousApplyTheme = applyTheme;
    applyTheme = function () {
      previousApplyTheme();
      const dark = document.body.classList.contains('dark');
      document.documentElement.classList.toggle('dark-root', dark);
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#171316' : (data.beauty?.topBg || '#f8f3ef'));
      try { window.AndroidBridge?.setSystemBars(dark); } catch (_) {}
      applyBeauty();
    };
    if (!icepearInstalled) {
      const previousApplyAllV230 = applyAll;
      applyAll = function () {
        ensureV230Data();
        previousApplyAllV230();
        ensureBalanceFallbackSettings();
        prepareShopPage();
        renderShopExperience();
        rebuildStickerSettings();
        if (el('pageMoments')?.classList.contains('active')) renderMomentsV230();
        applyTheme();
      };
    }
    icepearInstalled = true;
    prepareShopPage();
    rebuildStickerSettings();
    renderShopExperience();
    renderChatStickersV230();
    renderChat(false);
    applyTheme();
    save();
    document.documentElement.dataset.icepearVersion = '2.3.0';
  }

  /* ---------- 启动与统一刷新 ---------- */
  const originalApplyAll = applyAll;
  applyAll = function () {
    originalApplyAll();
    applyBeauty();
    renderCustomSounds();
    renderPatchList();
  };

  replayPatches();
  /* Old user patches may still replace legacy interaction handlers. Keep the upgraded built-ins authoritative. */
  openAction = openMessageContextMenu;
  closeAction = closeMessageMenu;
  installVideoMiniInteractions(true);
  installIcepearV230();
  applyAll();
  renderEmojiManage();
  renderStickerManage();
})();
