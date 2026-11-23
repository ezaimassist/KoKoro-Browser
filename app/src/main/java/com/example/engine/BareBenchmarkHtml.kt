package com.example.engine

object BareBenchmarkHtml {

    fun buildBenchmarkPage(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <title>Bare Turbo — WebGL2 & Input Latency Lab</title>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            html, body {
              width: 100%; height: 100%; overflow: hidden;
              background: #0A0C10; color: #F1F5F9;
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
              touch-action: none; user-select: none; -webkit-user-select: none;
            }
            #glCanvas {
              position: fixed; inset: 0; width: 100%; height: 100%; display: block;
            }
            .panel {
              position: fixed; left: 14px; right: 14px; bottom: 16px;
              background: rgba(16, 20, 28, 0.88);
              border: 1px solid rgba(43, 52, 72, 0.9);
              border-radius: 16px; padding: 14px;
              backdrop-filter: blur(12px);
            }
            .row { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
            .title { font-size: 13px; font-weight: 700; letter-spacing: 0.08em; color: #B8FF2C; text-transform: uppercase; }
            .sub { font-size: 11px; color: #94A3B8; margin-top: 2px; word-break: break-word; }
            .metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 10px; }
            .metric {
              background: rgba(10, 12, 16, 0.72); border: 1px solid #252C3D;
              border-radius: 10px; padding: 8px 6px; text-align: center;
            }
            .m-val { font-size: 16px; font-weight: 700; color: #00E5FF; font-variant-numeric: tabular-nums; }
            .m-val.lime { color: #B8FF2C; }
            .m-lbl { font-size: 10px; color: #94A3B8; margin-top: 2px; text-transform: uppercase; }
            .controls { display: flex; gap: 8px; margin-top: 10px; }
            button {
              flex: 1; padding: 9px 10px; border-radius: 10px; border: 1px solid #2B3448;
              background: #171C27; color: #F1F5F9; font-size: 12px; font-weight: 600;
            }
            button.active { background: #B8FF2C; color: #0B1400; border-color: #B8FF2C; }
            .touch-hint {
              position: fixed; top: 18px; left: 50%; transform: translateX(-50%);
              background: rgba(16, 20, 28, 0.8); border: 1px solid #2B3448;
              border-radius: 999px; padding: 6px 14px; font-size: 11px; color: #CBD5E1;
              pointer-events: none; white-space: nowrap;
            }
          </style>
        </head>
        <body>
          <canvas id="glCanvas"></canvas>
          <div class="touch-hint">Drag anywhere on screen to test touch-to-paint latency</div>
          <div class="panel">
            <div class="row">
              <div>
                <div class="title" id="modeBadge">WebGL2 GPU Stress Lab</div>
                <div class="sub" id="gpuInfo">Initializing shader pipeline...</div>
              </div>
            </div>
            <div class="metrics">
              <div class="metric"><div class="m-val lime" id="fpsVal">--</div><div class="m-lbl">WebGL FPS</div></div>
              <div class="metric"><div class="m-val" id="frameVal">--</div><div class="m-lbl">Frame ms</div></div>
              <div class="metric"><div class="m-val" id="touchVal">--</div><div class="m-lbl">Input ms</div></div>
              <div class="metric"><div class="m-val" id="dprVal">--</div><div class="m-lbl">Eff DPR</div></div>
            </div>
            <div class="controls">
              <button id="btn1" class="active" onclick="setLoad(1)">1x Shader</button>
              <button id="btn2" onclick="setLoad(2)">2x Heavy</button>
              <button id="btn4" onclick="setLoad(4)">4x Extreme</button>
            </div>
          </div>

          <script>
            (function() {
              const canvas = document.getElementById('glCanvas');
              const gpuInfo = document.getElementById('gpuInfo');
              const fpsVal = document.getElementById('fpsVal');
              const frameVal = document.getElementById('frameVal');
              const touchVal = document.getElementById('touchVal');
              const dprVal = document.getElementById('dprVal');

              let loadFactor = 1;
              let pointerX = 0.5, pointerY = 0.5;
              let pendingInputTs = 0;
              let lastTouchLatency = 0;

              window.setLoad = function(n) {
                loadFactor = n;
                document.getElementById('btn1').className = n === 1 ? 'active' : '';
                document.getElementById('btn2').className = n === 2 ? 'active' : '';
                document.getElementById('btn4').className = n === 4 ? 'active' : '';
              };

              function onPointer(e) {
                pendingInputTs = performance.now();
                pointerX = e.clientX / Math.max(1, window.innerWidth);
                pointerY = 1.0 - (e.clientY / Math.max(1, window.innerHeight));
              }
              canvas.addEventListener('pointerdown', onPointer, { passive: true });
              canvas.addEventListener('pointermove', onPointer, { passive: true });

              const gl = canvas.getContext('webgl2', { powerPreference: 'high-performance', desynchronized: true })
                || canvas.getContext('webgl', { powerPreference: 'high-performance', desynchronized: true });

              function resize() {
                const dpr = window.devicePixelRatio || 1;
                canvas.width = Math.max(1, Math.floor(window.innerWidth * dpr));
                canvas.height = Math.max(1, Math.floor(window.innerHeight * dpr));
                dprVal.textContent = dpr.toFixed(2) + 'x';
                if (gl) gl.viewport(0, 0, canvas.width, canvas.height);
              }
              window.addEventListener('resize', resize);
              resize();

              if (!gl) {
                document.getElementById('modeBadge').textContent = 'Canvas 2D Fallback Stress';
                gpuInfo.textContent = 'WebGL unavailable — running 2D particle pipeline';
                const ctx = canvas.getContext('2d');
                let frames = 0, lastSec = performance.now(), prev = lastSec;
                function loop2d(now) {
                  const dt = now - prev; prev = now; frames++;
                  if (pendingInputTs > 0) {
                    lastTouchLatency = Math.max(0.4, now - pendingInputTs);
                    pendingInputTs = 0;
                    touchVal.textContent = lastTouchLatency.toFixed(1);
                  }
                  ctx.fillStyle = '#0A0C10';
                  ctx.fillRect(0, 0, canvas.width, canvas.height);
                  const count = 120 * loadFactor;
                  for (let i = 0; i < count; i++) {
                    const a = now * 0.002 + i * 0.21;
                    const r = (i / count) * Math.min(canvas.width, canvas.height) * 0.42;
                    const x = pointerX * canvas.width + Math.cos(a) * r;
                    const y = (1 - pointerY) * canvas.height + Math.sin(a * 1.3) * r;
                    ctx.fillStyle = i % 2 === 0 ? '#B8FF2C' : '#00E5FF';
                    ctx.fillRect(x, y, 4, 4);
                  }
                  if (now - lastSec >= 400) {
                    fpsVal.textContent = Math.round(frames * 1000 / (now - lastSec));
                    frameVal.textContent = dt.toFixed(1);
                    frames = 0; lastSec = now;
                  }
                  requestAnimationFrame(loop2d);
                }
                requestAnimationFrame(loop2d);
                return;
              }

              const dbg = gl.getExtension('WEBGL_debug_renderer_info');
              const rendererName = dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
              const attrs = gl.getContextAttributes() || {};
              gpuInfo.textContent = rendererName + ' • AA:' + (attrs.antialias ? 'ON' : 'OFF') + ' • Desync:' + (attrs.desynchronized ? 'ON' : 'OFF');

              const vsSrc = `
                attribute vec2 aPos;
                varying vec2 vUv;
                void main() {
                  vUv = aPos * 0.5 + 0.5;
                  gl_Position = vec4(aPos, 0.0, 1.0);
                }
              `;
              const fsSrc = `
                precision highp float;
                varying vec2 vUv;
                uniform float uTime;
                uniform vec2 uRes;
                uniform vec2 uTouch;
                uniform int uIter;

                void main() {
                  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / min(uRes.x, uRes.y);
                  vec2 t = (uTouch - 0.5) * 1.6;
                  float acc = 0.0;
                  float wave = 0.0;
                  for (int i = 0; i < 48; i++) {
                    if (i >= uIter) break;
                    float fi = float(i) * 0.37;
                    vec2 p = uv * (2.2 + sin(uTime * 0.6 + fi) * 0.4) - t * 0.5;
                    float d = length(p + vec2(sin(uTime + fi), cos(uTime * 0.8 - fi)) * 0.35);
                    acc += 0.018 / (abs(d - 0.32) + 0.025);
                    wave += sin(p.x * 8.0 + uTime * 2.0 + fi) * cos(p.y * 8.0 - uTime * 1.6);
                  }
                  vec3 base = vec3(0.039, 0.047, 0.063);
                  vec3 lime = vec3(0.72, 1.0, 0.17);
                  vec3 cyan = vec3(0.0, 0.90, 1.0);
                  float ring = smoothstep(0.14, 0.0, abs(length(vUv - uTouch) - 0.08));
                  vec3 col = base + mix(cyan, lime, clamp(wave * 0.15 + 0.5, 0.0, 1.0)) * clamp(acc * 0.14, 0.0, 1.25);
                  col += lime * ring * 0.9;
                  gl_FragColor = vec4(col, 1.0);
                }
              `;

              function compile(type, src) {
                const s = gl.createShader(type);
                gl.shaderSource(s, src);
                gl.compileShader(s);
                return s;
              }
              const prog = gl.createProgram();
              gl.attachShader(prog, compile(gl.VERTEX_SHADER, vsSrc));
              gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fsSrc));
              gl.linkProgram(prog);
              gl.useProgram(prog);

              const buf = gl.createBuffer();
              gl.bindBuffer(gl.ARRAY_BUFFER, buf);
              gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, 1,1]), gl.STATIC_DRAW);
              const loc = gl.getAttribLocation(prog, 'aPos');
              gl.enableVertexAttribArray(loc);
              gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);

              const uTime = gl.getUniformLocation(prog, 'uTime');
              const uRes = gl.getUniformLocation(prog, 'uRes');
              const uTouch = gl.getUniformLocation(prog, 'uTouch');
              const uIter = gl.getUniformLocation(prog, 'uIter');

              let frames = 0, lastSec = performance.now(), prev = lastSec;
              function render(now) {
                const dt = now - prev; prev = now; frames++;
                if (pendingInputTs > 0) {
                  lastTouchLatency = Math.max(0.3, now - pendingInputTs);
                  pendingInputTs = 0;
                  touchVal.textContent = lastTouchLatency.toFixed(1);
                }
                gl.uniform1f(uTime, now * 0.001);
                gl.uniform2f(uRes, canvas.width, canvas.height);
                gl.uniform2f(uTouch, pointerX, pointerY);
                gl.uniform1i(uIter, 10 * loadFactor);
                gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

                if (now - lastSec >= 400) {
                  fpsVal.textContent = Math.round(frames * 1000 / (now - lastSec));
                  frameVal.textContent = dt.toFixed(1);
                  frames = 0; lastSec = now;
                }
                requestAnimationFrame(render);
              }
              requestAnimationFrame(render);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
