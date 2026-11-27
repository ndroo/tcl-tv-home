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
    a.addEventListener('loadstart', function () { if (mine() && a.getAttribute('src')) emit(ET.LOAD_START); });
    a.addEventListener('playing', function () {
      if (!mine()) return;
      clearTimeout(startTimer);
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
          }, 15000);
        }
      });
    }
  };

  function freshLoad() {
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
    toggle: function () { (!audio || audio.paused || state === PS.IDLE) ? this.play() : this.pause(); },
    // A live stream: playing again starts it fresh (in sync), rather than carrying on from a stale buffer.
    play: function () { retries = 0; freshLoad(); },
    pause: function () { if (audio && !audio.paused) audio.pause(); },
    stop: function () {
      loads++;
      state = PS.IDLE;
      clearTimeout(startTimer);
      if (audio) { audio.pause(); audio.removeAttribute('src'); audio.load(); }
    },
    state: function () { return state + (audio ? ' t=' + audio.currentTime.toFixed(1) : ''); }
  };
})();
