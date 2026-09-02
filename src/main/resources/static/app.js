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
    menu: document.getElementById('menu'),
    lobby: document.getElementById('lobby'),
    game: document.getElementById('game'),
    name: document.getElementById('name'),
    room: document.getElementById('room'),
    createBtn: document.getElementById('create-btn'),
    joinBtn: document.getElementById('join-btn'),
    lobbyCode: document.getElementById('lobby-code'),
    lobbyPlayers: document.getElementById('lobby-players'),
    lobbyHint: document.getElementById('lobby-hint'),
    readyBtn: document.getElementById('ready-btn'),
    startBtn: document.getElementById('start-btn'),
    board: document.getElementById('board'),
    gameCode: document.getElementById('game-code'),
    gameTick: document.getElementById('game-tick'),
    gamePhase: document.getElementById('game-phase'),
    scoreboard: document.getElementById('scoreboard')
  };

  var ctx = el.board.getContext('2d');

  // --- connection ---------------------------------------------------------

  var socket = null;
  var myPlayerId = recall(STORE_PLAYER);
  var lastState = null;

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
      showBanner('Disconnected. Reload the page to play again.');
    });
    socket.addEventListener('error', function () {
      showBanner('Connection error.');
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

  function show(which) {
    el.menu.hidden = which !== 'menu';
    el.lobby.hidden = which !== 'lobby';
    el.game.hidden = which !== 'game';
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
    } else {
      show('game');
      renderGame(state);
    }
  }

  // --- lobby --------------------------------------------------------------

  function renderLobby(state) {
    el.lobbyCode.textContent = state.room;
    el.lobbyPlayers.innerHTML = '';

    state.players.forEach(function (player) {
      var row = document.createElement('li');
      if (!player.connected) {
        row.className = 'away';
      }

      var name = document.createElement('span');
      name.textContent = player.name;
      if (player.playerId === myPlayerId) {
        name.className = 'you';
        name.textContent = player.name + ' (you)';
      }

      var tags = document.createElement('span');
      tags.className = 'tags';
      var parts = [];
      if (player.host) {
        parts.push('host');
      }
      parts.push(player.ready ? 'ready' : 'not ready');
      if (!player.connected) {
        parts.push('away');
      }
      tags.textContent = parts.join(' · ');

      row.appendChild(name);
      row.appendChild(tags);
      el.lobbyPlayers.appendChild(row);
    });

    var mine = me(state);
    el.readyBtn.textContent = mine && mine.ready ? 'Not ready' : 'Ready up';
    el.readyBtn.className = mine && mine.ready ? 'primary' : '';

    var isHost = !!mine && mine.host;
    el.startBtn.hidden = !isHost;
    el.lobbyHint.textContent = isHost
      ? 'You are the host. Start whenever you like.'
      : 'Waiting for the host to start.';
  }

  // --- game ---------------------------------------------------------------

  var COLORS = {
    grid: '#1b1f25',
    wall: '#4b5563',
    food: '#fbbf24',
    mine: '#4ade80',
    mineHead: '#bbf7d0',
    theirs: '#60a5fa',
    theirsHead: '#bfdbfe',
    frozen: '#e5e7eb'
  };

  function renderGame(state) {
    el.gameCode.textContent = state.room;
    el.gameTick.textContent = state.tick;
    el.gamePhase.textContent = state.phase;

    drawBoard(state);
    drawScores(state);
  }

  function drawBoard(state) {
    var size = el.board.width;
    var cell = size / state.width;

    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#0b0d10';
    ctx.fillRect(0, 0, size, size);

    // Faint grid, so a cell of movement is readable while tuning.
    ctx.strokeStyle = COLORS.grid;
    ctx.lineWidth = 1;
    for (var i = 1; i < state.width; i++) {
      var at = Math.round(i * cell) + 0.5;
      ctx.beginPath();
      ctx.moveTo(at, 0);
      ctx.lineTo(at, size);
      ctx.moveTo(0, at);
      ctx.lineTo(size, at);
      ctx.stroke();
    }

    // Walls: the board edge is lethal, so make it unmistakable.
    ctx.strokeStyle = COLORS.wall;
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, size - 2, size - 2);

    ctx.fillStyle = COLORS.food;
    state.food.forEach(function (f) {
      var pad = cell * 0.25;
      ctx.fillRect(f.x * cell + pad, f.y * cell + pad, cell - pad * 2, cell - pad * 2);
    });

    state.snakes.forEach(function (snake) {
      drawSnake(snake, cell);
    });
  }

  function drawSnake(snake, cell) {
    var mine = snake.id === myPlayerId;
    var body = mine ? COLORS.mine : COLORS.theirs;
    var head = mine ? COLORS.mineHead : COLORS.theirsHead;

    snake.body.forEach(function (p, index) {
      ctx.fillStyle = index === 0 ? head : body;
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
  }

  function drawScores(state) {
    var levelById = {};
    state.snakes.forEach(function (snake) {
      levelById[snake.id] = snake;
    });

    var rows = state.players.map(function (player) {
      var snake = levelById[player.playerId];
      return {
        name: player.name,
        connected: player.connected,
        isMe: player.playerId === myPlayerId,
        level: snake ? snake.level : 0,
        length: snake ? snake.length : 0,
        alive: !!snake,
        stunned: !!snake && snake.stunned
      };
    });

    rows.sort(function (a, b) {
      return b.level - a.level;
    });

    el.scoreboard.innerHTML = '';
    rows.forEach(function (row) {
      var li = document.createElement('li');
      if (!row.connected) {
        li.className = 'away';
      }

      var name = document.createElement('span');
      name.textContent = row.isMe ? row.name + ' (you)' : row.name;
      if (row.isMe) {
        name.className = 'you';
      }

      var stat = document.createElement('span');
      stat.className = 'lvl';
      var bits = ['lvl ' + row.level, 'len ' + row.length];
      if (!row.alive) {
        bits = ['off board'];
      } else if (row.stunned) {
        bits.push('frozen');
      }
      stat.textContent = bits.join(' · ');

      li.appendChild(name);
      li.appendChild(stat);
      el.scoreboard.appendChild(li);
    });
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

  el.createBtn.addEventListener('click', function () {
    clearBanner();
    var name = el.name.value;
    connect(function () {
      send({ type: 'create', name: name });
    });
  });

  el.joinBtn.addEventListener('click', function () {
    clearBanner();
    var code = el.room.value.trim().toUpperCase();
    if (!code) {
      showBanner('Enter a room code to join.');
      return;
    }
    var name = el.name.value;
    connect(function () {
      send({ type: 'join', room: code, name: name });
    });
  });

  el.room.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      el.joinBtn.click();
    }
  });

  el.readyBtn.addEventListener('click', function () {
    send({ type: 'ready' });
  });

  el.startBtn.addEventListener('click', function () {
    send({ type: 'start' });
  });

  // --- boot ---------------------------------------------------------------

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
