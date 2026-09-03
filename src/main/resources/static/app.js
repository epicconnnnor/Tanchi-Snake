/*
 * Tanchi Snake browser client.
 *
 * The client holds no game state and predicts nothing. Every frame drawn comes
 * straight out of the last state message; input is sent and then forgotten.
 */
(function () {
  'use strict';

  // --- identity -----------------------------------------------------------
  // sessionStorage, not localStorage: two tabs in one browser must be two
  // different players, and localStorage is shared across them.
  var STORE_PLAYER = 'tanchi.playerId';
  var STORE_ROOM = 'tanchi.room';

  function remember(playerId, room) {
    try {
      sessionStorage.setItem(STORE_PLAYER, playerId);
      sessionStorage.setItem(STORE_ROOM, room);
    } catch (ignored) {
      // Private mode and the like. Identity just will not survive a reload.
    }
  }

  function recall(key) {
    try {
      return sessionStorage.getItem(key);
    } catch (ignored) {
      return null;
    }
  }

  function forget() {
    try {
      sessionStorage.removeItem(STORE_PLAYER);
      sessionStorage.removeItem(STORE_ROOM);
    } catch (ignored) {
      // Nothing to do.
    }
  }

  // --- elements -----------------------------------------------------------

  var el = {
    banner: document.getElementById('banner'),
    screenName: document.getElementById('screen-name'),
    marqueeCode: document.getElementById('marquee-code'),
    marqueeCodeValue: document.getElementById('marquee-code-value'),

    menu: document.getElementById('menu'),
    lobby: document.getElementById('lobby'),
    game: document.getElementById('game'),
    results: document.getElementById('results'),

    menuControls: document.getElementById('menu-controls'),
    lobbyControls: document.getElementById('lobby-controls'),
    gameControls: document.getElementById('game-controls'),
    resultsControls: document.getElementById('results-controls'),

    menuForm: document.getElementById('menu-form'),
    name: document.getElementById('name'),
    room: document.getElementById('room'),
    roomField: document.getElementById('room-field'),
    soloBtn: document.getElementById('solo-btn'),
    createBtn: document.getElementById('create-btn'),
    joinBtn: document.getElementById('join-btn'),

    lobbyCode: document.getElementById('lobby-code'),
    copyCode: document.getElementById('copy-code'),
    copyStatus: document.getElementById('copy-status'),
    lobbyPlayers: document.getElementById('lobby-players'),
    lobbyHint: document.getElementById('lobby-hint'),
    readyBtn: document.getElementById('ready-btn'),
    startBtn: document.getElementById('start-btn'),
    leaveLobbyBtn: document.getElementById('leave-lobby-btn'),
    leaveGameBtn: document.getElementById('leave-game-btn'),

    board: document.getElementById('board'),
    gameClock: document.getElementById('game-clock'),
    scoreboard: document.getElementById('scoreboard'),
    dpad: document.querySelectorAll('.dpad-cell'),

    standings: document.getElementById('standings'),
    againBtn: document.getElementById('again-btn'),
    resultsHint: document.getElementById('results-hint'),

    logoBody: document.getElementById('logo-body'),
    logoDot: document.getElementById('logo-dot')
  };

  var ctx = el.board.getContext('2d');

  // --- connection ---------------------------------------------------------

  var socket = null;
  var myPlayerId = recall(STORE_PLAYER);
  var lastState = null;

  /*
   * Solo is a room like any other, just one nobody else is invited to. The
   * client asks for the round itself the moment the empty lobby arrives, so
   * there is no server rule about how many players a round needs.
   */
  var startAlone = false;

  /** Set while we hang up on purpose, so the close reads as intended. */
  var quitting = false;

  function socketUrl() {
    var scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return scheme + '//' + location.host + '/ws';
  }

  /** Opens the socket, running onOpen once it is ready to carry messages. */
  function connect(onOpen) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      onOpen();
      return;
    }
    socket = new WebSocket(socketUrl());
    socket.addEventListener('open', onOpen);
    socket.addEventListener('message', onMessage);
    socket.addEventListener('close', function () {
      // A socket we closed on the way out of a room is not a fault.
      if (quitting) {
        quitting = false;
        return;
      }
      showBanner('Disconnected. Reload the page to play again.');
    });
    socket.addEventListener('error', function () {
      if (!quitting) {
        showBanner('Connection error.');
      }
    });
  }

  function send(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload));
    }
  }

  // --- messages -----------------------------------------------------------

  function onMessage(event) {
    var message;
    try {
      message = JSON.parse(event.data);
    } catch (unparseable) {
      showBanner('Received something unreadable from the server.');
      return;
    }

    if (message.type === 'joined') {
      myPlayerId = message.you;
      remember(message.you, message.room);
      clearBanner();
    } else if (message.type === 'state') {
      lastState = message;
      if (startAlone && message.phase === 'LOBBY') {
        startAlone = false;
        send({ type: 'start' });
        return; // the round's own state message is a tick away
      }
      render(message);
    } else if (message.type === 'error') {
      showBanner(message.message || 'Something went wrong.');
      // A resume against a room that is gone leaves us holding a dead id.
      if (message.message === 'no such room') {
        forget();
        myPlayerId = null;
        show('menu');
      }
    }
  }

  // --- screens ------------------------------------------------------------

  /*
   * Four screens, one chassis. Each entry names the panel behind the bezel,
   * the control set under it, and what the marquee and the tab should say.
   */
  var SCREENS = {
    menu: { panel: el.menu, controls: el.menuControls, name: 'MENU', title: 'Tanchi Snake' },
    lobby: { panel: el.lobby, controls: el.lobbyControls, name: 'LOBBY', title: 'Lobby' },
    game: { panel: el.game, controls: el.gameControls, name: 'GAME', title: 'Playing' },
    results: { panel: el.results, controls: el.resultsControls, name: 'RESULTS', title: 'Results' }
  };

  var current = null;

  function show(which) {
    Object.keys(SCREENS).forEach(function (key) {
      SCREENS[key].panel.hidden = key !== which;
      SCREENS[key].controls.hidden = key !== which;
    });

    var screen = SCREENS[which];
    el.screenName.textContent = screen.name;

    // The code sits beside the screen name while a round is on, where the
    // lobby's big gold code would otherwise be out of reach.
    if (which !== 'game') {
      el.marqueeCode.hidden = true;
      el.marqueeCodeValue.textContent = '';
    }

    document.title = which === 'menu' ? screen.title : screen.title + ' · Tanchi Snake';

    if (which !== current) {
      current = which;
      // The wordmark only runs where there is nothing else moving.
      if (which === 'menu') {
        startLogo();
      } else {
        stopLogo();
      }
    }
  }

  function showBanner(text) {
    el.banner.textContent = text;
    el.banner.hidden = false;
  }

  function clearBanner() {
    el.banner.hidden = true;
    el.banner.textContent = '';
  }

  function me(state) {
    for (var i = 0; i < state.players.length; i++) {
      if (state.players[i].playerId === myPlayerId) {
        return state.players[i];
      }
    }
    return null;
  }

  function render(state) {
    if (state.phase === 'LOBBY') {
      show('lobby');
      renderLobby(state);
    } else if (state.phase === 'RESULTS') {
      show('results');
      renderResults(state);
    } else {
      show('game');
      renderGame(state);
    }
  }

  /*
   * Walking out. The seat goes back straight away rather than sitting empty
   * for the reconnect window, and the stored identity goes with it: whoever
   * comes back is a newcomer, not this player returning.
   */
  function leaveRoom() {
    send({ type: 'leave' });
    forget();
    myPlayerId = null;
    lastState = null;
    startAlone = false;
    if (socket) {
      quitting = true;
      socket.close();
      socket = null;
    }
    clearBanner();
    closeRoomField();
    show('menu');
  }

  // --- shared row pattern -------------------------------------------------

  /*
   * One row shape on all three list screens: panel background, the player's
   * colour on the left edge and nowhere else, a name that flexes, and a
   * right-aligned mono value.
   */
  function row(colorIndex) {
    var li = document.createElement('li');
    if (typeof colorIndex === 'number') {
      li.style.borderLeftColor = paletteOf(colorIndex).body;
    }
    return li;
  }

  function nameCell(text, isMe) {
    var span = document.createElement('span');
    span.className = 'row-name';
    span.textContent = text;
    span.title = text;
    if (isMe) {
      var mine = document.createElement('span');
      mine.className = 'sr-only';
      mine.textContent = ' (you)';
      span.appendChild(mine);
    }
    return span;
  }

  function valueCell(text, extraClass) {
    var span = document.createElement('span');
    span.className = 'row-value' + (extraClass ? ' ' + extraClass : '');
    span.textContent = text;
    return span;
  }

  function tag(text, extraClass) {
    var span = document.createElement('span');
    span.className = 'tag' + (extraClass ? ' ' + extraClass : '');
    span.textContent = text;
    return span;
  }

  /** The white ring the board draws on your own head, repeated in the list. */
  function youRing() {
    var ring = document.createElement('span');
    ring.className = 'you-ring';
    ring.setAttribute('aria-hidden', 'true');
    return ring;
  }

  function emptyNote(list, text) {
    var li = document.createElement('li');
    li.className = 'empty';
    li.textContent = text;
    list.appendChild(li);
  }

  // --- lobby --------------------------------------------------------------

  function renderLobby(state) {
    el.lobbyCode.textContent = state.room;
    el.copyCode.setAttribute('aria-label', 'Copy room code ' + state.room.split('').join(' '));
    el.lobbyPlayers.innerHTML = '';

    if (state.players.length === 0) {
      emptyNote(el.lobbyPlayers, 'Nobody here yet.');
    }

    state.players.forEach(function (player) {
      var li = row(player.colorIndex);
      var isMe = player.playerId === myPlayerId;
      if (!player.connected) {
        li.className = 'is-away';
      }

      if (isMe) {
        li.appendChild(youRing());
      }
      li.appendChild(nameCell(player.name, isMe));
      if (player.host) {
        li.appendChild(tag('HOST', 'tag--host'));
      }
      if (!player.connected) {
        li.appendChild(tag('AWAY'));
      }
      li.appendChild(player.ready
        ? valueCell('READY', 'is-ready')
        : valueCell('WAITING'));

      el.lobbyPlayers.appendChild(li);
    });

    var mine = me(state);
    var isReady = !!mine && mine.ready;
    el.readyBtn.textContent = isReady ? 'Not ready' : 'Ready';
    el.readyBtn.classList.toggle('is-on', isReady);
    el.readyBtn.setAttribute('aria-pressed', isReady ? 'true' : 'false');

    var isHost = !!mine && mine.host;
    el.startBtn.hidden = !isHost;
    el.lobbyHint.textContent = isHost
      ? 'You are the host. Start whenever you like.'
      : 'Waiting for the host to start.';
  }

  // --- copy the room code -------------------------------------------------

  var copyResetTimer = null;

  function copyStatus(text, done) {
    el.copyStatus.textContent = text;
    el.copyStatus.classList.toggle('is-done', !!done);
    clearTimeout(copyResetTimer);
    copyResetTimer = setTimeout(function () {
      el.copyStatus.textContent = 'Click to copy';
      el.copyStatus.classList.remove('is-done');
    }, 2000);
  }

  /*
   * A LAN game is served over plain http, where navigator.clipboard does not
   * exist, so the old execCommand path has to stay as the fallback.
   */
  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var scratch = document.createElement('textarea');
      scratch.value = text;
      scratch.setAttribute('readonly', '');
      scratch.style.position = 'fixed';
      scratch.style.opacity = '0';
      document.body.appendChild(scratch);
      scratch.select();
      var ok = false;
      try {
        ok = document.execCommand('copy');
      } catch (blocked) {
        ok = false;
      }
      document.body.removeChild(scratch);
      return ok ? resolve() : reject(new Error('copy refused'));
    });
  }

  el.copyCode.addEventListener('click', function () {
    var code = el.lobbyCode.textContent;
    copyText(code).then(function () {
      copyStatus('Copied ' + code, true);
    }, function () {
      copyStatus('Press Ctrl+C to copy');
    });
  });

  // --- game ---------------------------------------------------------------

  var COLORS = {
    grid: '#1b1f25',
    seam: '#333b47',
    food: '#f5f5f4',
    frozen: '#e5e7eb',
    self: '#ffffff'
  };

  // 48 cells of 12 CSS pixels. Both are fixed: a fractional cell renders blurry.
  var BOARD_PX = 576;

  // GameLoop.TICK_MILLIS. The round clock is derived from the tick count.
  var TICK_MS = 100;

  /** Food radius in CSS pixels, by what the food is worth. */
  var FOOD_RADIUS = { 1: 3, 2: 5, 3: 7 };

  /*
   * One colour per seat, in join order. Lightness is deliberately varied as
   * well as hue: green and red read as the same muddy tone to a red-green
   * colourblind player, so the green is a pale mint and the red is dark. The
   * white head outline on your own snake means nobody has to tell colours
   * apart to find themselves.
   */
  var PALETTE = [
    { body: '#4ade80', head: '#bbf7d0', name: 'green' },
    { body: '#3b82f6', head: '#bfdbfe', name: 'blue' },
    { body: '#b91c1c', head: '#fca5a5', name: 'red' },
    { body: '#facc15', head: '#fef08a', name: 'yellow' },
    { body: '#a855f7', head: '#e9d5ff', name: 'purple' },
    { body: '#f97316', head: '#fed7aa', name: 'orange' },
    { body: '#22d3ee', head: '#a5f3fc', name: 'cyan' },
    { body: '#ec4899', head: '#fbcfe8', name: 'pink' }
  ];

  function paletteOf(colorIndex) {
    if (typeof colorIndex !== 'number' || colorIndex < 0 || colorIndex >= PALETTE.length) {
      return PALETTE[0];
    }
    return PALETTE[colorIndex];
  }

  /** A small square of a player's colour, for the results list. */
  function swatch(colorIndex) {
    var box = document.createElement('span');
    box.className = 'swatch';
    box.style.background = paletteOf(colorIndex).body;
    box.title = paletteOf(colorIndex).name;
    return box;
  }

  function renderGame(state) {
    el.gameClock.textContent = clock(state.tick);
    el.marqueeCodeValue.textContent = state.room;
    el.marqueeCode.hidden = false;

    drawBoard(state);
    drawScores(state);
    lightDpad(state);
  }

  /** Ticks elapsed as mm:ss. The server ticks every TICK_MS. */
  function clock(tick) {
    var seconds = Math.floor((tick * TICK_MS) / 1000);
    var minutes = Math.floor(seconds / 60);
    return pad(minutes) + ':' + pad(seconds % 60);
  }

  function pad(n) {
    return (n < 10 ? '0' : '') + n;
  }

  /*
   * Matches the backing store to the display. The canvas keeps its 576 CSS
   * pixels and its 12px cells either way; all that changes is how many device
   * pixels sit behind each one, which is what stops one cell coming out a
   * pixel wider than the next on a scaled display.
   */
  var boardDpr = 0;

  function sizeBoard() {
    var dpr = window.devicePixelRatio || 1;
    if (dpr !== boardDpr) {
      boardDpr = dpr;
      el.board.width = Math.round(BOARD_PX * dpr);
      el.board.height = Math.round(BOARD_PX * dpr);
      el.board.style.width = BOARD_PX + 'px';
      el.board.style.height = BOARD_PX + 'px';
      // Resizing resets the context, so the scale goes on afterwards. Every
      // draw below is then in CSS pixels.
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    return dpr;
  }

  /** The D-pad reports the direction the server last had you facing. */
  function lightDpad(state) {
    var facing = null;
    state.snakes.forEach(function (snake) {
      if (snake.id === myPlayerId) {
        facing = snake.direction;
      }
    });

    for (var i = 0; i < el.dpad.length; i++) {
      var cell = el.dpad[i];
      var lit = facing !== null && cell.classList.contains('dpad-' + facing.toLowerCase());
      cell.classList.toggle('is-lit', lit);
    }
  }

  function drawBoard(state) {
    var dpr = sizeBoard();
    var size = BOARD_PX;
    var cell = size / state.width;
    // One device pixel, whatever the display is doing.
    var hair = 1 / dpr;

    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#0b0d10';
    ctx.fillRect(0, 0, size, size);

    /*
     * Grid lines are filled rectangles on the cell boundaries, not strokes.
     * A stroke straddles the coordinate it is drawn on, so it lands between
     * device pixels and leaves one cell looking a pixel wider than the next;
     * a fill starting on the boundary is the same width every time.
     */
    ctx.fillStyle = COLORS.grid;
    for (var i = 1; i < state.width; i++) {
      var at = i * cell;
      ctx.fillRect(at, 0, hair, size);
      ctx.fillRect(0, at, size, hair);
    }

    // The edges wrap, so they are a seam rather than a wall. Dashed, to say
    // that a snake goes through it and comes back on the far side.
    ctx.strokeStyle = COLORS.seam;
    ctx.lineWidth = hair * 2;
    ctx.setLineDash([cell / 2, cell / 2]);
    ctx.strokeRect(0, 0, size, size);
    ctx.setLineDash([]);

    // Food is a pale dot rather than a colour any player might be wearing.
    // The bigger the dot, the more it is worth.
    ctx.fillStyle = COLORS.food;
    state.food.forEach(function (f) {
      var mid = cell / 2;
      ctx.beginPath();
      ctx.arc(f.x * cell + mid, f.y * cell + mid, radiusOf(f.value), 0, Math.PI * 2);
      ctx.fill();
    });

    state.snakes.forEach(function (snake) {
      drawSnake(snake, cell);
    });
  }

  function radiusOf(value) {
    return FOOD_RADIUS[value] || FOOD_RADIUS[1];
  }

  function drawSnake(snake, cell) {
    var colors = paletteOf(snake.colorIndex);

    snake.body.forEach(function (p, index) {
      ctx.fillStyle = index === 0 ? colors.head : colors.body;
      ctx.fillRect(p.x * cell, p.y * cell, cell, cell);
    });

    // A stunned snake is frozen but still lethal, which is worth seeing.
    if (snake.stunned && snake.body.length > 0) {
      ctx.strokeStyle = COLORS.frozen;
      ctx.lineWidth = 1;
      snake.body.forEach(function (p) {
        ctx.strokeRect(p.x * cell + 0.5, p.y * cell + 0.5, cell - 1, cell - 1);
      });
    }

    // Your own head is ringed in white. Colour alone is not enough to find
    // yourself at a glance, and it has to survive a colourblind player too.
    if (snake.id === myPlayerId && snake.body.length > 0) {
      var head = snake.body[0];
      ctx.strokeStyle = COLORS.self;
      ctx.lineWidth = 2;
      ctx.strokeRect(head.x * cell + 1, head.y * cell + 1, cell - 2, cell - 2);
    }
  }

  function drawScores(state) {
    var snakeById = {};
    state.snakes.forEach(function (snake) {
      snakeById[snake.id] = snake;
    });

    var rows = state.players.map(function (player) {
      var snake = snakeById[player.playerId];
      return {
        name: player.name,
        colorIndex: player.colorIndex,
        connected: player.connected,
        isMe: player.playerId === myPlayerId,
        level: snake ? snake.level : 0,
        alive: !!snake,
        stunned: !!snake && snake.stunned
      };
    });

    rows.sort(function (a, b) {
      return b.level - a.level;
    });

    el.scoreboard.innerHTML = '';

    if (rows.length === 0) {
      emptyNote(el.scoreboard, 'No players.');
      return;
    }

    rows.forEach(function (r) {
      var li = row(r.colorIndex);
      if (!r.connected) {
        li.className = 'is-away';
      }

      if (r.isMe) {
        li.appendChild(youRing());
      }
      li.appendChild(nameCell(r.name, r.isMe));
      if (!r.alive) {
        li.appendChild(tag('OUT'));
      } else if (r.stunned) {
        li.appendChild(tag('FROZEN'));
      }
      li.appendChild(valueCell('LV ' + r.level, 'row-value--number'));

      el.scoreboard.appendChild(li);
    });
  }

  // --- results ------------------------------------------------------------

  function renderResults(state) {
    // Standings carry no colour, so the seat colours come off the player list.
    // Standing.sessionId holds a playerId, the same way winnerPlayerId does.
    var colorByPlayer = {};
    state.players.forEach(function (player) {
      colorByPlayer[player.playerId] = player.colorIndex;
    });

    el.standings.innerHTML = '';

    var table = state.standings || [];
    if (table.length === 0) {
      emptyNote(el.standings, 'No standings for this round.');
    }

    table.forEach(function (standing) {
      var playerId = standing.sessionId;
      var isMe = playerId === myPlayerId;
      var li = document.createElement('li');

      // The left edge is the medal here, so the seat colour moves to a swatch.
      li.className = 'standing standing--' + (standing.rank <= 3 ? standing.rank : 'rest');
      if (!standing.connected) {
        li.classList.add('is-away');
      }

      var rank = document.createElement('span');
      rank.className = 'standing-rank';
      rank.textContent = standing.rank;
      li.appendChild(rank);

      li.appendChild(swatch(colorByPlayer[playerId]));
      li.appendChild(nameCell(standing.name, isMe));

      if (playerId === state.winnerPlayerId) {
        li.appendChild(tag('WINNER', 'tag--winner'));
      }
      if (!standing.connected) {
        li.appendChild(tag('AWAY'));
      }
      li.appendChild(valueCell('LV ' + standing.level, 'row-value--number'));

      el.standings.appendChild(li);
    });

    // Only the host can reopen the lobby, so only the host gets the button.
    var mine = me(state);
    var isHost = !!mine && mine.host;
    el.againBtn.hidden = !isHost;
    el.resultsHint.hidden = isHost;
  }

  // --- logo ---------------------------------------------------------------

  /*
   * The wordmark is the game's own grid. Eight cells trace an S-curve - three
   * right, two down, three right - with food one cell past the head. Every
   * 400ms the snake takes a step; it eats on the second, grows by one the way
   * the board would, runs out the tail and starts over.
   */
  var LOGO_PATH = [
    [0, 0], [1, 0], [2, 0],
    [2, 1], [2, 2],
    [3, 2], [4, 2], [5, 2],
    [6, 2], [7, 2], [8, 2]
  ];

  // [first cell of the body, last cell (the head), is the food still there]
  var LOGO_FRAMES = [
    [0, 7, true],
    [0, 8, false],
    [1, 9, false],
    [2, 10, false]
  ];

  var LOGO_STEP_MS = 400;

  /** Passes before the mark settles back to rest. Four frames each. */
  var LOGO_CYCLES = 3;

  var logoCells = [];
  var logoTimer = null;
  var logoFrame = 0;

  function buildLogo() {
    if (!el.logoBody) {
      return;
    }
    logoCells = Array.prototype.slice.call(el.logoBody.querySelectorAll('.logo-cell'));

    // The markup holds the eight cells of the resting mark. Eating grows the
    // snake to nine, so one more cell is needed before the loop can run.
    var extra = logoCells[0].cloneNode(false);
    extra.classList.remove('logo-cell--head');
    el.logoBody.appendChild(extra);
    logoCells.push(extra);

    // Draw the resting frame, which is what hides that ninth cell until the
    // snake has eaten its way up to it.
    drawLogo(0);
  }

  function drawLogo(index) {
    var frame = LOGO_FRAMES[index];
    var tail = frame[0];
    var head = frame[1];

    for (var i = 0; i < logoCells.length; i++) {
      var at = tail + i;
      var on = at <= head;
      var cell = logoCells[i];
      cell.style.display = on ? '' : 'none';
      if (on) {
        cell.setAttribute('x', (LOGO_PATH[at][0] + 0.07).toFixed(2));
        cell.setAttribute('y', (LOGO_PATH[at][1] + 0.07).toFixed(2));
        cell.classList.toggle('logo-cell--head', at === head);
      }
    }

    el.logoDot.style.display = frame[2] ? '' : 'none';
  }

  function startLogo() {
    if (logoTimer !== null || logoCells.length === 0) {
      return;
    }
    // Decorative motion. Anyone who has asked for less gets the resting mark.
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    var stepsLeft = LOGO_CYCLES * LOGO_FRAMES.length;
    logoTimer = setInterval(function () {
      logoFrame = (logoFrame + 1) % LOGO_FRAMES.length;
      drawLogo(logoFrame);
      if (--stepsLeft <= 0) {
        stopLogo();
      }
    }, LOGO_STEP_MS);
  }

  function stopLogo() {
    if (logoTimer !== null) {
      clearInterval(logoTimer);
      logoTimer = null;
    }
    logoFrame = 0;
    if (logoCells.length > 0) {
      drawLogo(0);
    }
  }

  // --- input --------------------------------------------------------------

  var KEYS = {
    ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
    w: 'UP', a: 'LEFT', s: 'DOWN', d: 'RIGHT',
    W: 'UP', A: 'LEFT', S: 'DOWN', D: 'RIGHT'
  };

  document.addEventListener('keydown', function (event) {
    // Never steal keystrokes from the name and room fields.
    if (event.target && event.target.tagName === 'INPUT') {
      return;
    }
    var direction = KEYS[event.key];
    if (!direction) {
      return;
    }
    // Stop the arrow keys scrolling the page out from under the board.
    event.preventDefault();

    // Reversals are the server's rule to enforce, not ours. Send and forget.
    send({ type: 'turn', dir: direction });
  });

  // --- actions ------------------------------------------------------------

  function createRoom(alone) {
    clearBanner();
    startAlone = !!alone;
    var name = el.name.value.trim();
    connect(function () {
      send({ type: 'create', name: name });
    });
  }

  /** The code field only appears once somebody says they have a code. */
  function openRoomField() {
    el.roomField.hidden = false;
    el.joinBtn.setAttribute('aria-expanded', 'true');
    el.joinBtn.textContent = 'Join';
    el.room.focus();
  }

  function closeRoomField() {
    el.roomField.hidden = true;
    el.room.value = '';
    el.joinBtn.setAttribute('aria-expanded', 'false');
    el.joinBtn.textContent = 'Join via game code';
  }

  function joinRoom() {
    clearBanner();
    // First press asks for the code; the next one uses it.
    if (el.roomField.hidden) {
      openRoomField();
      return;
    }
    var code = el.room.value.trim().toUpperCase();
    if (!code) {
      showBanner('Enter a game code to join.');
      el.room.focus();
      return;
    }
    var name = el.name.value.trim();
    startAlone = false;
    connect(function () {
      send({ type: 'join', room: code, name: name });
    });
  }

  // Both buttons submit the one form, which is what carries Enter into here.
  el.menuForm.addEventListener('submit', function (event) {
    event.preventDefault();
    if (event.submitter === el.joinBtn) {
      joinRoom();
    } else {
      createRoom(event.submitter === el.soloBtn);
    }
  });

  /*
   * Implicit submission would always pick the first button in the form,
   * whichever field you were in. Each field says for itself what Enter means:
   * a name on its own creates a room, a room code joins one.
   */
  /*
   * Enter in the name field means the code field if it is open -- somebody
   * halfway through joining a game is not asking to start a solo round.
   */
  el.name.addEventListener('keydown', function (event) {
    if (event.key !== 'Enter') {
      return;
    }
    event.preventDefault();
    if (!el.roomField.hidden) {
      el.room.focus();
      return;
    }
    createRoom(true);
  });

  el.room.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      joinRoom();
    }
  });

  el.readyBtn.addEventListener('click', function () {
    send({ type: 'ready' });
  });

  el.startBtn.addEventListener('click', function () {
    send({ type: 'start' });
  });

  el.againBtn.addEventListener('click', function () {
    send({ type: 'playagain' });
  });

  el.leaveLobbyBtn.addEventListener('click', leaveRoom);

  /*
   * Mid-round, leaving costs the player their snake and their placing, so it
   * asks first -- the same question closing the tab would have put to them.
   */
  el.leaveGameBtn.addEventListener('click', function () {
    var running = lastState && lastState.phase === 'RUNNING';
    if (running && !window.confirm('Leave the round? Your snake goes with you.')) {
      return;
    }
    leaveRoom();
  });

  /*
   * Only while a round is on. Reloading from the menu, the lobby or the
   * results screen costs nothing, and a prompt there is just an obstacle.
   */
  window.addEventListener('beforeunload', function (event) {
    if (lastState && lastState.phase === 'RUNNING') {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  // --- boot ---------------------------------------------------------------

  buildLogo();

  var savedRoom = recall(STORE_ROOM);
  if (myPlayerId && savedRoom) {
    // The server needs the room as well as the id to place a returning player.
    show('menu');
    connect(function () {
      send({ type: 'join', room: savedRoom, you: myPlayerId });
    });
  } else {
    show('menu');
  }
}());
