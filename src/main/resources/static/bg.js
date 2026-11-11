// The app's floating background (client/src/components/FloatingBackground.tsx) for the
// website: the same emoji, places and motions, drawn with CSS animations behind the page.
(function () {
  // emoji, left %, top %, size px, opacity, ms per half-cycle, drift y px, drift x px, rotation deg, motion
  var SHAPES = [
    ['🦴', 5, 8, 38, .62, 3500, 35, 10, 20],
    ['🐾', 80, 5, 30, .64, 4500, 40, 12, 15],
    ['⚽', 18, 52, 34, .58, 3800, 32, 22, 0, 'spin'],
    ['🐕', 86, 28, 42, .55, 5500, 45, 8, 10],
    ['🐶', 38, 36, 38, .50, 5000, 40, 14, 8],
    ['🦮', 46, 10, 32, .52, 5200, 34, 16, 8],
    ['🐩', 8, 66, 30, .50, 4400, 28, 0, 0, 'breathe'],
    ['🦴', 62, 62, 28, .48, 4800, 38, 10, 25, 'diagonal'],
    ['🐾', 33, 77, 24, .48, 3700, 30, 8, 12],
    ['⚽', 84, 72, 30, .46, 5500, 0, 20, 0, 'spin'],
    ['🐕‍🦺', 70, 18, 26, .40, 6800, 26, 12, 6],
    ['🌿', 24, 20, 28, .44, 5800, 0, 0, 0, 'breathe'],
    ['🌳', 90, 54, 30, .40, 7500, 0, 0, 0, 'breathe'],
    ['🏡', 14, 88, 28, .42, 6500, 0, 0, 0, 'breathe'],
    ['🦴', 54, 87, 22, .42, 5200, 24, 8, 30],
    ['🦮', 58, 44, 20, .38, 7200, 28, 14, 6]
  ];

  var layer = document.createElement('div');
  layer.className = 'floaty';
  layer.setAttribute('aria-hidden', 'true');
  SHAPES.forEach(function (s) {
    var el = document.createElement('span');
    el.textContent = s[0];
    el.className = 'floaty-' + (s[9] || 'float');
    var st = el.style;
    st.left = s[1] + '%';
    st.top = s[2] + '%';
    st.fontSize = s[3] + 'px';
    st.setProperty('--o', s[4]);
    // Spins take one cycle per duration; the others go there and back, as in the app.
    st.animationDuration = (s[9] === 'spin' ? s[5] : s[5] * 2) + 'ms';
    st.setProperty('--ty', -s[6] + 'px');
    st.setProperty('--tx', s[7] + 'px');
    st.setProperty('--rot', s[8] + 'deg');
    layer.appendChild(el);
  });
  document.body.insertBefore(layer, document.body.firstChild);
})();
