// Stands in for Google's Cast receiver framework (cast_receiver_framework.js) when triplec.fm's own receiver page
// (cast-receiver.html) runs as the Android TV app, with no phone casting to it. The page is loaded unchanged from
// triplec.fm; only this script differs. It plays the station's stream in an <audio> element and raises the events
// the page listens for (LOAD_START, PLAYING, BUFFERING, PAUSE, ENDED, ERROR, MEDIA_FINISHED), so the page's timing,
// sync and restart logic all run as they do on a Chromecast. The app replaces __STREAM_URL__ before serving it.
(function () {
  'use strict';
  var STREAM = '__STREAM_URL__';
  var ET = { PLAYING: 'PLAYING', PAUSE: 'PAUSE', ENDED: 'ENDED', ABORT: 'ABORT', ERROR: 'ERROR',
    MEDIA_FINISHED: 'MEDIA_FINISHED', BUFFERING: 'BUFFERING', LOAD_START: 'LOAD_START' };
  var ER = { END_OF_STREAM: 'END_OF_STREAM', ERROR: 'ERROR', STOPPED: 'STOPPED', INTERRUPTED: 'INTERRUPTED' };
  var PS = { IDLE: 'IDLE', PLAYING: 'PLAYING', PAUSED: 'PAUSED', BUFFERING: 'BUFFERING' };

  var listeners = {}, interceptors = {}, media = null, audio = null, state = PS.IDLE, loads = 0, startTimer = 0, retries = 0;

  // Starting (OK pressed, sound not there yet: the page waits up to 2 s to line up with the broadcast): the page's pause
  // icon becomes a spinning ring at once, and further OKs are ignored. Before, nothing showed for a moment and a second
  // OK paused it again, so it looked as if it wouldn't resume.
  var starting = false, startingTimer = 0;
  var css = document.createElement('style');
  css.textContent = 'body.tc-starting .paused svg{display:none}' +
    'body.tc-starting .paused::after{content:"";width:11vh;height:11vh;box-sizing:border-box;border-radius:50%;' +
    'border:1.1vh solid rgba(255,255,255,.25);border-top-color:#ff4d3d;animation:tc-spin .8s linear infinite}' +
    '@keyframes tc-spin{to{transform:rotate(360deg)}}';
  (document.head || document.documentElement).appendChild(css);
  // The stream's own volume (left/right and the volume buttons, from the app). Over the TV's optical out the TV's
  // volume does nothing, but this scales the sound itself, so it reaches the sound system. 20 steps of 2 dB; kept.
  var STEPS = 20, vol = STEPS, muted = false, hud = null, hudTimer = 0;
  try { var saved = parseInt(localStorage.getItem('tcTvVol'), 10); if (saved >= 0 && saved <= STEPS) vol = saved; } catch (e) {}
  css.textContent += '#tc-vol{position:fixed;left:50%;bottom:7vh;transform:translateX(-50%);display:flex;align-items:center;gap:2.2vh;' +
    'padding:1.7vh 3.2vh;border-radius:99px;background:#17171d;border:1px solid rgba(255,255,255,.16);color:#f4f3f7;' +
    'box-shadow:0 1.5vh 5vh rgba(0,0,0,.6);' +
    'font:600 2.6vh/1 var(--body,system-ui),sans-serif;opacity:0;transition:opacity .25s;z-index:50;pointer-events:none}' +
    '#tc-vol.on{opacity:1}#tc-vol svg{width:3.4vh;height:3.4vh;flex:none}' +
    '#tc-vol .bar{width:38vh;height:1.2vh;border-radius:99px;background:rgba(255,255,255,.18);overflow:hidden}' +
    '#tc-vol .bar i{display:block;height:100%;background:#ff4d3d;border-radius:99px;transition:width .12s}' +
    '#tc-vol b{min-width:8vh;text-align:right;font-variant-numeric:tabular-nums}';
  function gain() { return muted || vol === 0 ? 0 : Math.pow(10, (vol - STEPS) / 10); }
  function showVolume() {
    if (!document.body) return;
    if (!hud) { hud = document.createElement('div'); hud.id = 'tc-vol'; document.body.appendChild(hud); }
    var off = muted || vol === 0;
    hud.innerHTML = '<svg viewBox="0 0 24 24" fill="#fff"><path d="M3 9v6h4l5 5V4L7 9H3z"/>' +
      (off ? '<path d="M15.5 9.5l5 5m0-5l-5 5" stroke="#fff" stroke-width="2" stroke-linecap="round"/>'
           : '<path d="M16.5 12A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4z"/>') + '</svg>' +
      '<div class="bar"><i style="width:' + (muted ? 0 : Math.round(vol / STEPS * 100)) + '%"></i></div><b>' + (muted ? 'Muted' : vol) + '</b>';
    hud.classList.add('on');
    clearTimeout(hudTimer);
    hudTimer = setTimeout(function () { hud.classList.remove('on'); }, 1800);
  }

  function setStarting(on) {
    starting = on;
    clearTimeout(startingTimer);
    if (on) startingTimer = setTimeout(function () { setStarting(false); }, 45000);   // never stuck ignoring OK
    if (document.body) document.body.classList.toggle('tc-starting', on);
  }

  function emit(type, extra) {
    var ev = { type: type };
    for (var k in extra || {}) ev[k] = extra[k];
    (listeners[type] || []).slice().forEach(function (fn) {
      try { fn(ev); } catch (e) { console.error('[tc-tv] listener', type, e); }
    });
  }

  function LoadRequestData() { this.requestId = 0; this.media = null; this.autoplay = true; }
  function MusicTrackMediaMetadata() { this.metadataType = 3; }
  function Image(url) { this.url = url; }
  function CastReceiverOptions() {}

  // A fresh <audio> for every load. On Android, Chromium suspends a paused player's media pipeline after a short while
  // and the element didn't always come back from that (paused on the TV, OK did nothing), so a play never reuses one.
  // Events from an element that has been replaced are ignored.
  function newPlayer() {
    if (audio) { var old = audio; old.pause(); old.removeAttribute('src'); old.load(); old.remove(); }
    var a = audio = document.createElement('audio');
    var mine = function () { return a === audio; };
    a.preload = 'auto';
    a.volume = gain();
    a.addEventListener('loadstart', function () { if (mine() && a.getAttribute('src')) emit(ET.LOAD_START); });
    a.addEventListener('playing', function () {
      if (!mine()) return;
      clearTimeout(startTimer);
      setStarting(false);
      state = PS.PLAYING;
      emit(ET.BUFFERING, { isBuffering: false });
      emit(ET.PLAYING);
    });
    a.addEventListener('waiting', function () {
      if (!mine() || state === PS.IDLE) return;
      state = PS.BUFFERING;
      emit(ET.BUFFERING, { isBuffering: true });
    });
    a.addEventListener('pause', function () {
      if (!mine() || state === PS.IDLE || a.ended) return;
      setStarting(false);
      state = PS.PAUSED;
      emit(ET.PAUSE);
    });
    a.addEventListener('ended', function () {
      if (!mine()) return;
      state = PS.IDLE;
      emit(ET.ENDED);
      emit(ET.MEDIA_FINISHED, { endedReason: ER.END_OF_STREAM });
    });
    a.addEventListener('error', function () {
      if (!mine() || !a.getAttribute('src') || state === PS.IDLE) return;
      setStarting(false);
      state = PS.IDLE;
      emit(ET.ERROR);
      emit(ET.MEDIA_FINISHED, { endedReason: ER.ERROR });
    });
    document.body.appendChild(a);
    return a;
  }

  var pm = {
    setMessageInterceptor: function (type, fn) { interceptors[type] = fn; },
    addEventListener: function (types, fn) {
      [].concat(types).forEach(function (t) { (listeners[t] = listeners[t] || []).push(fn); });
    },
    removeEventListener: function (types, fn) {
      [].concat(types).forEach(function (t) { listeners[t] = (listeners[t] || []).filter(function (f) { return f !== fn; }); });
    },
    getCurrentTimeSec: function () { return audio ? audio.currentTime : 0; },
    getPlayerState: function () { return state; },
    getMediaInformation: function () { return media; },
    setMediaInformation: function (mi) { media = mi; },
    load: function (req) {
      var mine = ++loads, fn = interceptors.LOAD;
      return Promise.resolve(fn ? fn(req) : req).then(function (r) {
        if (mine !== loads) return; // a newer load came in while the page was getting this one ready
        r = r || req;
        media = r.media;
        var a = newPlayer(), url = media.contentUrl || media.contentId;
        state = PS.BUFFERING;
        a.src = url;
        if (r.autoplay !== false) {
          var p = a.play();
          if (p && p.catch) p.catch(function (e) { console.error('[tc-tv] play', e); });
          // Not playing 15 s later: start it again (twice at most), as a fresh load
          clearTimeout(startTimer);
          startTimer = setTimeout(function () {
            if (mine !== loads || state === PS.PLAYING || state === PS.IDLE) return;
            if (retries++ < 2) { console.warn('[tc-tv] not playing after 15 s, starting again'); freshLoad(); }
            else setStarting(false);
          }, 15000);
        }
      });
    }
  };

  function freshLoad() {
    setStarting(true);
    var r = new LoadRequestData();
    var lang = new URLSearchParams(location.search).get('lang') || 'en';
    r.media = { contentId: STREAM, contentUrl: STREAM, contentType: 'audio/mpeg', streamType: 'LIVE',
      metadata: new MusicTrackMediaMetadata(), customData: { lang: lang } };
    r.autoplay = true;
    return pm.load(r);
  }

  var ctx = {
    getPlayerManager: function () { return pm; },
    sendCustomMessage: function () {},
    addCustomMessageListener: function () {},
    addEventListener: function () {},
    start: function () { freshLoad(); } // no sender: play the station straight away, as a phone's cast would
  };

  window.cast = { framework: {
    CastReceiverContext: { getInstance: function () { return ctx; } },
    CastReceiverOptions: CastReceiverOptions,
    messages: {
      LoadRequestData: LoadRequestData, MusicTrackMediaMetadata: MusicTrackMediaMetadata, Image: Image,
      MessageType: { LOAD: 'LOAD' }, Command: { PAUSE: 1, SEEK: 2, STREAM_VOLUME: 4, STREAM_MUTE: 8 },
      PlayerState: PS
    },
    events: { EventType: ET, EndedReason: ER },
    system: { MessageType: { JSON: 'json', STRING: 'string' } }
  } };

  // For the Android app: the remote's play/pause, and stopping when the app is left.
  window.__tcTv = {
    toggle: function () {
      if (starting) return;   // on its way: a second OK would pause it again
      (!audio || audio.paused || state === PS.IDLE) ? this.play() : this.pause();
    },
    // A live stream: playing again starts it fresh (in sync), rather than carrying on from a stale buffer.
    play: function () { retries = 0; freshLoad(); },
    pause: function () { if (audio && !audio.paused) audio.pause(); },
    stop: function () {
      loads++;
      state = PS.IDLE;
      clearTimeout(startTimer);
      setStarting(false);
      if (audio) { audio.pause(); audio.removeAttribute('src'); audio.load(); }
    },
    volume: function (step) {
      muted = false;
      vol = Math.max(0, Math.min(STEPS, vol + step));
      try { localStorage.setItem('tcTvVol', String(vol)); } catch (e) {}
      if (audio) audio.volume = gain();
      showVolume();
    },
    mute: function () { muted = !muted; if (audio) audio.volume = gain(); showVolume(); },
    state: function () { return state + (audio ? ' t=' + audio.currentTime.toFixed(1) + ' vol=' + audio.volume.toFixed(3) : '') + (starting ? ' starting' : ''); }
  };
})();
